package main

import (
	"bytes"
	"image"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
)

func TestHandleRoot(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	w := httptest.NewRecorder()
	handleRoot(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 got %v", w.Code)
	}

	reqPost := httptest.NewRequest(http.MethodPost, "/", nil)
	wPost := httptest.NewRecorder()
	handleRoot(wPost, reqPost)
	if wPost.Code != http.StatusNotFound {
		t.Fatalf("expected 404 got %v", wPost.Code)
	}
}

func TestReadMultipartPhoto(t *testing.T) {
	f, err := os.CreateTemp("", "dummy")
	if err == nil {
		defer os.Remove(f.Name())
		f.Write([]byte("test"))
		f.Seek(0, 0)
		data, _ := readMultipartPhoto(f)
		if string(data) != "test" {
			t.Fatalf("unexpected data %s", string(data))
		}
		f.Close()
	}
}

func TestHandleSubmitDogPhotoErrors(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/submitDogPhoto", nil)
	w := httptest.NewRecorder()
	handleSubmitDogPhoto(w, req)
	if w.Code != http.StatusNotFound {
		t.Fatalf("expected 404 got %v", w.Code)
	}

	req = httptest.NewRequest(http.MethodPost, "/submitDogPhoto", nil)
	w = httptest.NewRecorder()
	handleSubmitDogPhoto(w, req)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 got %v", w.Code)
	}

	req = httptest.NewRequest(http.MethodPost, "/submitDogPhoto", nil)
	req.Header.Set("Content-Type", "multipart/form-data")
	w = httptest.NewRecorder()
	handleSubmitDogPhoto(w, req)

	var b bytes.Buffer
	mw := multipart.NewWriter(&b)
	_ = mw.WriteField("other", "value")
	mw.Close()

	req = httptest.NewRequest(http.MethodPost, "/submitDogPhoto", &b)
	req.Header.Set("Content-Type", mw.FormDataContentType())
	w = httptest.NewRecorder()
	handleSubmitDogPhoto(w, req)

	var b2 bytes.Buffer
	mw2 := multipart.NewWriter(&b2)
	part, _ := mw2.CreateFormFile("photo", "empty.jpg")
	part.Write([]byte(""))
	mw2.Close()

	req2 := httptest.NewRequest(http.MethodPost, "/submitDogPhoto", &b2)
	req2.Header.Set("Content-Type", mw2.FormDataContentType())
	w2 := httptest.NewRecorder()
	handleSubmitDogPhoto(w2, req2)
}

func TestRandomString(t *testing.T) {
	s := randomString(16)
	if len(s) != 16 {
		t.Fatalf("unexpected len %d", len(s))
	}
}

func TestRandomStringError(t *testing.T) {
	orig := randRead
	randRead = func(b []byte) (int, error) {
		return 0, os.ErrPermission
	}
	defer func() { randRead = orig }()

	s := randomString(16)
	if s != "randomfallback" {
		t.Fatalf("expected randomfallback, got %q", s)
	}
}

func TestHandleSubmitDogPhoto_AppErrors(t *testing.T) {
	od := detectAndCropDog
	op := predictBreedFromBytes
	defer func() {
		detectAndCropDog = od
		predictBreedFromBytes = op
	}()

	var b bytes.Buffer
	mw := multipart.NewWriter(&b)
	part, _ := mw.CreateFormFile("photo", "dog.jpg")
	part.Write([]byte("fake data here that is not empty"))
	mw.Close()

	// Test detect error
	detectAndCropDog = func(data []byte) (*image.Rectangle, float32, []byte, error) {
		return nil, 0, nil, os.ErrPermission
	}
	req := httptest.NewRequest(http.MethodPost, "/submitDogPhoto", bytes.NewReader(b.Bytes()))
	req.Header.Set("Content-Type", mw.FormDataContentType())
	w := httptest.NewRecorder()
	handleSubmitDogPhoto(w, req)

	// Test detect no dog
	detectAndCropDog = func(data []byte) (*image.Rectangle, float32, []byte, error) {
		return nil, 0.1, nil, nil
	}
	req = httptest.NewRequest(http.MethodPost, "/submitDogPhoto", bytes.NewReader(b.Bytes()))
	req.Header.Set("Content-Type", mw.FormDataContentType())
	w = httptest.NewRecorder()
	handleSubmitDogPhoto(w, req)

	// Test predict error
	detectAndCropDog = func(data []byte) (*image.Rectangle, float32, []byte, error) {
		return &image.Rectangle{}, 0.9, []byte("ok"), nil
	}
	predictBreedFromBytes = func(data []byte) (string, float32, error) {
		return "", 0, os.ErrPermission
	}
	req = httptest.NewRequest(http.MethodPost, "/submitDogPhoto", bytes.NewReader(b.Bytes()))
	req.Header.Set("Content-Type", mw.FormDataContentType())
	w = httptest.NewRecorder()
	handleSubmitDogPhoto(w, req)

	// Test predict low confidence
	predictBreedFromBytes = func(data []byte) (string, float32, error) {
		return "Husky", 10.0, nil
	}
	req = httptest.NewRequest(http.MethodPost, "/submitDogPhoto", bytes.NewReader(b.Bytes()))
	req.Header.Set("Content-Type", mw.FormDataContentType())
	w = httptest.NewRecorder()
	handleSubmitDogPhoto(w, req)
	if w.Code != 515 {
		t.Errorf("expected 515 for low confidence, got %v", w.Code)
	}
}

func TestMkdirError(t *testing.T) {
	os.WriteFile("dog_photos", []byte("file instead of dir"), 0o644)
	defer os.Remove("dog_photos")

	var b bytes.Buffer
	mw := multipart.NewWriter(&b)
	part, _ := mw.CreateFormFile("photo", "dog.jpg")
	part.Write([]byte("fake data here"))
	mw.Close()

	req := httptest.NewRequest(http.MethodPost, "/submitDogPhoto", bytes.NewReader(b.Bytes()))
	req.Header.Set("Content-Type", mw.FormDataContentType())
	w := httptest.NewRecorder()
	handleSubmitDogPhoto(w, req)
}

func TestMkdirErrorReal(t *testing.T) {
	// First clean it up
	os.RemoveAll("dog_photos")

	// Create a file there
	os.WriteFile("dog_photos", []byte("file instead of dir"), 0o644)
	defer os.Remove("dog_photos")

	var b bytes.Buffer
	mw := multipart.NewWriter(&b)
	part, _ := mw.CreateFormFile("photo", "dog.jpg")
	part.Write([]byte("fake data here that is not empty"))
	mw.Close()

	req := httptest.NewRequest(http.MethodPost, "/submitDogPhoto", bytes.NewReader(b.Bytes()))
	req.Header.Set("Content-Type", mw.FormDataContentType())
	w := httptest.NewRecorder()
	handleSubmitDogPhoto(w, req)
}

func TestFullSuccess(t *testing.T) {
	od := detectAndCropDog
	op := predictBreedFromBytes
	defer func() {
		detectAndCropDog = od
		predictBreedFromBytes = op
	}()

	var b bytes.Buffer
	mw := multipart.NewWriter(&b)
	part, _ := mw.CreateFormFile("photo", "dog.jpg")
	part.Write([]byte("fake data here that is not empty"))
	mw.Close()

	detectAndCropDog = func(data []byte) (*image.Rectangle, float32, []byte, error) {
		return &image.Rectangle{}, 0.9, []byte("ok"), nil
	}
	predictBreedFromBytes = func(data []byte) (string, float32, error) {
		return "Golden Retriever", 95.5, nil
	}

	req := httptest.NewRequest(http.MethodPost, "/submitDogPhoto", bytes.NewReader(b.Bytes()))
	req.Header.Set("Content-Type", mw.FormDataContentType())
	w := httptest.NewRecorder()
	handleSubmitDogPhoto(w, req)
	if w.Code != http.StatusOK {
		t.Errorf("expected 200 got %v", w.Code)
	}
}
