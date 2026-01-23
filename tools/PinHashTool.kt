package com.hellza.cloudrun.tools

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * pinHash生成ツール（Kotlinワンファイル）
 *
 * 方式:
 *   pinHash = Base64URL( HMAC-SHA256(HP_PIN_SECRET, "{cid}:{pin}") )
 *
 * 使い方（例）:
 *   # 環境変数で秘密鍵を渡す
 *   set HP_PIN_SECRET=change-this-to-long-random-string
 *
 *   # 実行（Gradle無しでkotlin実行できる環境なら）
 *   kotlin PinHashTool.kt HP-000123 1234
 *
 *   # 出力された pinHash を Firestore の cards/{cid} ドキュメントに
 *   # フィールド名 "pinHash" として貼り付け
 *
 * 引数で秘密鍵を渡す場合:
 *   kotlin PinHashTool.kt HP-000123 1234 --secret=xxxxx
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("Usage: PinHashTool <CID> <PIN> [--secret=... | --secret ...]")
        return
    }

    val cid = args[0].trim()
    val pin = args[1].trim()

    val secretFromArg = parseSecretArg(args)
    val secret = secretFromArg
        ?: System.getenv("HP_PIN_SECRET")?.takeIf { it.isNotBlank() }
        ?: run {
            System.err.println("Missing secret. Set env HP_PIN_SECRET or pass --secret.")
            return
        }

    val msg = "$cid:$pin"
    val hash = b64url(hmacSha256(secret, msg))
    println(hash)
}

private fun parseSecretArg(args: Array<String>): String? {
    for (i in args.indices) {
        val a = args[i]
        if (a.startsWith("--secret=")) return a.removePrefix("--secret=").trim()
        if (a == "--secret" && i + 1 < args.size) return args[i + 1].trim()
    }
    return null
}

private fun hmacSha256(secret: String, msg: String): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(msg.toByteArray(Charsets.UTF_8))
}

private fun b64url(bytes: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
