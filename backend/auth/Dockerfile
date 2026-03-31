FROM golang:1.21-alpine AS builder

WORKDIR /app

# Copy Go source files
COPY main.go handlers.go utils.go go.mod go.sum* ./

# Copy keys file
COPY keys.txt ./

# Download dependencies
RUN go mod tidy
RUN go mod download

# Build the application
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o authServer .

# Final stage
FROM alpine:latest

WORKDIR /app

# Copy binary from builder
COPY --from=builder /app/authServer .

# Copy keys file
COPY keys.txt ./

EXPOSE 8010

CMD ["./authServer"]
