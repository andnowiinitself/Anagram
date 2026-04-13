import os
import logging
import asyncio
from pathlib import Path
from telethon import TelegramClient
from dotenv import load_dotenv

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

env_path = Path(__file__).resolve().parent.parent.parent / ".env"
load_dotenv(dotenv_path=env_path)

API_ID = int(os.getenv("TELEGRAM_API_ID"))
API_HASH = os.getenv("TELEGRAM_API_HASH")

client = TelegramClient('anagram', API_ID, API_HASH)

async def main():
    logger.info("Starting authorization...")
    await client.start()
    logger.info("Authorized!")
    logger.info(f"Logged in as: {(await client.get_me()).first_name}")
    
    # попробуем получить информацию о канале
    try:
        entity = await client.get_entity("durov")
        logger.debug(f"Test channel: {entity.title}")
    except Exception as e:
        logger.error(f"Could not fetch channel: {e}")
    
    await client.disconnect()
    logger.info("Disconnected. Session saved to 'anagram.session'")

if __name__ == "__main__":
    asyncio.run(main())