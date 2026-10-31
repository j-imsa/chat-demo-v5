import logging
import uuid
from collections.abc import AsyncIterator

import grpc

import ai_service_pb2 as pb
import ai_service_pb2_grpc
from history import History
from prompts import (
    HISTORY_LIMIT,
    MAX_TOOL_ITERATIONS,
    NO_LLM_MESSAGE,
    TEMPERATURE,
    assistant_tool_message,
    build_messages,
    tool_message,
)

log = logging.getLogger("ai_service")


class ProtocolError(Exception):
    pass


class StreamClosed(Exception):
    pass


def _delta(content: str) -> pb.AiToCore:
    return pb.AiToCore(chat_delta=pb.ChatDelta(content=content))


def _completed(status: pb.Status, message: str = "") -> pb.AiToCore:
    return pb.AiToCore(chat_completed=pb.ChatCompleted(status=status, message=message))


def _activity(call: pb.ToolCall, phase: pb.ToolPhase) -> pb.AiToCore:
    return pb.AiToCore(chat_tool_activity=pb.ChatToolActivity(call_id=call.id, name=call.name, phase=phase))


def _new_id() -> str:
    return str(uuid.uuid4())


async def _next(inbound: AsyncIterator[pb.CoreToAi], *kinds: str, request_id: str | None = None) -> pb.CoreToAi:
    try:
        message = await anext(inbound)
    except StopAsyncIteration:
        raise StreamClosed() from None
    kind = message.WhichOneof("payload")
    if kind not in kinds:
        raise ProtocolError(f"expected one of {kinds}, got {kind}")
    if request_id is not None and getattr(message, kind).request_id != request_id:
        raise ProtocolError(f"unexpected request_id for {kind}")
    return message


class AiServicer(ai_service_pb2_grpc.AiServiceServicer):
    def __init__(self, history: History) -> None:
        self._history = history

    async def Chat(self, request_iterator, context):
        try:
            async for outbound in self._turn(aiter(request_iterator)):
                yield outbound
        except StreamClosed:
            log.info("core closed the stream mid-turn")
        except ProtocolError as error:
            log.warning("protocol error: %s", error)
            await context.abort(grpc.StatusCode.FAILED_PRECONDITION, str(error))

    async def _turn(self, inbound: AsyncIterator[pb.CoreToAi]):
        request = (await _next(inbound, "chat_request")).chat_request
        self._history.ensure_conversation(request.conversation_id, request.user_id)
        self._history.add_message(request.conversation_id, "user", request.message)

        query_id = _new_id()
        yield pb.AiToCore(llm_availability_query=pb.LlmAvailabilityQuery(request_id=query_id))
        availability = (await _next(inbound, "llm_availability", request_id=query_id)).llm_availability

        if not availability.available:
            log.info("conversation %s: no LLM available (%s)", request.conversation_id, availability.reason)
            yield _delta(NO_LLM_MESSAGE)
            yield _completed(pb.Status.NO_LLM, availability.reason or "No LLM configured")
            return

        catalog_id = _new_id()
        yield pb.AiToCore(tool_catalog_query=pb.ToolCatalogQuery(request_id=catalog_id))
        tools = list((await _next(inbound, "tool_catalog", request_id=catalog_id)).tool_catalog.tools)

        messages = build_messages(self._history.recent(request.conversation_id, HISTORY_LIMIT), availability.info)
        reply: list[str] = []
        for iteration in range(MAX_TOOL_ITERATIONS + 1):
            call_id = _new_id()
            yield pb.AiToCore(
                llm_call_request=pb.LlmCallRequest(
                    request_id=call_id,
                    messages=messages,
                    temperature=TEMPERATURE,
                    tools=tools if iteration < MAX_TOOL_ITERATIONS else [],
                )
            )

            parts: list[str] = []
            while True:
                message = await _next(inbound, "llm_chunk", "llm_completion", request_id=call_id)
                if message.HasField("llm_completion"):
                    completion = message.llm_completion
                    break
                content = message.llm_chunk.content
                if content:
                    parts.append(content)
                    yield _delta(content)

            if not completion.ok:
                log.warning("conversation %s: LLM call failed: %s", request.conversation_id, completion.error)
                yield _completed(pb.Status.LLM_ERROR, f"The LLM call failed: {completion.error}")
                return

            reply.extend(parts)
            if not completion.tool_calls:
                break

            messages.append(assistant_tool_message("".join(parts), completion.tool_calls))
            for call in completion.tool_calls:
                log.info("conversation %s: requesting tool %s", request.conversation_id, call.name)
                yield _activity(call, pb.ToolPhase.STARTED)
                tool_request_id = _new_id()
                yield pb.AiToCore(tool_call_request=pb.ToolCallRequest(request_id=tool_request_id, call=call))
                result = (await _next(inbound, "tool_result", request_id=tool_request_id)).tool_result
                yield _activity(call, pb.ToolPhase.SUCCEEDED if result.ok else pb.ToolPhase.FAILED)
                messages.append(tool_message(call, result))

        self._history.add_message(request.conversation_id, "assistant", "".join(reply))
        yield _completed(pb.Status.OK)
