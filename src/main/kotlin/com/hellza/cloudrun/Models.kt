package com.hellza.cloudrun

import kotlinx.serialization.Serializable

// =========== 共通データクラス定義 ===========

@Serializable
data class AdminSetPinRequest(
    val cid: String? = null,
    val pin: String? = null,
    val initialPoint: Int? = 0
)

@Serializable
data class PinRequest(
    val cid: String? = null,
    val pin: String? = null
)

@Serializable
data class OkResponse(val ok: Boolean = true)

@Serializable
data class ErrorResponse(val message: String)

@Serializable
data class CardView(
    val cid: String,
    val point: Int,
    val updatedAt: Long,
    val state: String = "normal"
)

// 同期用
@Serializable
data class SyncRequest(val events: List<SyncEvent>)

@Serializable
data class SyncEvent(
    val event_id: String,
    val timestamp: Long,
    val type: String,
    val card_id: String?,
    val delta: Int?,
    val new_point: Int?,
    val tag_uid: String?
)

// Cookieセッション用 (Auth.ktで使用)
data class UserSession(
    val cid: String,
    val issuedAtEpochSec: Long
)