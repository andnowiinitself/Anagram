package services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}


interface SummarizerService {
    suspend fun summarize(text: String): String
}

// groq реализация
class GroqSummarizer(private val apiKey: String) : SummarizerService {

    // специфичные для groq dto

    @Serializable
    private data class GroqRequest(
        val model: String,
        val messages: List<GroqMessage>,
        val max_tokens: Int
    )

    @Serializable
    private data class GroqMessage(
        val role: String,
        val content: String
    )

    @Serializable
    private data class GroqResponse(
        val choices: List<GroqChoice>,
        val usage: GroqUsage? = null
    )

    @Serializable
    private data class GroqChoice(
        val message: GroqResponseMessage
    )

    @Serializable
    private data class GroqResponseMessage(
        val content: String
    )

    @Serializable
    private data class GroqUsage(
        val total_tokens: Int
    )

    @Serializable
    private data class GroqError(
        val error: GroqErrorDetail?
    )

    @Serializable
    private data class GroqErrorDetail(
        val message: String?
    )

    // -----------------------------------------------------------------------------------------------------------------

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    override suspend fun summarize(text: String): String {
        logger.debug {"Summarizer called"}

        try {
            val response = client.post("https://api.groq.com/openai/v1/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                header("Content-Type", "application/json")
                setBody(GroqRequest(
                    model = "llama-3.1-8b-instant",
                    messages = listOf(
                        GroqMessage("system", "Ты полезный ассистент. Делай краткие но информативные саммари текста."),
                        GroqMessage("user", "Сделай краткое саммари этого текста:\n\n$text")
                    ),
                    max_tokens = 300
                ))
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.body<GroqError>()
                val errorMessage = errorBody.error?.message ?: "Unknown error"
                logger.warn {"Groq API error (${response.status}): $errorMessage"}
                return "API error: $errorMessage"
            }

            val groqResponse = response.body<GroqResponse>()
            val summary = groqResponse.choices.firstOrNull()?.message?.content
            logger.info {"Summary generated"}
            val tokens = groqResponse.usage?.total_tokens

            logger.debug {"Groq tokens used: $tokens"}

            return summary ?: "Не удалось получить ответ от нейросети"

        } catch (e: Exception) {
            logger.error(e) {"Groq error: ${e.message}"}
            return "Error: ${e.message}"
        }
    }
}
