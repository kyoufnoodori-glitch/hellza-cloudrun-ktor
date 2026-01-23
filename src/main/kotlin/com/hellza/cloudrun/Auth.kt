package com.hellza.cloudrun

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Auth {
    fun calcPinHash(pinSecret: String, cid: String, pin: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(pinSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val raw = mac.doFinal("${cid}:${pin}".toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    }

    fun isSessionValid(session: UserSession, nowEpochSec: Long, ttlSec: Long = 3600): Boolean {
        val age = nowEpochSec - session.issuedAtEpochSec
        return age in 0..ttlSec
    }

    fun safeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].code xor b[i].code)
        }
        return diff == 0
    }
}