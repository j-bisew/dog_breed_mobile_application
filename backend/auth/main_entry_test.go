package main

import (
	"errors"
	"net/http"
	"os"
	"testing"
)

func TestMainAuthCallsServer(t *testing.T) {
	oldStart := startServer
	oldFatal := fatalf
	defer func() {
		startServer = oldStart
		fatalf = oldFatal
	}()

	called := false
	startServer = func(addr string, handler http.Handler) error {
		called = true
		if addr != ":8010" {
			t.Fatalf("unexpected addr %s", addr)
		}
		return errors.New("stop")
	}
	fatalf = func(v ...interface{}) {}

	main()
	if !called {
		t.Fatalf("expected startServer to be called")
	}
}

func TestLoadSecretKeyErrorPath(t *testing.T) {
	cwd, err := os.Getwd()
	if err != nil {
		t.Fatalf("getwd failed: %v", err)
	}
	defer func() { _ = os.Chdir(cwd) }()

	tmp := t.TempDir()
	if err := os.Chdir(tmp); err != nil {
		t.Fatalf("chdir failed: %v", err)
	}

	_, err = loadSecretKey()
	if err == nil {
		t.Fatalf("expected error when keys.txt is missing")
	}

	ok, err := verifyToken("abc")
	if err == nil || ok {
		t.Fatalf("expected verifyToken to fail when key missing")
	}
}
