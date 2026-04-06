# Anagram — Telegram Summarizer

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-purple?logo=kotlin)](https://kotlinlang.org)a
[![Ktor](https://img.shields.io/badge/Ktor-2.3.7-blue?logo=kotlin)](https://ktor.io)
[![Python](https://img.shields.io/badge/Python-3.11-green?logo=python)](https://python.org)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.115-009688?logo=fastapi)](https://fastapi.tiangolo.com)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-336791?logo=postgresql)](https://postgresql.org)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)](https://docker.com)

**Микросервисная система для автоматического саммари сообщений из Telegram-каналов с использованием LLM.**

---

## Архитектура

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           CLIENT (Browser)                              │
│                        http://localhost:8080                            │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │ HTTP/JSON
                                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    KOTLIN BACKEND (Ktor, Port 8080)                     │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐   │
│  │   Routing   │  │  Controllers │  │   Services   │  │  Database   │   │
│  │   /api/*    │  │  DTO/JSON    │  │ Groq/Telegram│  │  Exposed    │   │
│  └─────────────┘  └──────────────┘  └──────────────┘  └─────────────┘   │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              │                  │                  │
              ▼                  ▼                  ▼
┌─────────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│  PYTHON SERVICE     │ │   GROQ API      │ │   PostgreSQL    │
│  (FastAPI, 8000)    │ │   (LLM)         │ │   (5432)        │
│  - Telegram Fetch   │ │   - Summarize   │ │   - History     │
│  - Mock Mode        │ │   - Llama 3.1   │ │   - Sessions    │
└─────────────────────┘ └─────────────────┘ └─────────────────┘
```

### Ключевые компоненты

| Компонент | Технология | Порт | Назначение |
|-----------|-----------|------|------------|
| **Backend** | Kotlin + Ktor | 8080 | API, бизнес-логика, рендеринг UI |
| **Telegram Service** | Python + FastAPI | 8000 | Получение сообщений из каналов |
| **Database** | PostgreSQL 15 | 5432 | Хранение истории саммари |
| **LLM Provider** | Groq API | — | Генерация саммари (Llama 3.1) |

---

## Технологический стек

### Backend (Kotlin)
- **Ktor** — асинхронный веб-фреймворк
- **Exposed** — типобезопасный ORM для работы с БД
- **kotlinx.serialization** — сериализация JSON
- **Kotlin Logging** + **Logback** — логирование
- **HikariCP** — пул подключений к БД

### Microservice (Python)
- **FastAPI** — асинхронный API-сервис
- **Telethon** — клиент Telegram API
- **Pydantic** — валидация данных
- **Uvicorn** — ASGI-сервер

### Infrastructure
- **Docker Compose** — оркестрация сервисов
- **PostgreSQL** — реляционная база данных
- **Groq Cloud** — LLM-провайдер (Llama 3.1 8B)

---

## Как запустить

### Требования

- Docker и Docker Compose
- Java 21+ (для локальной разработки)
- Python 3.11+ (для локальной разработки)
- `.env` файл с конфигурацией (см. ниже)

### 1. Настройка переменных окружения

Создайте файл `.env` в корне проекта:

```bash
cp .env.example .env
```

Заполните его:

```env
DATABASE_URL=jdbc:postgresql://localhost:5432/anagram_db
DATABASE_USER=postgres
DATABASE_PASSWORD=password

GROQ_API_KEY=realapikey
TELEGRAM_API_ID=realapiid
TELEGRAM_API_HASH=realapihash

SERVER_PORT=8080
```

> ⚠ **Mock-режим:** Если `TELEGRAM_API_ID` и `TELEGRAM_API_HASH` не являются реальными ключами, сервис автоматически работает в режиме эмуляции (возвращает тестовые данные).

### 3. Запуск через Docker (рекомендуется) 
```bash
# Запуск всех сервисов
docker-compose up -d --build

# Проверка статуса
docker-compose ps

# Просмотр логов
docker-compose logs -f
```

**Доступные GET эндпоинты:**

*Backend:*
- main: http://localhost:8080
- health: http://localhost:8080/health
- db: http://localhost:8080/api/summaries
- docs (swagger): http://localhost:8080/api/docs

*TelegramFetcher:*
- main: http://localhost:8000
- health: http://localhost:8000/health
- docs (swagger): http://localhost:8000/docs

### 4. Локальная разработка

```bash
# 1. Запустить инфраструктуру (БД + Python)
docker-compose up -d db telegram-service

# 2. Запустить Kotlin-бэкенд
./gradlew clean build run

# 3. (Опционально) Запустить Python локально
cd telegram-service
python -m venv venv
source venv/bin/activate  # Linux/macOS
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

---

## API Endpoints

| Метод | Эндпоинт | Описание |
|-------|----------|----------|
| `GET` | `/` | Главная страница (Frontend) |
| `GET` | `/health` | Проверка работоспособности |
| `GET` | `/api/docs` | Swagger UI документация |
| `POST` | `/api/summarize` | Саммари произвольного текста |
| `POST` | `/api/channels/refresh` | Получить сообщения из канала + саммари |
| `GET` | `/api/summaries` | История саммари (текущая сессия) |

### Пример запроса

```bash
curl -X POST http://localhost:8080/api/channels/refresh \
  -H "Content-Type: application/json" \
  -d '{"channel_link": "durov", "limit": 5}'
```

**Ответ:**
```json
{
  "channel": "durov",
  "messages_count": 5,
  "summary": "Краткое содержание последних сообщений...",
  "is_mock": true,
  "saved": true
}
```

---

## 🪵 Работа с логами

### Docker-контейнеры

```bash
# Все логи в реальном времени
docker-compose logs -f

# Только бэкенд
docker-compose logs -f backend

# Только база данных
docker-compose logs -f db

# Только Python-сервис
docker-compose logs -f telegram-service

# Последние 50 строк
docker-compose logs --tail=50 backend

# Поиск по логам
docker-compose logs backend | grep "ERROR"
docker-compose logs backend | grep "GroqSummarizer"
```

### Локальная разработка

Логи выводятся в консоль при запуске `./gradlew run`.

**Уровни логирования:**
```bash
# INFO (по умолчанию) — важные события
./gradlew run

# DEBUG — подробная отладка
LOG_LEVEL=DEBUG ./gradlew run

# ERROR — только ошибки
LOG_LEVEL=ERROR ./gradlew run
```

**Файловые логи** (если настроены в `logback.xml`):
```bash
# Просмотр файла
tail -f logs/anagram.log

# Поиск ошибок
grep "ERROR" logs/anagram.log
```

---

## Production-версия (план развития)

Текущая версия — **MVP для демонстрации архитектуры**. В продакшене планируется:

| Компонент          | Сейчас              | В продакшене                                  |
|--------------------|---------------------|-----------------------------------------------|
| **Аутентификация** | —                    | JWT + OAuth2 (Google/Telegram)                |
| **Frontend**       | —                    | Kotlin/JS                                     |
| **База данных**    | PostgreSQL (локально) | PostgreSQL + репликация, бэкапы               |
| **Кэширование**    | —                   | Redis для часто запрашиваемых каналов         |
| **Очереди**        | —                   | RabbitMQ/Kafka для асинхронной обработки      |
| **Деплой**         | Docker Compose      | Возжможен переход на Kubernetes (GKE/AWS EKS) |
| **CI/CD**          | —                   | GitHub Actions (тесты -> build -> deploy)     |
| **Telegram API**   | Mock-режим          | Реальные ключи (при получении доступа)        |