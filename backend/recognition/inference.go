package main

import (
	"bytes"
	"errors"
	"fmt"
	"image"
	"image/color"
	"image/draw"
	_ "image/gif"
	"image/jpeg"
	_ "image/png"
	"os"
	"path/filepath"
	"strings"
	"sync"

	ort "github.com/yalue/onnxruntime_go"
)

const (
	classifierInputW   = 224
	classifierInputH   = 224
	detectionThreshold = 0.5
)

var (
	classNames        []string
	inferMu           sync.Mutex
	yoloEnabled       bool
	yoloSession       *ort.DynamicAdvancedSession
	classifierSession *ort.DynamicAdvancedSession
	ortInitialized    bool
)

var (
	ortGetInputOutputInfo     = ort.GetInputOutputInfo
	ortNewDynamicAdvSession   = ort.NewDynamicAdvancedSession
	ortInitializeEnvironment  = ort.InitializeEnvironment
	ortDestroyEnvironment     = ort.DestroyEnvironment
	jpegEncode                = jpeg.Encode
)

var ortLibCandidates = []string{
	filepath.Join("lib", "libonnxruntime.so.1.24.1"),
	filepath.Join("lib", "libonnxruntime.so"),
	"libonnxruntime.so.1.24.1",
	"libonnxruntime.so",
	"onnxruntime.so",
}

func initializeRecognition() error {
	yoloPath, err := firstExistingPath([]string{
		filepath.Join("models", "go", "yolov8n.onnx"),
		filepath.Join("models", "go", "yolov8n", "yolov8n.onnx"),
		"yolov8n.onnx",
	})
	if err != nil {
		return fmt.Errorf("missing YOLO ONNX model; run convert_models_for_go.py first: %w", err)
	}

	classifierPath, err := firstExistingPath([]string{
		filepath.Join("models", "go", "mobilenetv2_finetuned.onnx"),
		filepath.Join("models", "go", "mobilenetv2_finetuned", "mobilenetv2_finetuned.onnx"),
	})
	if err != nil {
		return fmt.Errorf("missing classifier ONNX model; run convert_models_for_go.py first: %w", err)
	}

	err = initializeONNXRuntime()
	if err != nil {
		return err
	}

	inInfos, outInfos, err := ortGetInputOutputInfo(classifierPath)
	if err != nil {
		return fmt.Errorf("failed to inspect classifier model IO: %w", err)
	}
	if len(inInfos) != 1 || len(outInfos) != 1 {
		return fmt.Errorf("classifier must have exactly 1 input and 1 output, got %d and %d", len(inInfos), len(outInfos))
	}

	classifierSession, err = ortNewDynamicAdvSession(
		classifierPath,
		[]string{inInfos[0].Name},
		[]string{outInfos[0].Name},
		nil,
	)
	if err != nil {
		return fmt.Errorf("failed to create ONNX classifier session: %w", err)
	}

	// Keep YOLO model validation, but continue using full-image crop until
	// YOLO inference is migrated from OpenCV DNN.
	yoloInInfos, yoloOutInfos, err := ortGetInputOutputInfo(yoloPath)
	if err != nil {
		return fmt.Errorf("failed to inspect YOLO model IO: %w", err)
	}
	yoloSession, err = ortNewDynamicAdvSession(
		yoloPath,
		[]string{yoloInInfos[0].Name},
		[]string{yoloOutInfos[0].Name},
		nil,
	)
	if err != nil {
		return fmt.Errorf("failed to create ONNX YOLO session: %w", err)
	}

	yoloEnabled = true

	cn, err := loadClassNames()
	if err != nil {
		return err
	}
	classNames = cn

	return nil
}

func initializeONNXRuntime() error {
	if ortInitialized {
		return nil
	}

	var selected string
	for _, c := range ortLibCandidates {
		if _, err := os.Stat(c); err == nil {
			selected = c
			break
		}
	}
	if selected != "" {
		ort.SetSharedLibraryPath(selected)
	}

	if err := ortInitializeEnvironment(); err != nil {
		return fmt.Errorf("failed to initialize onnxruntime environment: %w", err)
	}
	ortInitialized = true
	return nil
}

func shutdownRecognition() {
	inferMu.Lock()
	defer inferMu.Unlock()

	if yoloSession != nil {
		_ = yoloSession.Destroy()
		yoloSession = nil
	}
	if classifierSession != nil {
		_ = classifierSession.Destroy()
		classifierSession = nil
	}
	if ortInitialized {
		_ = ortDestroyEnvironment()
		ortInitialized = false
	}
}

func firstExistingPath(candidates []string) (string, error) {
	for _, p := range candidates {
		if _, err := os.Stat(p); err == nil {
			return p, nil
		}
	}
	return "", fmt.Errorf("none of the candidate paths exists: %v", candidates)
}

func loadClassNames() ([]string, error) {
	b, err := os.ReadFile("class_names.txt")
	if err != nil {
		return nil, fmt.Errorf("error loading class names: %w", err)
	}
	lines := strings.Split(string(b), "\n")
	out := make([]string, 0, len(lines))
	for _, l := range lines {
		v := strings.TrimSpace(l)
		if v != "" {
			out = append(out, v)
		}
	}
	if len(out) == 0 {
		return nil, errors.New("class names file is empty")
	}
	return out, nil
}

var detectAndCropDog = func(photoData []byte) (*image.Rectangle, float32, []byte, error) {
	img, _, err := image.Decode(bytes.NewReader(photoData))
	if err != nil {
		return nil, 0, nil, fmt.Errorf("failed to decode image: %w", err)
	}
	bounds := img.Bounds()
	if bounds.Dx() <= 0 || bounds.Dy() <= 0 {
		return nil, 0, nil, errors.New("failed to decode image")
	}

	var rect *image.Rectangle
	conf := float32(1.0)

	if yoloEnabled {
		rect, conf, err = detectDogYOLO(img, detectionThreshold)
		if err != nil {
			return nil, 0, nil, err
		}
		if rect == nil {
			return nil, 0, nil, nil
		}
	} else {
		full := bounds
		rect = &full
	}

	crop := cropImage(img, *rect)
	encoded, err := encodeJPEG(crop)
	if err != nil {
		return nil, 0, nil, fmt.Errorf("failed to encode cropped image: %w", err)
	}

	return rect, conf, encoded, nil
}

var detectDogYOLO = func(img image.Image, confThreshold float32) (*image.Rectangle, float32, error) {
	inputS := 640

	// 1. Resize/Letterbox to 640x640
	imgW := img.Bounds().Dx()
	imgH := img.Bounds().Dy()

	scale := float32(inputS) / float32(imgW)
	if float32(inputS)/float32(imgH) < scale {
		scale = float32(inputS) / float32(imgH)
	}

	newW := int(float32(imgW) * scale)
	newH := int(float32(imgH) * scale)

	resized := resizeToRGBA(img, newW, newH)

	padW := inputS - newW
	padH := inputS - newH
	padLeft := padW / 2
	padTop := padH / 2

	inputData := make([]float32, 1*3*inputS*inputS)
	for y := 0; y < newH; y++ {
		for x := 0; x < newW; x++ {
			c := resized.At(x, y).(color.RGBA)
			idxR := 0*inputS*inputS + (y+padTop)*inputS + (x + padLeft)
			idxG := 1*inputS*inputS + (y+padTop)*inputS + (x + padLeft)
			idxB := 2*inputS*inputS + (y+padTop)*inputS + (x + padLeft)

			// YOLO expected format: normalize to 0-1, order RGB.
			inputData[idxR] = float32(c.R) / 255.0
			inputData[idxG] = float32(c.G) / 255.0
			inputData[idxB] = float32(c.B) / 255.0
		}
	}

	inputTensor, err := ort.NewTensor(ort.NewShape(1, 3, int64(inputS), int64(inputS)), inputData)
	if err != nil {
		return nil, 0, fmt.Errorf("failed to create YOLO input tensor: %w", err)
	}
	defer inputTensor.Destroy()

	outputs := []ort.Value{nil}

	inferMu.Lock()
	err = yoloSession.Run([]ort.Value{inputTensor}, outputs)
	inferMu.Unlock()

	if err != nil {
		return nil, 0, fmt.Errorf("failed to run YOLO session: %w", err)
	}
	if outputs[0] == nil {
		return nil, 0, errors.New("empty YOLO output")
	}
	defer outputs[0].Destroy()

	outputTensor, ok := outputs[0].(*ort.Tensor[float32])
	if !ok {
		return nil, 0, fmt.Errorf("unexpected YOLO output tensor type")
	}
	outData := outputTensor.GetData()

	shape := outputTensor.GetShape()

	// Output shape YOLOv8: [1, 84, 8400]
	// Columns 0-3: cx, cy, w, h
	// Columns 4-83: class scores
	if len(shape) != 3 || shape[1] != 84 {
		return nil, 0, fmt.Errorf("unexpected custom YOLO output shape: %v", shape)
	}

	numBoxes := int(shape[2])

	type bbox struct {
		cx, cy, w, h float32
		conf         float32
		cls          int
	}
	dogs := []bbox{}
	anyAnimal := []bbox{}

	for i := 0; i < numBoxes; i++ {
		bestCls := -1
		bestConf := float32(-1.0)
		for c := 0; c < 80; c++ {
			score := outData[0*84*numBoxes+(4+c)*numBoxes+i]
			if score > bestConf {
				bestConf = score
				bestCls = c
			}
		}

		if bestConf > confThreshold {
			cx := outData[0*84*numBoxes+0*numBoxes+i]
			cy := outData[0*84*numBoxes+1*numBoxes+i]
			w := outData[0*84*numBoxes+2*numBoxes+i]
			h := outData[0*84*numBoxes+3*numBoxes+i]

			bx := bbox{cx: cx, cy: cy, w: w, h: h, conf: bestConf, cls: bestCls}

			if bestCls == 16 { // Dog
				dogs = append(dogs, bx)
			} else if bestCls >= 15 && bestCls <= 23 && bestCls != 16 {
				anyAnimal = append(anyAnimal, bx)
			}
		}
	}

	if len(dogs) == 0 && len(anyAnimal) == 0 {
		return nil, 0, nil
	}

	var bestBox bbox
	found := false

	if len(dogs) > 0 {
		bestArea := float32(-1.0)
		for _, b := range dogs {
			area := b.w * b.h
			if area > bestArea {
				bestArea = area
				bestBox = b
				found = true
			}
		}
	} else if len(anyAnimal) > 0 {
		bestArea := float32(-1.0)
		for _, b := range anyAnimal {
			area := b.w * b.h
			if area > bestArea {
				bestArea = area
				bestBox = b
				found = true
			}
		}
	}

	if !found {
		return nil, 0, nil
	}

	// Map back bounding box to original image coordinates
	cx_orig := (bestBox.cx - float32(padLeft)) / scale
	cy_orig := (bestBox.cy - float32(padTop)) / scale
	w_orig := bestBox.w / scale
	h_orig := bestBox.h / scale

	x0 := int(cx_orig - w_orig/2)
	y0 := int(cy_orig - h_orig/2)
	x1 := int(cx_orig + w_orig/2)
	y1 := int(cy_orig + h_orig/2)

	if x0 < 0 {
		x0 = 0
	}
	if y0 < 0 {
		y0 = 0
	}
	if x1 > imgW {
		x1 = imgW
	}
	if y1 > imgH {
		y1 = imgH
	}

	rect := image.Rect(x0, y0, x1, y1)
	return &rect, bestBox.conf, nil
}

var predictBreedFromBytes = func(photoData []byte) (string, float32, error) {
	img, _, err := image.Decode(bytes.NewReader(photoData))
	if err != nil {
		return "", 0, fmt.Errorf("failed to decode cropped image: %w", err)
	}
	if img.Bounds().Dx() <= 0 || img.Bounds().Dy() <= 0 {
		return "", 0, errors.New("failed to decode cropped image")
	}

	resized := resizeToRGBA(img, classifierInputW, classifierInputH)
	inputData := make([]float32, classifierInputH*classifierInputW*3)
	idx := 0
	for y := 0; y < classifierInputH; y++ {
		for x := 0; x < classifierInputW; x++ {
			c := color.RGBAModel.Convert(resized.At(x, y)).(color.RGBA)
			inputData[idx] = float32(c.R) / 255.0
			idx++
			inputData[idx] = float32(c.G) / 255.0
			idx++
			inputData[idx] = float32(c.B) / 255.0
			idx++
		}
	}

	inputTensor, err := ort.NewTensor(ort.NewShape(1, classifierInputH, classifierInputW, 3), inputData)
	if err != nil {
		return "", 0, fmt.Errorf("failed to create classifier input tensor: %w", err)
	}
	defer inputTensor.Destroy()

	outputs := []ort.Value{nil}

	inferMu.Lock()
	err = classifierSession.Run([]ort.Value{inputTensor}, outputs)
	inferMu.Unlock()
	if err != nil {
		return "", 0, fmt.Errorf("failed to run classifier session: %w", err)
	}
	if outputs[0] == nil {
		return "", 0, errors.New("empty classifier output")
	}
	defer outputs[0].Destroy()

	outputTensor, ok := outputs[0].(*ort.Tensor[float32])
	if !ok {
		return "", 0, fmt.Errorf("unexpected classifier output tensor type: %T", outputs[0])
	}
	pred := outputTensor.GetData()
	if len(pred) == 0 {
		return "", 0, errors.New("no classifier predictions")
	}

	bestIdx := 0
	bestVal := pred[0]
	for i := 1; i < len(pred); i++ {
		if pred[i] > bestVal {
			bestVal = pred[i]
			bestIdx = i
		}
	}

	if bestIdx >= len(classNames) {
		return "", 0, fmt.Errorf("prediction index %d out of class_names range %d", bestIdx, len(classNames))
	}

	return classNames[bestIdx], bestVal * 100.0, nil
}

func cropImage(src image.Image, rect image.Rectangle) image.Image {
	srcB := src.Bounds()
	r := rect.Intersect(srcB)
	if r.Empty() {
		r = srcB
	}

	dst := image.NewRGBA(image.Rect(0, 0, r.Dx(), r.Dy()))
	draw.Draw(dst, dst.Bounds(), src, r.Min, draw.Src)
	return dst
}

func resizeToRGBA(src image.Image, width, height int) *image.RGBA {
	if width <= 0 || height <= 0 {
		return image.NewRGBA(image.Rect(0, 0, 1, 1))
	}

	sb := src.Bounds()
	sw, sh := sb.Dx(), sb.Dy()
	if sw <= 0 || sh <= 0 {
		return image.NewRGBA(image.Rect(0, 0, width, height))
	}

	dst := image.NewRGBA(image.Rect(0, 0, width, height))
	for y := 0; y < height; y++ {
		sy := sb.Min.Y + (y*sh)/height
		for x := 0; x < width; x++ {
			sx := sb.Min.X + (x*sw)/width
			dst.Set(x, y, src.At(sx, sy))
		}
	}
	return dst
}

func encodeJPEG(img image.Image) ([]byte, error) {
	var buf bytes.Buffer
	if err := jpegEncode(&buf, img, &jpeg.Options{Quality: 92}); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}
