import base64
import os
import traceback
from datetime import datetime, timezone
from io import BytesIO
from threading import Lock
from uuid import uuid4

import torch
from diffusers import DDIMScheduler, StableDiffusionPipeline
from fastapi import FastAPI
from PIL import Image
from pydantic import BaseModel

# segmind/tiny-sd — лёгкий; при битом кэше HF см. README (очистить volume hf_cache).
MODEL_ID = os.getenv("SD_MODEL_ID", "segmind/tiny-sd")
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
DTYPE = torch.float16 if DEVICE == "cuda" else torch.float32
OUTPUT_DIR = os.getenv("OUTPUT_DIR", "/generated")

app = FastAPI(title="SyncRoom Local Draw Service")

_pipe = None
_pipe_lock = Lock()


class Txt2ImgRequest(BaseModel):
    prompt: str
    negative_prompt: str | None = None
    steps: int = 20
    cfg_scale: float = 7.0
    width: int = 512
    height: int = 512


def get_pipe() -> StableDiffusionPipeline:
    global _pipe
    if _pipe is None:
        with _pipe_lock:
            if _pipe is None:
                # Некоторые снапшоты tiny-sd содержат только .bin, не .safetensors в vae
                pipe = StableDiffusionPipeline.from_pretrained(
                    MODEL_ID,
                    torch_dtype=DTYPE,
                    safety_checker=None,
                    requires_safety_checker=False,
                    use_safetensors=False,
                )
                # Euler/DPMSolver с «чужим» config дают IndexError по sigmas (diffusers 0.30 + tiny-sd).
                # DDIM — стабильный классический планировщик для SD1.x.
                pipe.scheduler = DDIMScheduler.from_config(pipe.scheduler.config)
                if DEVICE == "cuda":
                    pipe = pipe.to("cuda")
                else:
                    pipe = pipe.to("cpu")
                pipe.set_progress_bar_config(disable=True)
                _pipe = pipe
    return _pipe


def image_to_base64(image: Image.Image) -> str:
    buffer = BytesIO()
    image.save(buffer, format="PNG")
    return base64.b64encode(buffer.getvalue()).decode("utf-8")


def save_image_png(image: Image.Image) -> str:
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    ts = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    filename = f"draw-{ts}-{uuid4().hex[:8]}.png"
    path = os.path.join(OUTPUT_DIR, filename)
    image.save(path, format="PNG")
    return path


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "model": MODEL_ID, "device": DEVICE}


@app.post("/sdapi/v1/txt2img")
def txt2img(request: Txt2ImgRequest) -> dict:
    pipe = get_pipe()
    width = max(256, min(request.width, 768))
    height = max(256, min(request.height, 768))
    # DDIM: 20–30 шагов — нормальный диапазон; не поднимать до 50 на CPU без нужды
    steps = max(15, min(int(request.steps), 35))
    try:
        image = pipe(
            prompt=request.prompt,
            negative_prompt=request.negative_prompt,
            num_inference_steps=steps,
            guidance_scale=max(1.0, min(request.cfg_scale, 12.0)),
            width=width,
            height=height,
        ).images[0]
    except Exception:
        traceback.print_exc()
        raise
    saved_path = save_image_png(image)
    return {"images": [image_to_base64(image)], "savedPath": saved_path}
