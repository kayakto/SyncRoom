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
OLLAMA_TEXT_MODEL = os.getenv("OLLAMA_TEXT_MODEL", OLLAMA_VISION_MODEL)
OLLAMA_KEEP_ALIVE = os.getenv("OLLAMA_KEEP_ALIVE", "30m")
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


class TextRequest(BaseModel):
    prompt: str


class TextResponse(BaseModel):
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
    # Shown only when LOCAL_DRAW_URL is unreachable or returns an error — not a real sketch.
    img = Image.new("RGB", (512, 280), color=(245, 245, 245))
    drawer = ImageDraw.Draw(img)
    short_prompt = (prompt or "empty prompt").strip()[:72]
    drawer.text((10, 12), "FALLBACK (no local SD)", fill=(160, 40, 40))
    drawer.text((10, 42), "Check LOCAL_DRAW_URL / docker-compose.local-ai.yml", fill=(20, 20, 20))
    drawer.text((10, 72), "Phrase for draw:", fill=(60, 60, 60))
    drawer.text((10, 102), short_prompt, fill=(0, 90, 180))
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
            "keep_alive": OLLAMA_KEEP_ALIVE,
        }
        resp = requests.post(f"{OLLAMA_URL}/api/generate", json=payload, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        text = str(data.get("response", "")).strip()
        return text if text else None
    except Exception:
        return None


def _ollama_text(prompt: str) -> Optional[str]:
    try:
        payload = {
            "model": OLLAMA_TEXT_MODEL,
            "prompt": prompt,
            "stream": False,
            "keep_alive": OLLAMA_KEEP_ALIVE,
        }
        resp = requests.post(f"{OLLAMA_URL}/api/generate", json=payload, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        text = str(data.get("response", "")).strip()
        return text if text else None
    except Exception:
        return None


def _human_style_prompt(prompt: str, draw_seconds: int = 25) -> str:
    # CLIP в SD1.x — около 77 токенов; длинный русский текст даёт truncation и мусор на выходе.
    clean_prompt = (prompt or "").strip()[:80]
    return (
        f"Very simple doodle, thick rough lines, minimal details, mouse drawing in {draw_seconds}s: {clean_prompt}"
    )


def _local_draw(prompt: str) -> Optional[str]:
    if not LOCAL_DRAW_URL:
        return None
    try:
        payload = {
            "prompt": _human_style_prompt(prompt),
            "negative_prompt": "photorealistic, realistic shading, high detail, 3d render, text, watermark",
            # Keep draw latency below game timeout (90s) on CPU hosts.
            "steps": 6,
            "cfg_scale": 5,
            "width": 320,
            "height": 320,
        }
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
        }
        resp = requests.post(LOCAL_DRAW_URL, json=payload, headers=headers, timeout=180)
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
    except Exception as e:
        print(f"[inference-mock] local draw failed: {e}", flush=True)
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
    print(
        "[inference-mock] /api/draw: local SD failed or disabled; "
        f"LOCAL_DRAW_URL={LOCAL_DRAW_URL!r}, using fallback PNG",
        flush=True,
    )
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


def _fallback_text(prompt: str) -> str:
    p = (prompt or "").lower()
    if "gartic" in p or "фразу" in p:
        return random.choice([
            "кот в тапках ест морковку",
            "динозавр на самокате",
            "пицца в космосе",
            "чайник играет в футбол",
        ])
    if "quiplash" in p or "вопрос" in p:
        return random.choice([
            "Это звучит как плохая, но гениальная идея",
            "Только если после этого дадут пиццу",
            "План отличный, но кот против",
            "Я бы ответил, но меня опередил тостер",
        ])
    return "кот в очках"


@app.post("/api/text", response_model=TextResponse)
def text(req: TextRequest) -> TextResponse:
    generated = _ollama_text(req.prompt)
    if generated:
        return TextResponse(text=generated)
    return TextResponse(text=_fallback_text(req.prompt))
