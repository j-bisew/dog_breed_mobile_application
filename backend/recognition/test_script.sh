#!/bin/bash
export ONNXRUNTIME_VERSION="1.16.3"
curl -sLO https://github.com/microsoft/onnxruntime/releases/download/v$ONNXRUNTIME_VERSION/onnxruntime-linux-x64-$ONNXRUNTIME_VERSION.tgz
tar -xzf onnxruntime-linux-x64-$ONNXRUNTIME_VERSION.tgz
export CGO_CFLAGS="-I/app/onnxruntime-linux-x64-$ONNXRUNTIME_VERSION/include"
export CGO_LDFLAGS="-L/app/onnxruntime-linux-x64-$ONNXRUNTIME_VERSION/lib"
export LD_LIBRARY_PATH="/app/onnxruntime-linux-x64-$ONNXRUNTIME_VERSION/lib:$LD_LIBRARY_PATH"
go mod tidy
go test -coverpkg=./... -coverprofile=coverage.out
go tool cover -func=coverage.out
