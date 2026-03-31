FROM golang:1.21-alpine AS builder

WORKDIR /app

COPY main.go handlers.go go.mod go.sum* ./

RUN go mod tidy
RUN go mod download

RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o endpointsServer .

FROM alpine:latest

WORKDIR /app

COPY --from=builder /app/endpointsServer .

EXPOSE 8000

CMD ["./endpointsServer"]
