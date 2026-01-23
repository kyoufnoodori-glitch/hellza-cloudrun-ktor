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

fun main() {
    val port = (System.getenv("PORT") ?: "8080").toInt()
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    install(CallLogging)
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    val repo = Repo() // Repo.ktを使う
    val pinSecret = System.getenv("HP_PIN_SECRET") ?: "dev-pin-secret"

    // ★ここを修正: 変数名を adminTokenFromEnv にして、全体でこれを使うように統一
    val adminTokenFromEnv = System.getenv("HP_ADMIN_TOKEN") ?: "admin-token-very-long"

    routing {
        // =========== 元々のAPI機能 ===========

        // 1. 管理者用：PIN設定
        post("/api/admin/setpin") {
            val token = call.request.header("Authorization")?.removePrefix("Bearer ")?.trim()

            // ★修正: 内側での再定義をやめ、外側の変数と比較する
            if (token != adminTokenFromEnv) {
                return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid admin token"))
            }

            val req = call.receive<AdminSetPinRequest>()
            val cid = req.cid
            val pin = req.pin
            if (cid.isNullOrBlank() || pin.isNullOrBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("cid and pin required"))
            }
            val hash = Auth.calcPinHash(pinSecret, cid, pin)
            repo.forceSetPin(cid, hash, req.initialPoint ?: 0)
            call.respond(OkResponse(true))
        }

        // 2. ユーザー用：PIN検証
        post("/api/pin") {
            val req = call.receive<PinRequest>()
            val cid = req.cid ?: return@post call.respond(HttpStatusCode.BadRequest)
            val pin = req.pin ?: return@post call.respond(HttpStatusCode.BadRequest)
            val storedHash = repo.getPinHash(cid) ?: return@post call.respond(HttpStatusCode.NotFound)
            val actual = Auth.calcPinHash(pinSecret, cid, pin)
            if (actual != storedHash) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("pin invalid"))
                return@post
            }
            call.respond(OkResponse(true))
        }

        // 3. ユーザー用：カード情報取得
        get("/api/card") {
            val cid = call.request.queryParameters["cid"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val cardView = repo.getCardView(cid) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(cardView)
        }

        // ★追加: アプリからのログ同期（管理者用）
        post("/api/admin/syncEvents") {
            // 認証チェック
            val token = call.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
            if (token != adminTokenFromEnv) {
                return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid admin token"))
            }

            try {
                // ★修正: 変数に入れずにデータを受け取る（警告対策）
                call.receive<String>()

                // 本来はここでデータを解析して保存するが、今はOKだけ返す
                call.respond(OkResponse(true))
            } catch (_: Exception) { // ★修正: 使わない変数は _ にする
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid JSON format"))
            }
        }

        // =========== Web画面を表示する機能 ===========

        // 4. app.js や styles.css
        staticResources("/c", "static")

        // 5. 画像ファイル
        staticResources("/assets", "static/assets")

        // 6. index.html 表示
        get("/c/{cid}") {
            val content = this::class.java.classLoader.getResource("static/index.html")?.readText()
            if (content != null) {
                call.respondText(content, ContentType.Text.Html)
            } else {
                call.respond(HttpStatusCode.NotFound, "index.html not found on server")
            }
        }
    }
}