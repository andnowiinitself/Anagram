import os
import logging
import random
from pathlib import Path
from contextlib import asynccontextmanager
from datetime import datetime, timedelta
from typing import AsyncGenerator

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import JSONResponse
from telethon import TelegramClient
from telethon.errors import FloodWaitError
from dotenv import load_dotenv

# uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

env_path = Path(__file__).resolve().parent.parent.parent / ".env"
load_dotenv(dotenv_path=env_path)

# cfg
API_ID = os.getenv("TELEGRAM_API_ID", "")
API_HASH = os.getenv("TELEGRAM_API_HASH", "")
SESSION_NAME = "telegram_session"
MOCK_MODE = not API_ID or not API_HASH or API_ID == "123456"

client: TelegramClient | None = None

@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Управление жизненным циклом приложения (startup/shutdown)"""
    global client
    
    if not MOCK_MODE:
        try:
            client = TelegramClient(SESSION_NAME, int(API_ID), API_HASH)
            await client.start()
            logger.info("Telegram client connected")
        except Exception as e:
            logger.error(f"Failed to connect to Telegram: {e}")
            client = None
    else:
        logger.warning("(!)Running in MOCK MODE — no real Telegram API calls(!)")
        client = None
    
    yield  # робiт
    
    if client and not MOCK_MODE:
        await client.disconnect()
        logger.info("Telegram client disconnected")


# инициализация приложения с lifespan
app = FastAPI(
    title="Telegram Fetcher",
    version="1.0",
    description="Microservice for fetching Telegram channel messages",
    lifespan=lifespan
)


@app.get("/")
async def index() -> str:
    return "ANAGRAM TELEGRAM-SERVICE IS RUNNING"

@app.get("/health")
async def health() -> dict:
    return {
        "status": "ok",
        "service": "telegram-fetcher",
        "mock_mode": MOCK_MODE,
        "connected": client.is_connected() if client and not MOCK_MODE else None
    }

@app.get("/fetch_messages")
async def fetch_messages(
    channel_link: str = Query(..., description="Ссылка на канал (https://t.me/username или username)"),
    limit: int = Query(5, ge=1, le=100, description="Количество сообщений (1-100)")
) -> dict:
    """
    Получает последние сообщения из публичного канала.
    В режиме MOCK возвращает тестовые данные.
    """
    
    # мок
    if MOCK_MODE:
        logger.info(f"(!) MOCK: Fetching {limit} messages from '{channel_link}'")
        return _generate_mock_messages(channel_link, limit)
    
    # прод
    if not client:
        raise HTTPException(status_code=503, detail="Telegram client not initialized")
    
    try:
        # извлекаем username
        channel_username = (
            channel_link
            .replace("https://t.me/", "")
            .replace("http://t.me/", "")
            .replace("@", "")
            .strip("/")
        )
        
        if not channel_username:
            raise HTTPException(status_code=400, detail="Invalid channel link")
        
        entity = await client.get_entity(channel_username)
        
        messages = await client.get_messages(entity, limit=limit)
        
        result = []
        for msg in messages:
            if msg.text:
                result.append({
                    "id": msg.id,
                    "date": msg.date.isoformat() if msg.date else None,
                    "text": msg.text[:4000],  # обрезаем очень длинные
                    "views": getattr(msg, "views", None),
                    "forwards": getattr(msg, "forwards", None),
                    "media": bool(msg.media) if msg.media else False
                })
        
        return {
            "channel": getattr(entity, "title", getattr(entity, "username", channel_username)),
            "username": getattr(entity, "username", None),
            "count": len(result),
            "messages": result,
            "_mock": False
        }
        
    except FloodWaitError as e:
        logger.warning(f"Rate limited by Telegram: {e.seconds}s")
        return JSONResponse(
            status_code=429,
            content={"error": "Rate limited", "retry_after": e.seconds}
        )
    except ValueError as e:
        logger.error(f"Invalid channel: {e}")
        raise HTTPException(status_code=404, detail=f"Channel not found: {channel_username}")
    except Exception as e:
        logger.error(f"Unexpected error: {type(e).__name__}: {e}")
        raise HTTPException(status_code=500, detail=f"Internal error: {str(e)}")


# Helper для генерации мок-данных
def _generate_mock_messages(channel: str, limit: int) -> dict:
    """Генерирует реалистичные тестовые сообщения для разработки"""
    
    samples = [
        "Привет! Это тестовое сообщение из канала.",
        "🚀 Важное обновление: мы запустили новую функцию!",
        "Сегодня отличная погода для программирования ☀️",
        "Не забудьте подписаться на наш канал!",
        "📊 Краткий дайджест новостей за неделю:",
        "🔥 Горячая новость: только что произошло что-то важное!",
        "💡 Совет дня: всегда делайте бэкапы данных.",
        "Мы работаем над улучшением сервиса. Спасибо за терпение!",
        "🎉 Поздравляем с достижением 1000 подписчиков!",
        "📢 Анонс: завтра вебинар по интересной теме."
    ]
    
    messages = []
    base_time = datetime.now()
    
    for i in range(limit):
        messages.append({
            "id": 10000 + i,
            "date": (base_time - timedelta(minutes=i*17)).isoformat(),
            "text": f"[{channel}] {random.choice(samples)} (mock #{i+1})",
            "views": random.randint(50, 10000),
            "forwards": random.randint(0, 500),
            "media": random.choice([True, False])
        })
    
    return {
        "channel": channel,
        "username": channel,
        "count": len(messages),
        "messages": messages,
        "_mock": True,
        "_note": "This is mock data for development. Set TELEGRAM_API_ID/HASH for real data."
    }