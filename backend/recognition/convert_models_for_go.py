#!/usr/bin/env python3
"""
Convert recognition models to Go-friendly runtime format.

Outputs:
- Keras model (.keras) -> ONNX (.onnx)
- YOLO PyTorch model (.pt) -> ONNX (.onnx)

Default inputs:
- models/mobilenetv2_finetuned.keras
- yolov8n.pt

Example:
    python convert_models_for_go.py

Custom paths:
    python convert_models_for_go.py \
        --keras-path models/mobilenetv2_finetuned.keras \
        --yolo-path yolov8n.pt \
        --out-dir models/go
"""

from __future__ import annotations

import argparse
import os
import shutil
import sys
from pathlib import Path


def _require_module(module_name: str, install_hint: str) -> None:
    try:
        __import__(module_name)
    except ImportError:
        print(f"Missing dependency: {module_name}")
        print(f"Install with: {install_hint}")
        sys.exit(1)


def convert_keras_to_onnx(keras_path: Path, output_onnx_path: Path) -> None:
    _require_module("tensorflow", "pip install tensorflow")
    _require_module("tf2onnx", "pip install tf2onnx onnx")

    import tensorflow as tf
    import tf2onnx

    if not keras_path.exists():
        raise FileNotFoundError(f"Keras model not found: {keras_path}")

    print(f"[1/2] Loading Keras model: {keras_path}")
    model = tf.keras.models.load_model(str(keras_path))

    # Input shape matches current preprocessing in recognitionHelperFunctions.py
    input_signature = (
        tf.TensorSpec((1, 224, 224, 3), tf.float32, name="input"),
    )

    output_onnx_path.parent.mkdir(parents=True, exist_ok=True)

    print(f"[1/2] Exporting Keras model to ONNX: {output_onnx_path}")
    _, _ = tf2onnx.convert.from_keras(
        model,
        input_signature=input_signature,
        opset=13,
        output_path=str(output_onnx_path),
    )

    print("[1/2] Done")


def convert_yolo_to_onnx(
    yolo_path: Path,
    output_onnx_path: Path,
    imgsz: int,
    opset: int,
    simplify: bool,
) -> None:
    _require_module("ultralytics", "pip install ultralytics")

    from ultralytics import YOLO

    if not yolo_path.exists():
        raise FileNotFoundError(f"YOLO model not found: {yolo_path}")

    output_onnx_path.parent.mkdir(parents=True, exist_ok=True)

    print(f"[2/2] Loading YOLO model: {yolo_path}")
    model = YOLO(str(yolo_path))

    print(f"[2/2] Exporting YOLO model to ONNX (imgsz={imgsz})")
    # Ultralytics writes ONNX near model by default; project/name controls destination.
    export_result = model.export(
        format="onnx",
        opset=opset,
        imgsz=imgsz,
        simplify=simplify,
        dynamic=False,
        project=str(output_onnx_path.parent),
        name=output_onnx_path.stem,
    )

    export_path = Path(str(export_result)).resolve()
    output_onnx_path = output_onnx_path.resolve()

    # Ultralytics may write to nested paths depending on version; normalize to exact target path.
    if export_path.exists() and export_path != output_onnx_path:
        output_onnx_path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(export_path, output_onnx_path)

    if not output_onnx_path.exists():
        # Fallback search in output dir tree.
        candidates = list(output_onnx_path.parent.rglob("*.onnx"))
        if not candidates:
            raise FileNotFoundError(f"YOLO ONNX export not found under {output_onnx_path.parent}")
        shutil.copy2(candidates[0], output_onnx_path)

    print(f"[2/2] Export result: {export_result}")
    print("[2/2] Done")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Convert recognition models to ONNX for Go")
    parser.add_argument(
        "--keras-path",
        default="models/mobilenetv2_finetuned.keras",
        help="Path to input Keras model (.keras)",
    )
    parser.add_argument(
        "--yolo-path",
        default="yolov8n.pt",
        help="Path to input YOLO model (.pt)",
    )
    parser.add_argument(
        "--out-dir",
        default="models/go",
        help="Directory where converted ONNX models are written",
    )
    parser.add_argument(
        "--yolo-imgsz",
        type=int,
        default=640,
        help="Image size for YOLO ONNX export",
    )
    parser.add_argument(
        "--yolo-opset",
        type=int,
        default=12,
        help="ONNX opset for YOLO export (OpenCV 4.6 usually works better with 11/12)",
    )
    parser.add_argument(
        "--yolo-simplify",
        action="store_true",
        help="Enable ONNX graph simplification for YOLO export",
    )
    parser.add_argument(
        "--skip-keras",
        action="store_true",
        help="Skip Keras -> ONNX conversion",
    )
    parser.add_argument(
        "--skip-yolo",
        action="store_true",
        help="Skip YOLO -> ONNX conversion",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    script_dir = Path(__file__).resolve().parent
    keras_path = (script_dir / args.keras_path).resolve()
    yolo_path = (script_dir / args.yolo_path).resolve()
    out_dir = (script_dir / args.out_dir).resolve()

    keras_out = out_dir / "mobilenetv2_finetuned.onnx"
    yolo_out = out_dir / "yolov8n.onnx"

    print("Converting models for Go runtime...")
    print(f"Keras input: {keras_path}")
    print(f"YOLO input:  {yolo_path}")
    print(f"Output dir:  {out_dir}")

    try:
        if not args.skip_keras:
            convert_keras_to_onnx(keras_path, keras_out)
        if not args.skip_yolo:
            convert_yolo_to_onnx(
                yolo_path,
                yolo_out,
                args.yolo_imgsz,
                args.yolo_opset,
                args.yolo_simplify,
            )
    except Exception as exc:
        print(f"Conversion failed: {exc}")
        return 1

    print("All conversions completed successfully.")
    print(f"- {keras_out}")
    print(f"- {yolo_out}")
    print("Use ONNX Runtime in Go (for example: github.com/yalue/onnxruntime_go).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
