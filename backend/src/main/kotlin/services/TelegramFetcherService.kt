package services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}


interface TelegramFetcher {
    suspend fun fetchMessages(channelLink: String, limit: Int): TelegramFetchResult
}

// реализация через python микросервис
class PythonTelegramFetcher(
    private val baseUrl: String,  // Например: "http://localhost:8000"
    private val timeoutMs: Long = 10000
) : TelegramFetcher {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        // таймауты, чтоб не висеть вечно
        install(HttpTimeout) {
            requestTimeoutMillis = timeoutMs
            socketTimeoutMillis = timeoutMs
            connectTimeoutMillis = timeoutMs
        }
        expectSuccess = true
    }

    override suspend fun fetchMessages(channelLink: String, limit: Int): TelegramFetchResult {
        logger.debug {"TelegramFetcher called"}

        try {
            val response = client.get("$baseUrl/fetch_messages") {
                parameter("channel_link", channelLink)
                parameter("limit", limit)
            }
            logger.info {"Messages fetched"}
            return response.body<TelegramFetchResult>()

        } catch (e: Exception) {
            logger.error(e) {"Error fetching from Telegram service: ${e.message}"}
            return TelegramFetchResult(
                channel = channelLink,
                username = null,
                count = 0,
                messages = emptyList(),
                isMock = false
            )
        }
    }
}