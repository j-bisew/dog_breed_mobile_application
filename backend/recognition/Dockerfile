FROM golang:1.21-bookworm AS builder

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    ca-certificates \
    curl \
    tar \
    && rm -rf /var/lib/apt/lists/*

ARG ONNXRUNTIME_VERSION=1.24.1
RUN mkdir -p /opt/ort && \
    curl -fsSL -o /tmp/onnxruntime.tgz "https://github.com/microsoft/onnxruntime/releases/download/v${ONNXRUNTIME_VERSION}/onnxruntime-linux-x64-${ONNXRUNTIME_VERSION}.tgz" && \
    tar -xzf /tmp/onnxruntime.tgz -C /opt/ort --strip-components=1 && \
    rm -f /tmp/onnxruntime.tgz

COPY go.mod go.sum* ./
COPY main.go handlers.go inference.go class_names.txt ./
COPY models/go ./models/go

RUN go mod tidy
RUN go mod download

RUN go build -o recognitionServer .

FROM debian:bookworm-slim

WORKDIR /app

COPY --from=builder /app/recognitionServer ./recognitionServer
COPY --from=builder /app/class_names.txt ./class_names.txt
COPY --from=builder /app/models/go ./models/go
COPY --from=builder /opt/ort/lib/libonnxruntime.so* ./lib/

RUN mkdir -p dog_photos

EXPOSE 8030

CMD ["./recognitionServer"]