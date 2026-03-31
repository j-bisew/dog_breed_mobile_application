#!/bin/bash
export CGO_ENABLED=1
export CGO_CFLAGS="-I/opt/ort/include"
export CGO_LDFLAGS="-L/opt/ort/lib"
export LD_LIBRARY_PATH="/opt/ort/lib"
mkdir -p lib
cp -n /opt/ort/lib/libonnxruntime.so* lib/
go test -coverpkg=./... -coverprofile=coverage.out
go tool cover -func=coverage.out
