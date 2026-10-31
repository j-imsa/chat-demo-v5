import asyncio
import logging
import os

import grpc

import ai_service_pb2_grpc
from history import History
from servicer import AiServicer


async def serve() -> None:
    port = os.environ.get("AI_SERVICE_PORT", "50051")
    db_path = os.environ.get("AI_SERVICE_DB", "data/ai_service.db")
    server = grpc.aio.server()
    ai_service_pb2_grpc.add_AiServiceServicer_to_server(AiServicer(History(db_path)), server)
    server.add_insecure_port(f"[::]:{port}")
    await server.start()
    logging.info("ai-service gRPC server listening on port %s", port)
    await server.wait_for_termination()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    asyncio.run(serve())
