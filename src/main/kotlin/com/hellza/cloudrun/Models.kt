package com.hellza.cloudrun

import kotlinx.serialization.Serializable

@Serializable
data class AdminSetPinRequest(
    val cid: String? = null,
    val pin: String? = null,
    val initialPoint: Int? = null
)

@Serializable
data class PinRequest(
    val cid: String? = null,
    val pin: String? = null
)

@Serializable
data class SyncRequest(
    val events: List<SyncEvent> = emptyList()
)

@Serializable
data class SyncEvent(
    val event_id: String,
    // 数値(Long)になっていること
    val timestamp: Long,
    val type: String,
    val card_id: String? = null,
    val delta: Int? = null,
    val new_point: Int? = null,
    val tag_uid: String? = null
)

@Serializable
data class OkResponse(
    val ok: Boolean,
    val message: String = "ok"
)

@Serializable
data class ErrorResponse(
    val error: String
)

@Serializable
data class CardView(
    val cid: String,
    val point: Int,
    val updatedAt: Long
)

// ★これが抜けていました！これを追加すれば直ります
@Serializable
data class UserSession(
    val cid: String,
    val issuedAtEpochSec: Long
)