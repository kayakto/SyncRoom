import base64
import os
import random
from datetime import datetime, timezone
from io import BytesIO
from typing import Optional
from uuid import uuid4

import requests
from fastapi import FastAPI
from pydantic import BaseModel
from PIL import Image, ImageDraw

app = FastAPI(title="SyncRoom Local Inference Mock")

PROVIDER = os.getenv("INFERENCE_PROVIDER", "local").lower()
OLLAMA_URL = os.getenv("OLLAMA_URL", "http://127.0.0.1:11434")
OLLAMA_VISION_MODEL = os.getenv("OLLAMA_VISION_MODEL", "llava:7b")
LOCAL_DRAW_URL = os.getenv(
    "LOCAL_DRAW_URL", "http://host.docker.internal:7860/sdapi/v1/txt2img"
).strip()
LOCAL_GUESS_URL = os.getenv("LOCAL_GUESS_URL", "").strip()
OUTPUT_DIR = os.getenv("OUTPUT_DIR", "/generated")


class DrawRequest(BaseModel):
    prompt: str


class DrawResponse(BaseModel):
    imageBase64: str


class GuessRequest(BaseModel):
    imageBase64: str


class GuessResponse(BaseModel):
    text: str


def _png_data_url_from_image(img: Image.Image) -> str:
    buf = BytesIO()
    img.save(buf, format="PNG")
    encoded = base64.b64encode(buf.getvalue()).decode("utf-8")
    return f"data:image/png;base64,{encoded}"


def _save_data_url_png(image_data_url: str) -> Optional[str]:
    try:
        if not image_data_url.startswith("data:image"):
            return None
        payload = image_data_url.split(",", 1)[1]
        data = base64.b64decode(payload)
        os.makedirs(OUTPUT_DIR, exist_ok=True)
        ts = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
        filename = f"adapter-{ts}-{uuid4().hex[:8]}.png"
        path = os.path.join(OUTPUT_DIR, filename)
        with open(path, "wb") as f:
            f.write(data)
        return path
    except Exception:
        return None


def _mock_draw(prompt: str) -> str:
    # Tiny generated image with prompt hint for quick local testing.
    img = Image.new("RGB", (320, 180), color=(245, 245, 245))
    drawer = ImageDraw.Draw(img)
    short_prompt = (prompt or "empty prompt").strip()[:42]
    drawer.text((10, 20), "SyncRoom Bot Draw", fill=(20, 20, 20))
    drawer.text((10, 65), short_prompt, fill=(0, 90, 180))
    return _png_data_url_from_image(img)


def _mock_guess(image_base64: str) -> str:
    if not image_base64 or image_base64.strip() == "":
        return "ничего не видно"
    candidates = [
        "похоже на кота",
        "кажется, это пицца",
        "похоже на робота",
        "видно что-то абстрактное",
        "на рисунке, возможно, медведь",
    ]
    return random.choice(candidates)


def _ollama_guess(image_base64: str) -> Optional[str]:
    # Works with local multimodal models in Ollama (for example llava).
    try:
        payload = {
            "model": OLLAMA_VISION_MODEL,
            "prompt": "Опиши картинку коротко по-русски (2-5 слов).",
            "images": [image_base64.split(",")[-1]],
            "stream": False,
        }
        resp = requests.post(f"{OLLAMA_URL}/api/generate", json=payload, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        text = str(data.get("response", "")).strip()
        return text if text else None
    except Exception:
        return None


def _human_style_prompt(prompt: str, draw_seconds: int = 25) -> str:
    clean_prompt = (prompt or "").strip()
    return (
        f"Сгенерируй изображение: {clean_prompt}. "
        f"Условие: это рисунок, который нарисовал человек за {draw_seconds} секунд. "
        "Используй простые линии, минимум деталей, легкую кривизну, "
        "немного неровные контуры, как быстрый скетч маркером."
    )


def _local_draw(prompt: str) -> Optional[str]:
    if not LOCAL_DRAW_URL:
        return None
    try:
        payload = {
            "prompt": _human_style_prompt(prompt),
            "negative_prompt": "photorealistic, high detail, 3d render",
            "steps": 12,
            "cfg_scale": 6,
            "width": 512,
            "height": 512,
        }
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
        }
        resp = requests.post(LOCAL_DRAW_URL, json=payload, headers=headers, timeout=90)
        resp.raise_for_status()
        body = resp.json()
        if isinstance(body, dict):
            # A1111 API: {"images": ["base64..."]}
            images = body.get("images")
            if isinstance(images, list) and images and isinstance(images[0], str):
                return f"data:image/png;base64,{images[0]}"
            # Custom API compatibility
            value = body.get("imageBase64")
            if isinstance(value, str) and value.strip():
                return value if value.startswith("data:image") else f"data:image/png;base64,{value}"
        return None
    except Exception:
        return None


def _local_guess(image_base64: str) -> Optional[str]:
    if not LOCAL_GUESS_URL:
        return None
    try:
        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        resp = requests.post(
            LOCAL_GUESS_URL,
            json={"imageBase64": image_base64},
            headers=headers,
            timeout=30,
        )
        resp.raise_for_status()
        data = resp.json()
        text = str(data.get("text", "")).strip()
        return text if text else None
    except Exception:
        return None


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "provider": PROVIDER}


@app.post("/api/draw", response_model=DrawResponse)
def draw(req: DrawRequest) -> DrawResponse:
    if PROVIDER in ("local", "ollama", "mock"):
        image = _local_draw(req.prompt)
        if image:
            _save_data_url_png(image)
            return DrawResponse(imageBase64=image)
    fallback = _mock_draw(req.prompt)
    _save_data_url_png(fallback)
    return DrawResponse(imageBase64=fallback)


@app.post("/api/guess", response_model=GuessResponse)
def guess(req: GuessRequest) -> GuessResponse:
    if PROVIDER in ("local", "ollama", "mock"):
        guessed = _local_guess(req.imageBase64)
        if guessed:
            return GuessResponse(text=guessed)
        guessed = _ollama_guess(req.imageBase64)
        if guessed:
            return GuessResponse(text=guessed)
    return GuessResponse(text=_mock_guess(req.imageBase64))
