# Local Inference Mock (DRAW/GUESS)

Мини-сервис для `SyncRoom` ботов (`/api/draw`, `/api/guess`) только с локальными нейросетями.

## Быстрый старт (Python)

```bash
cd inference-mock
python -m venv .venv
. .venv/Scripts/activate  # Windows PowerShell: .\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8091
```

Проверка:

```bash
curl http://localhost:8091/health
```

## Быстрый старт (Docker)

```bash
docker build -t syncroom-inference-mock ./inference-mock
docker run --rm -p 8091:8091 syncroom-inference-mock
```

## Режимы провайдера

- `INFERENCE_PROVIDER=local` (по умолчанию): локальные draw/guess.
- `INFERENCE_PROVIDER=ollama`: draw как в `local`, caption через Ollama.
- `INFERENCE_PROVIDER=mock`: отладочный fallback.

## Локальный draw endpoint (A1111 / SD WebUI)

```bash
set INFERENCE_PROVIDER=local
set LOCAL_DRAW_URL=http://127.0.0.1:7860/sdapi/v1/txt2img
uvicorn app:app --host 0.0.0.0 --port 8091
```

## Локальный guess через Ollama (LLaVA)

```bash
ollama pull llava:7b
set INFERENCE_PROVIDER=local
set OLLAMA_URL=http://127.0.0.1:11434
set OLLAMA_VISION_MODEL=llava:7b
uvicorn app:app --host 0.0.0.0 --port 8091
```

Если есть отдельный локальный vision endpoint:

```bash
set INFERENCE_PROVIDER=local
set LOCAL_GUESS_URL=http://127.0.0.1:8100/api/guess
uvicorn app:app --host 0.0.0.0 --port 8091
```

Ожидается контракт:
- `POST /api/draw` body `{"prompt":"..."}` -> `{"imageBase64":"data:image/png;base64,..."}`
- `POST /api/guess` body `{"imageBase64":"..."}` -> `{"text":"..."}`

Промпт для draw автоматически усиливается стилем быстрого человеческого скетча
(простые линии, минимум деталей, эффект "нарисовано за N секунд").
