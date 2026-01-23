package com.hellza.cloudrun

import com.google.cloud.Timestamp
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

// --- DTO定義 (SyncDtosの中身をここに統合) ---
@Serializable
data class SyncEventDto(
    @SerialName("event_id") val eventId: String,
    val timestamp: String = "",
    val type: String = "",
    @SerialName("card_id") val cardId: String? = null,
    val delta: Int? = null,
    @SerialName("new_point") val newPoint: Int? = null,
    @SerialName("tag_uid") val tagUid: String? = null
)

@Serializable
data class ApplyResult(
    val acceptedIds: List<String>,
    val duplicateIds: List<String>,
    val invalidIds: List<String>
)

// --- リポジトリ実装 ---
class Repo {
    private val db: Firestore by lazy {
        FirestoreOptions.getDefaultInstance().service
    }

    suspend fun getCardView(cid: String): CardView? = withContext(Dispatchers.IO) {
        val doc = db.collection("cards").document(cid).get().get()
        if (!doc.exists()) return@withContext null

        val point = (doc.getLong("point") ?: 0L).toInt()
        val updatedAtIso = doc.getTimestamp("updatedAt")?.let { ts ->
            Instant.ofEpochSecond(ts.seconds, ts.nanos.toLong()).toString()
        } ?: doc.getString("updatedAt") ?: ""

        CardView(cid, point, updatedAtIso)
    }

    suspend fun getPinHash(cid: String): String? = withContext(Dispatchers.IO) {
        val doc = db.collection("cards").document(cid).get().get()
        if (!doc.exists()) return@withContext null
        doc.getString("pinHash")
    }

    // 管理者用：強制上書き保存
    suspend fun forceSetPin(cid: String, pinHash: String, point: Int) {
        withContext(Dispatchers.IO) {
            val data = mapOf(
                "pinHash" to pinHash,
                "point" to point,
                "updatedAt" to Timestamp.now()
            )
            db.collection("cards").document(cid).set(data, SetOptions.merge()).get()
        }
    }

    // 同期イベント処理
    suspend fun applyEvents(events: List<SyncEventDto>): ApplyResult = withContext(Dispatchers.IO) {
        val accepted = mutableListOf<String>()
        val duplicate = mutableListOf<String>()
        val invalid = mutableListOf<String>()

        for (e in events) {
            val eventId = e.eventId.trim()
            val cid = (e.cardId ?: "").trim()
            val delta = e.delta

            if (eventId.isBlank() || cid.isBlank() || delta == null) {
                invalid.add(eventId.ifBlank { "(missing)" })
                continue
            }

            val eventRef = db.collection("events").document(eventId)
            val cardRef = db.collection("cards").document(cid)

            val ok = db.runTransaction { tx ->
                if (tx.get(eventRef).get().exists()) return@runTransaction "duplicate"

                val cardSnap = tx.get(cardRef).get()
                val currentPoint = if (cardSnap.exists()) (cardSnap.getLong("point") ?: 0L).toInt() else 0
                val newPoint = currentPoint + delta

                if (cardSnap.exists()) {
                    tx.update(cardRef, mapOf("point" to newPoint, "updatedAt" to Timestamp.now()))
                } else {
                    tx.set(cardRef, mapOf("point" to newPoint, "updatedAt" to Timestamp.now(), "pinHash" to ""))
                }

                tx.set(eventRef, mapOf(
                    "cardId" to cid,
                    "delta" to delta,
                    "timestamp" to e.timestamp,
                    "createdAt" to Timestamp.now()
                ))
                "accepted"
            }.get()

            when (ok) {
                "accepted" -> accepted.add(eventId)
                "duplicate" -> duplicate.add(eventId)
                else -> invalid.add(eventId)
            }
        }
        ApplyResult(accepted, duplicate, invalid)
    }
}