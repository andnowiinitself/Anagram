package services

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// used in main

@Serializable
data class SummarizeRequest(
    val text: String,
    val maxLength: Int = 300
)

@Serializable
data class SummarizeResponse(
    val summary: String,
    val original_length: Int,
    val summary_length: Int,
    val tokensUsed: Int? = null,
    val provider: String
)

@Serializable
data class ErrorResponse(
    val error: String
)

@Serializable
data class ChannelRefreshRequest(
    val channel_link: String,
    val limit: Int = 5
)

@Serializable
data class ChannelRefreshResponse(
    val channel: String,
    val messages_count: Int,
    val summary: String,
    val is_mock: Boolean
)

// used in telegram fetcher

@Serializable
data class TelegramMessage(
    val id: Int,
    val date: String?,
    val text: String,
    val views: Int?,
    val forwards: Int?,
    val media: Boolean?
)

@Serializable
data class TelegramFetchResult(
    val channel: String,
    val username: String?,
    val count: Int,
    val messages: List<TelegramMessage>,

    @SerialName("_mock")
    val isMock: Boolean = false
)