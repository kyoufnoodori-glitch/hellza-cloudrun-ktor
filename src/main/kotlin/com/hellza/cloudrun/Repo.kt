package com.hellza.cloudrun

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient

class Repo {
    private val db by lazy {
        FirestoreClient.getFirestore()
    }

    init {
        // ★変更点：ファイル読み込みを廃止し、サーバーの自動認証(Default)を使います
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                // プロジェクトIDを指定して初期化（Cloud Runが勝手に認証してくれます）
                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setProjectId("hellza-point") // ★あなたのプロジェクトID
                    .build()

                FirebaseApp.initializeApp(options)
                println("Firebase Initialized with Application Default Credentials.")
            } catch (e: Exception) {
                e.printStackTrace()
                println("Firebase Init Failed: ${e.message}")
            }
        }
    }

    // --- 以下、変更なし ---
    
    fun forceSetPin(cid: String, pinHash: String, initialPoint: Int) {
        val docRef = db.collection("cards").document(cid)
        val snapshot = docRef.get().get()

        val data = HashMap<String, Any>()
        data["cid"] = cid
        data["pinHash"] = pinHash
        data["updatedAt"] = System.currentTimeMillis()

        if (!snapshot.exists()) {
            data["point"] = initialPoint
        }
        docRef.set(data, com.google.cloud.firestore.SetOptions.merge())
    }

    fun updatePoint(cid: String, newPoint: Int, updatedAt: Long) {
        val docRef = db.collection("cards").document(cid)
        val data = mapOf(
            "cid" to cid,
            "point" to newPoint,
            "updatedAt" to updatedAt
        )
        docRef.set(data, com.google.cloud.firestore.SetOptions.merge())
    }

    fun getPinHash(cid: String): String? {
        val snapshot = db.collection("cards").document(cid).get().get()
        return if (snapshot.exists()) snapshot.getString("pinHash") else null
    }

    fun getCardView(cid: String): CardView? {
        val snapshot = db.collection("cards").document(cid).get().get()
        return if (snapshot.exists()) {
            val pt = snapshot.getLong("point")?.toInt() ?: 0
            val ts = snapshot.getLong("updatedAt") ?: 0L
            CardView(cid, pt, ts)
        } else {
            null
        }
    }
}