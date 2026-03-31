FROM golang:1.21-alpine AS builder

WORKDIR /app

# Copy Go source files
COPY main.go handlers.go database.go go.mod ./

# Download dependencies
RUN go mod tidy
RUN go mod download

# Build the application (no CGO needed with modernc.org/sqlite)
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o databaseServer .

# Final stage
FROM alpine:latest

WORKDIR /app

# Copy binary from builder
COPY --from=builder /app/databaseServer .

# Copy database files
COPY *.db* ./

# Copy race folder with categories
COPY racesFolder ./racesFolder

EXPOSE 8020

CMD ["./databaseServer"]
