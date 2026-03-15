package com.garemat.moonstone_companion

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipInputStream

private const val TAG = "DataUpdateRepository"
private const val DATA_REPO = "garemat/moonstone-companion-data"
private const val PREFS_NAME = "moonstone_prefs"
private const val KEY_IMAGE_VERSION = "image_version"
private const val KEY_SKIP_DATA_VERSION = "skip_data_version"
private const val KEY_SKIP_IMAGE_VERSION = "skip_image_version"

@Serializable
private data class ApiRelease(
    @SerialName("tag_name") val tagName: String,
    val assets: List<ApiAsset> = emptyList()
)

@Serializable
private data class ApiAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String
)

class DataUpdateRepository(
    private val context: Context,
    private val repository: LocalCharacterRepository,
    private val client: HttpClient = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 15000
        }
    }
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val apiBase = "https://api.github.com/repos/$DATA_REPO/releases/latest"

    /**
     * Returns the latest release if it is strictly newer than the installed compendium
     * and not skipped by the user.
     *
     * If the latest release has an incompatible schema (major version > SUPPORTED_SCHEMA)
     * the returned [GitHubRelease] will have [GitHubRelease.schemaIncompatible] = true so
     * the UI can prompt the user to update the app instead of offering a data download.
     */
    suspend fun checkForDataUpdate(): GitHubRelease? = try {
        val body = client.get(apiBase).bodyAsText()
        val release = json.decodeFromString<ApiRelease>(body)
        val installed = CharacterData.getInstalledVersion(context)
        val skipped = prefs.getString(KEY_SKIP_DATA_VERSION, null)
        when {
            !CharacterData.isNewer(release.tagName, installed) -> null
            release.tagName == skipped -> null
            !CharacterData.isSchemaCompatible(release.tagName) ->
                GitHubRelease(tagName = release.tagName, schemaIncompatible = true)
            else -> GitHubRelease(
                tagName = release.tagName,
                assets = release.assets.map { GitHubAsset(it.name, it.browserDownloadUrl) }
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "checkForDataUpdate failed", e)
        null
    }

    /** Returns latest release tag if images are newer than installed and not skipped. */
    suspend fun checkForImageUpdate(): String? = try {
        val body = client.get(apiBase).bodyAsText()
        val release = json.decodeFromString<ApiRelease>(body)
        val installed = prefs.getString(KEY_IMAGE_VERSION, null)
        val skipped = prefs.getString(KEY_SKIP_IMAGE_VERSION, null)
        if (release.tagName != installed && release.tagName != skipped) release.tagName else null
    } catch (e: Exception) {
        Log.w(TAG, "checkForImageUpdate failed", e)
        null
    }

    suspend fun applyDataUpdate(release: GitHubRelease) {
        Log.d(TAG, "applyDataUpdate: starting for ${release.tagName}")
        val dataDir = File(context.filesDir, "data").also { it.mkdirs() }
        val asset = release.assets.firstOrNull { it.name == "compendium.json" }
            ?: throw IllegalStateException("compendium.json not found in release ${release.tagName}")
        Log.d(TAG, "applyDataUpdate: downloading compendium.json from ${asset.browserDownloadUrl}")
        val bytes = client.get(asset.browserDownloadUrl).readBytes()
        File(dataDir, "compendium.json").writeBytes(bytes)
        Log.d(TAG, "applyDataUpdate: saved compendium.json (${bytes.size} bytes)")
        repository.seedFromFiles(dataDir)
        Log.d(TAG, "applyDataUpdate: complete, version now ${release.tagName}")
    }

    suspend fun downloadImages(releaseTag: String) {
        val url = "https://api.github.com/repos/$DATA_REPO/releases/tags/$releaseTag"
        val body = client.get(url).bodyAsText()
        val release = json.decodeFromString<ApiRelease>(body)
        val zipAsset = release.assets.firstOrNull { it.name == "character_images.zip" }
            ?: throw IllegalStateException("character_images.zip not found in release $releaseTag")

        val zipBytes = client.get(zipAsset.browserDownloadUrl).readBytes()
        val imagesDir = File(context.filesDir, "images").also { it.mkdirs() }

        ZipInputStream(zipBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = File(imagesDir, File(entry.name).name)
                    outFile.outputStream().use { out -> zis.copyTo(out) }
                }
                entry = zis.nextEntry
            }
        }
        prefs.edit().putString(KEY_IMAGE_VERSION, releaseTag).apply()
    }

    fun markDataVersionSkipped(tag: String) {
        prefs.edit().putString(KEY_SKIP_DATA_VERSION, tag).apply()
    }

    fun markImageVersionSkipped(tag: String) {
        prefs.edit().putString(KEY_SKIP_IMAGE_VERSION, tag).apply()
    }

    fun isFirstImageLaunch(): Boolean = prefs.getString(KEY_IMAGE_VERSION, null) == null

    fun persistImagePreference(pref: ImageDownloadPreference) {
        prefs.edit().putString("image_download_pref", pref.name).apply()
    }

    fun loadImagePreference(): ImageDownloadPreference {
        val saved = prefs.getString("image_download_pref", null) ?: return ImageDownloadPreference.PROMPT
        return try { ImageDownloadPreference.valueOf(saved) } catch (_: Exception) { ImageDownloadPreference.PROMPT }
    }

    fun persistAutoCheck(enabled: Boolean) {
        prefs.edit().putBoolean("auto_check_data_updates", enabled).apply()
    }

    fun loadAutoCheck(): Boolean = prefs.getBoolean("auto_check_data_updates", true)
}
