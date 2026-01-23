package com.hellza.cloudrun

import kotlinx.serialization.Serializable

@Serializable
data class PinRequest(
    val cid: String? = null,
    val pin: String? = null
)

@Serializable
data class AdminSetPinRequest(
    val cid: String? = null,
    val pin: String? = null,
    val initialPoint: Int? = null
)

@Serializable
data class OkResponse(
    val ok: Boolean = true
)

@Serializable
data class ErrorResponse(
    val message: String
)

@Serializable
data class CardView(
    val cid: String,
    val point: Int,
    val updatedAt: String,
    val state: String = "normal"
)

// Cookieセッション用
data class UserSession(
    val cid: String,
    val issuedAtEpochSec: Long
)