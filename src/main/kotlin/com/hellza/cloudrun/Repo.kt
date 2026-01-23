package com.hellza.cloudrun

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import kotlinx.serialization.Serializable
import java.io.InputStream

/**
 * Firestore接続版リポジトリ
 * src/main/resources/service-account.json を読み込んで接続します。
 */
class Repo {

    // Firestoreデータベースのインスタンス
    private val db by lazy {
        FirestoreClient.getFirestore()
    }

    init {
        // Firebaseの初期化 (アプリ起動時に1回だけ実行)
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                // resourcesフォルダから鍵ファイルを読み込む
                val serviceAccount: InputStream = this::class.java.getResourceAsStream("/service-account.json")
                    ?: throw RuntimeException("service-account.json not found in resources!")

                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build()

                FirebaseApp.initializeApp(options)
                println("Firebase Initialized successfully.")
            } catch (e: Exception) {
                e.printStackTrace()
                println("Firebase Init Failed: ${e.message}")
            }
        }
    }

    /**
     * PIN登録 (管理者用)
     * ドキュメントが存在しなければ作成、あればPINのみ更新（ポイント維持）
     */
    fun forceSetPin(cid: String, pinHash: String, initialPoint: Int) {
        val docRef = db.collection("cards").document(cid)
        val snapshot = docRef.get().get()

        val data = HashMap<String, Any>()
        data["cid"] = cid
        data["pinHash"] = pinHash
        data["updatedAt"] = System.currentTimeMillis()

        if (!snapshot.exists()) {
            // 新規作成時はポイントを入れる
            data["point"] = initialPoint
        }
        // マージ更新（既存のポイントなどは消さない）
        docRef.set(data, com.google.cloud.firestore.SetOptions.merge())
    }

    /**
     * ポイント更新 (アプリからの同期用)
     */
    fun updatePoint(cid: String, newPoint: Int, updatedAt: Long) {
        val docRef = db.collection("cards").document(cid)

        val data = mapOf(
            "cid" to cid,
            "point" to newPoint,
            "updatedAt" to updatedAt
        )
        // mergeにより、未登録カードでも自動で作られ、PIN設定済みの場合はPINを消さずに更新できる
        docRef.set(data, com.google.cloud.firestore.SetOptions.merge())
        println("Firestore: Updated $cid -> $newPoint")
    }

    fun getPinHash(cid: String): String? {
        val snapshot = db.collection("cards").document(cid).get().get()
        return if (snapshot.exists()) {
            snapshot.getString("pinHash")
        } else {
            null
        }
    }

    /**
     * Web表示用のデータを返す
     */
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