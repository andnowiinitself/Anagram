import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.swagger.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import io.github.cdimascio.dotenv.dotenv
import kotlinx.serialization.json.*
import kotlinx.serialization.json.JsonArray
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import mu.KotlinLogging
import plugins.configureDatabase
import plugins.Summaries
import plugins.saveSummary
import services.SummarizerService
import services.GroqSummarizer
import services.TelegramFetcher
import services.PythonTelegramFetcher
import services.SummarizeRequest
import services.SummarizeResponse
import services.ErrorResponse
import services.ChannelRefreshRequest
import services.ChannelRefreshResponse

private val logger = KotlinLogging.logger {}


fun main() {
    val envFile = java.io.File(".env")

    logger.info {".env file exists: ${envFile.exists()}, absolute path: ${envFile.absolutePath}"}
    if (envFile.exists()) {
        dotenv().entries().forEach { entry ->
            System.setProperty(entry.key, entry.value)
        }
    } else {
        logger.info{".env file not found (likely because running in Docker)"}
    }

    logger.info {"Starting backend..."}

    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}


fun Application.module() {

    // для поддержки mapOf
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        })
    }

    configureDatabase()

    // --- выбор провайдера делать здесь ---
    val groqApiKey = System.getProperty("GROQ_API_KEY")
        ?: System.getenv("GROQ_API_KEY")
        ?: ""
    if (groqApiKey.isNotEmpty() && groqApiKey.startsWith("gsk_")) {
        logger.debug {"Summarizer api key loaded"}
    } else {
        logger.warn {"Summarizer api key is missing or invalid"}
    }
    val summarizer: SummarizerService = GroqSummarizer(groqApiKey)

    val telegramUrl = System.getProperty("TELEGRAM_SERVICE_URL")
        ?: System.getenv("TELEGRAM_SERVICE_URL")
        ?: "http://localhost:8000"
    logger.debug {"Telegram-Service url loaded"}
    val telegramFetcher: TelegramFetcher = PythonTelegramFetcher(telegramUrl)


    routing {
        swaggerUI("/api/docs", "documentation.yaml")

        get("/") {
            call.respondText("ANAGRAM BACKEND IS RUNNING")
        }

        get("/health") {
            call.respond(mapOf("status" to "ok", "service" to "backend"))
        }

        post("/api/summarize") {
            val request = call.receive<SummarizeRequest>()
            val text = request.text

            if (text.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Text cannot be empty"))
                return@post
            }

            val summary = summarizer.summarize(text)

            call.respond(
                HttpStatusCode.OK,
                SummarizeResponse(
                    summary = summary,
                    original_length = text.length,
                    summary_length = summary.length,
                    tokensUsed = null, // пока не реализовано
                    provider = "Groq"
                )
            )
        }

        post("/api/channels/refresh") {
            val request = call.receive<ChannelRefreshRequest>()

            if (request.channel_link.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("channel_link is required"))
                return@post
            }

            val fetchResult = telegramFetcher.fetchMessages(
                request.channel_link,
                request.limit
            )

            val summary = if (fetchResult.messages.isNotEmpty()) {
                val combinedText = fetchResult.messages.joinToString("\n---\n") { it.text }
                val sum = summarizer.summarize(combinedText)
                try {
                    saveSummary(fetchResult.channel, combinedText, sum, fetchResult.isMock)
                } catch(e: Exception) {
                    logger.warn(e) {"Summary has not been saved: ${e.message ?: "unknown error"}"}
                }
                sum
            } else {
                "No messages found in channel"
            }

            call.respond(
                HttpStatusCode.OK,
                ChannelRefreshResponse(
                    channel = fetchResult.channel,
                    messages_count = fetchResult.count,
                    summary = summary,
                    is_mock = fetchResult.isMock
                )
            )
        }

        get("/api/summaries") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
            logger.debug {"GET /api/summaries?limit=$limit"}

            val histories = transaction {
                Summaries.selectAll()
                    .orderBy(Summaries.createdAt to SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        buildJsonObject {
                            put("id", row[Summaries.id])
                            put("channel", row[Summaries.channel])
                            put("summary", row[Summaries.summary])
                            put("created_at", row[Summaries.createdAt].toString())
                            put("is_mock", row[Summaries.isMock])
                        }
                    }
            }

            logger.debug {"Returning ${histories.size} summaries"}
            call.respond(buildJsonObject {
                put("count", histories.size)
                put("items", JsonArray(histories))
            })
        }
    }
}
