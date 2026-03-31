package main

import (
"net/http"
"testing"
"time"
)

func TestMainCoverage(t *testing.T) {
// Give other tests time to finish their renames
time.Sleep(1 * time.Second)

go func() {
main()
}()

time.Sleep(1 * time.Second)

http.Get("http://localhost:8030/")
}
