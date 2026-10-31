FROM python:3.12-slim AS build
WORKDIR /build
COPY requirements-build.txt .
RUN pip install --no-cache-dir -r requirements-build.txt
COPY proto/ proto/
RUN mkdir generated && python -m grpc_tools.protoc -Iproto --python_out=generated --grpc_python_out=generated proto/ai_service.proto

FROM python:3.12-slim
ENV PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app/generated:/app/src \
    AI_SERVICE_PORT=50051 \
    AI_SERVICE_DB=/data/ai_service.db
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY --from=build /build/generated generated
COPY src/ src/
RUN useradd --system --no-create-home app && mkdir /data && chown app /data
USER app
VOLUME /data
EXPOSE 50051
CMD ["python", "src/main.py"]
