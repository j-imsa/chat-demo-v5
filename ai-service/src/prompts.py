import ai_service_pb2 as pb
from history import StoredMessage

HISTORY_LIMIT = 20
TEMPERATURE = 0.7
NO_LLM_MESSAGE = (
    "No LLM is configured. Open the Settings screen and enter an LLM base URL and model name to start chatting."
)

_ROLES = {"user": pb.Role.USER, "assistant": pb.Role.ASSISTANT}


def system_prompt(info: pb.LlmInfo) -> str:
    return (
        f"You are a helpful assistant powered by the model {info.model or 'unknown'}. "
        "Answer clearly and concisely."
    )


def build_messages(history: list[StoredMessage], info: pb.LlmInfo) -> list[pb.LlmMessage]:
    messages = [pb.LlmMessage(role=pb.Role.SYSTEM, content=system_prompt(info))]
    messages.extend(pb.LlmMessage(role=_ROLES[m.role], content=m.content) for m in history)
    return messages
