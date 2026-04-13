package plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}


object Summaries : Table("summaries") {
    val id = integer("id").autoIncrement()
    val channel = varchar("channel", 255)
    val originalText = text("original_text")
    val summary = text("summary")
    val createdAt = datetime("created_at")
    val isMock = bool("is_mock").default(false)

    override val primaryKey = PrimaryKey(id)
}

fun Application.configureDatabase() {
    // извлекаем значения переменных окружения
    val dbUrl = System.getProperty("DATABASE_URL")
        ?: System.getenv("DATABASE_URL")
        ?: throw IllegalStateException("DATABASE_URL not found")
    val dbUser = System.getProperty("DATABASE_USER")
        ?: System.getenv("DATABASE_USER")
        ?: throw IllegalStateException("DATABASE_USER not found")
    val dbPassword = System.getProperty("DATABASE_PASSWORD")
        ?: System.getenv("DATABASE_PASSWORD")
        ?: throw IllegalStateException("DATABASE_PASSWORD not found")

    val config = HikariConfig().apply {
        jdbcUrl = dbUrl
        username = dbUser
        password = dbPassword
        driverClassName = "org.postgresql.Driver"
        maximumPoolSize = 10
        isAutoCommit = false
    }

    logger.debug {"Connecting to database: $dbUrl"}
    Database.connect(HikariDataSource(config))

    // проверка подключения
    try {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Summaries)
        }
        logger.info {"Database connected successfully!"}
    } catch (e: Exception) {
        logger.error(e) {"Database connection failed: ${e.message}"}
        throw e
    }
}

fun saveSummary(channel: String, originalText: String, summary: String, isMock: Boolean) {
    transaction {
        Summaries.insert {
            it[this.channel] = channel
            it[this.originalText] = originalText.take(5000)
            it[this.summary] = summary
            it[createdAt] = LocalDateTime.now()
            it[this.isMock] = isMock
        }
    }
    logger.debug {"Summary saved for channel: $channel"}
}