package id.dompetku.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The database passphrase: 32 random bytes generated on first launch,
 * stored only in wrapped (encrypted) form, unwrapped by an AndroidKeyStore key.
 */
object DbKey {
    private const val ALIAS = "dompetku_db_wrap"
    private const val PREFS = "dbkey"

    fun getOrCreate(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wrapped = prefs.getString("wrapped", null)
        val iv = prefs.getString("iv", null)
        if (wrapped != null && iv != null) {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            return c.doFinal(Base64.decode(wrapped, Base64.NO_WRAP))
        }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        store(context, raw)
        return raw
    }

    /** Replace the stored passphrase (used after restoring a backup). */
    fun store(context: Context, raw: ByteArray) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, wrapKey())
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("wrapped", Base64.encodeToString(c.doFinal(raw), Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(c.iv, Base64.NO_WRAP))
            .commit()
        check(saved) { "Kunci database tidak bisa disimpan" }
    }

    fun deleteWrapKey() {
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) }
    }

    private fun wrapKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }
}
