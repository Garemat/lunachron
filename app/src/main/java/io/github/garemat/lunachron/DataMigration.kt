package io.github.garemat.lunachron

import android.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object DataMigration {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(payload: MigrationPayload): String {
        val jsonBytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        val compressed = ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { it.write(jsonBytes) }
            baos.toByteArray()
        }
        return Base64.encodeToString(compressed, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    fun decode(code: String): MigrationPayload {
        val compressed = Base64.decode(code.trim(), Base64.URL_SAFE)
        val jsonBytes = GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
        val payload = json.decodeFromString<MigrationPayload>(jsonBytes.toString(Charsets.UTF_8))
        if (payload.expiresAt > 0 && System.currentTimeMillis() > payload.expiresAt) {
            throw IllegalStateException("This code has expired. Generate a new one on the source device.")
        }
        return payload
    }
}
