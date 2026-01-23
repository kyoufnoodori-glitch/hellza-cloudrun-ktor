package com.hellza.cloudrun

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun main() {
    val port = (System.getenv("PORT") ?: "8080").toInt()
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    install(CallLogging)
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; isLenient = true })
    }

    val repo = Repo()
    val pinSecret = System.getenv("HP_PIN_SECRET") ?: "dev-pin-secret"
    val adminTokenFromEnv = System.getenv("HP_ADMIN_TOKEN") ?: "admin-token-very-long"

    // 簡易セッション署名用関数
    fun signSession(cid: String, ts: Long): String {
        val data = "$cid:$ts"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(pinSecret.toByteArray(), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.toByteArray()))
    }

    routing {
        // 1. 管理者用：PIN設定（ここは変更なし）
        post("/api/admin/setpin") {
            val token = call.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
            if (token != adminTokenFromEnv) {
                return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid admin token"))
            }
            val req = call.receive<AdminSetPinRequest>()
            if (req.cid.isNullOrBlank() || req.pin.isNullOrBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("cid/pin required"))
            }
            val hash = Auth.calcPinHash(pinSecret, req.cid, req.pin)
            repo.forceSetPin(req.cid, hash, req.initialPoint ?: 0)
            call.respond(OkResponse(true))
        }

        // 2. ユーザー用：PIN検証（★Cookie発行を追加）
        post("/api/pin") {
            val req = call.receive<PinRequest>()
            val cid = req.cid
            val pin = req.pin
            if (cid.isNullOrBlank() || pin.isNullOrBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing cid or pin"))
            }

            val storedHash = repo.getPinHash(cid)
            if (storedHash == null) {
                return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Card not found"))
            }

            val actual = Auth.calcPinHash(pinSecret, cid, pin)
            if (actual != storedHash) {
                return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid PIN"))
            }

            // ★認証成功：Cookieを作成
            val now = System.currentTimeMillis()
            val sig = signSession(cid, now)
            val cookieValue = "$cid:$now:$sig"

            // Cookieをセット（有効期限1時間）
            call.response.cookies.append(
                name = "hz_session",
                value = cookieValue,
                path = "/",
                maxAge = 3600L,
                httpOnly = true
            )

            call.respond(OkResponse(true))
        }

        // 3. ユーザー用：カード情報取得（★セキュリティチェック追加）
        get("/api/card") {
            val cidParam = call.request.queryParameters["cid"]
            if (cidParam.isNullOrBlank()) {
                return@get call.respond(HttpStatusCode.BadRequest)
            }

            // ★Cookieチェック開始
            val cookie = call.request.cookies["hz_session"]
            if (cookie.isNullOrBlank()) {
                return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Login required"))
            }

            try {
                val parts = cookie.split(":")
                if (parts.size != 3) throw Exception("Invalid cookie format")
                val (cookieCid, tsStr, sig) = parts

                // カードIDが一致しているか？
                if (cookieCid != cidParam) throw Exception("Session mismatch")

                // 署名が正しいか？
                val ts = tsStr.toLong()
                val expectedSig = signSession(cookieCid, ts)
                if (sig != expectedSig) throw Exception("Invalid signature")

                // 期限切れでないか（1時間）
                if (System.currentTimeMillis() - ts > 3600 * 1000) throw Exception("Session expired")

            } catch (e: Exception) {
                // 認証失敗
                return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Session invalid: ${e.message}"))
            }
            // ★Cookieチェック終了

            val cardView = repo.getCardView(cidParam)
            if (cardView == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(cardView)
            }
        }

        // 4. 管理者用：ログ同期
        post("/api/admin/syncEvents") {
            val token = call.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
            if (token != adminTokenFromEnv) {
                return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid admin token"))
            }
            try {
                val req = call.receive<SyncRequest>()
                var count = 0
                req.events.forEach { event ->
                    if (event.new_point != null && !event.card_id.isNullOrBlank()) {
                        repo.updatePoint(event.card_id, event.new_point, event.timestamp)
                        count++
                    }
                }
                println("Synced $count events.")
                call.respond(OkResponse(true))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Sync failed"))
            }
        }

        // Web画面
        staticResources("/c", "static")
        staticResources("/assets", "static/assets")

        get("/c/{cid}") {
            // index.htmlを返す（JSで/api/cardを叩いて認証エラーならPIN画面を出す）
            val content = this::class.java.classLoader.getResource("static/index.html")?.readText()
            if (content != null) {
                call.respondText(content, ContentType.Text.Html)
            } else {
                call.respond(HttpStatusCode.NotFound, "index.html not found")
            }
        }
    }
}