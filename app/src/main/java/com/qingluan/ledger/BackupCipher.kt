package com.qingluan.ledger

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

object BackupCipher {
    private const val ITERATIONS = 210000

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        require(password.size in 8..128) { "密码需8–128位" }
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try {
            SecretKeySpec(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,
                "AES",
            )
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(raw: String, password: CharArray): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD("qingluan-backup-v1".toByteArray())
        fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return JSONObject()
            .put("encrypted", "qingluan-v1")
            .put("iterations", ITERATIONS)
            .put("salt", b64(salt))
            .put("iv", b64(iv))
            .put("data", b64(cipher.doFinal(raw.toByteArray(Charsets.UTF_8))))
            .toString()
            .also {
                require(it.toByteArray(Charsets.UTF_8).size <= CompleteBackup.MAX_BYTES) {
                    "加密备份超过16MB，请缩小范围"
                }
            }
    }

    fun decrypt(raw: String, password: CharArray): String {
        require(raw.toByteArray(Charsets.UTF_8).size <= CompleteBackup.MAX_BYTES) { "文件超过16MB" }
        val j = JSONObject(raw)
        require(j.getString("encrypted") == "qingluan-v1" && j.getInt("iterations") == ITERATIONS) {
            "加密格式无效"
        }
        val salt = Base64.decode(j.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(j.getString("iv"), Base64.NO_WRAP)
        require(salt.size == 16 && iv.size == 12) { "加密参数无效" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD("qingluan-backup-v1".toByteArray())
        return try {
            String(
                cipher.doFinal(Base64.decode(j.getString("data"), Base64.NO_WRAP)),
                Charsets.UTF_8,
            )
        } catch (e: javax.crypto.AEADBadTagException) {
            throw IllegalArgumentException("密码错误或文件已损坏", e)
        }
    }
}
