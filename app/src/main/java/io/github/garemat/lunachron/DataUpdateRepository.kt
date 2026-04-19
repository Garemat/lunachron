package io.github.garemat.lunachron

import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.core.content.edit
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import io.ktor.client.*
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipInputStream

private const val TAG = "DataUpdateRepository"
private const val DATA_REPO = "garemat/lunachron-data"
private const val APP_REPO  = "garemat/lunachron"
private const val PREFS_NAME = "lunachron_prefs"
private const val KEY_IMAGE_VERSION = "image_version"
private const val KEY_SKIP_DATA_VERSION = "skip_data_version"
private const val KEY_SKIP_IMAGE_VERSION = "skip_image_version"

@Serializable
private data class ApiRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String = "",
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

    suspend fun downloadImages(
        releaseTag: String,
        onProgress: (downloaded: Long, total: Long, speedBps: Long) -> Unit = { _, _, _ -> }
    ) {
        val url = "https://api.github.com/repos/$DATA_REPO/releases/tags/$releaseTag"
        val body = client.get(url).bodyAsText()
        val release = json.decodeFromString<ApiRelease>(body)
        val zipAsset = release.assets.firstOrNull { it.name == "character_images.zip" }
            ?: throw IllegalStateException("character_images.zip not found in release $releaseTag")

        val response = client.get(zipAsset.browserDownloadUrl)
        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
        val channel = response.bodyAsChannel()

        val tempZip = File(context.cacheDir, "portraits_temp.zip")
        var downloaded = 0L
        var speedWindowStart = System.currentTimeMillis()
        var speedWindowBytes = 0L
        val readBuffer = ByteArray(DEFAULT_BUFFER_SIZE)

        tempZip.outputStream().use { out ->
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(readBuffer)
                if (read <= 0) break
                out.write(readBuffer, 0, read)
                downloaded += read
                speedWindowBytes += read
                val now = System.currentTimeMillis()
                val elapsed = now - speedWindowStart
                val speedBps = if (elapsed >= 500L) {
                    val bps = speedWindowBytes * 1000L / elapsed
                    speedWindowStart = now
                    speedWindowBytes = 0L
                    bps
                } else -1L // sentinel: don't update speed yet
                onProgress(downloaded, contentLength, speedBps)
            }
        }

        // Report 100% before extracting
        onProgress(downloaded, contentLength, 0L)

        val imagesDir = File(context.filesDir, "images").also { it.mkdirs() }
        ZipInputStream(tempZip.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = File(imagesDir, File(entry.name).name)
                    outFile.outputStream().use { out -> zis.copyTo(out) }
                }
                entry = zis.nextEntry
            }
        }
        tempZip.delete()
        prefs.edit { putString(KEY_IMAGE_VERSION, releaseTag) }
    }

    fun markDataVersionSkipped(tag: String) {
        prefs.edit { putString(KEY_SKIP_DATA_VERSION, tag) }
    }

    fun markImageVersionSkipped(tag: String) {
        prefs.edit { putString(KEY_SKIP_IMAGE_VERSION, tag) }
    }

    fun isFirstImageLaunch(): Boolean = prefs.getString(KEY_IMAGE_VERSION, null) == null

    fun persistImagePreference(pref: ImageDownloadPreference) {
        prefs.edit { putString("image_download_pref", pref.name) }
    }

    fun loadImagePreference(): ImageDownloadPreference {
        val saved = prefs.getString("image_download_pref", null) ?: return ImageDownloadPreference.PROMPT
        return try { ImageDownloadPreference.valueOf(saved) } catch (_: Exception) { ImageDownloadPreference.PROMPT }
    }

    fun persistAutoCheck(enabled: Boolean) {
        prefs.edit { putBoolean("auto_check_data_updates", enabled) }
    }

    fun loadAutoCheck(): Boolean = prefs.getBoolean("auto_check_data_updates", false)

    /**
     * Checks GitHub releases for a newer app version than the one currently installed.
     * Returns an [AppRelease] to display to the user if a newer tag exists, or null otherwise.
     *
     * Intentionally does NOT download or install anything — the action is opening a URL
     * in the browser (GitHub releases page or F-Droid), keeping us compliant with F-Droid's
     * inclusion policy (no self-updating APK, no REQUEST_INSTALL_PACKAGES permission).
     */
    suspend fun checkForAppUpdate(): AppRelease? = try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val currentVersion = pInfo.versionName ?: return null

        val body = client.get("https://api.github.com/repos/$APP_REPO/releases/latest").bodyAsText()
        val release = json.decodeFromString<ApiRelease>(body)

        if (isNewerVersion(release.tagName.trimStart('v'), currentVersion.trimStart('v'))) {
            AppRelease(tagName = release.tagName, htmlUrl = release.htmlUrl)
        } else null
    } catch (e: Exception) {
        Log.w(TAG, "checkForAppUpdate failed", e)
        null
    }

    /** Returns whether the app was installed via F-Droid or a direct APK download. */
    fun getInstallerSource(): InstallerSource {
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            }.getOrNull()
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
        return when {
            installer == "org.fdroid.fdroid" || installer == "org.fdroid.fdroid.privileged.ota" -> InstallerSource.FDROID
            installer == "com.android.vending" -> InstallerSource.PLAY_STORE
            else -> InstallerSource.DIRECT
        }
    }

    /**
     * Downloads the APK asset from the given release into the app's cache dir and returns the
     * file. Only called on the github build variant (CAN_SELF_UPDATE = true); F-Droid and Play
     * Store builds never reach this path.
     */
    suspend fun downloadApk(
        release: AppRelease,
        onProgress: (Float) -> Unit
    ): File {
        val body = client.get("https://api.github.com/repos/$APP_REPO/releases/tags/${release.tagName}").bodyAsText()
        val apiRelease = json.decodeFromString<ApiRelease>(body)
        val apkAsset = apiRelease.assets.firstOrNull { it.name.endsWith(".apk") && !it.name.contains("debug", ignoreCase = true) }
            ?: throw IllegalStateException("No release APK found in ${release.tagName}")

        val response = client.get(apkAsset.browserDownloadUrl)
        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
        val channel = response.bodyAsChannel()

        val apkFile = File(context.cacheDir, "lunachron-update.apk")
        var downloaded = 0L
        val readBuffer = ByteArray(DEFAULT_BUFFER_SIZE)

        apkFile.outputStream().use { out ->
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(readBuffer)
                if (read <= 0) break
                out.write(readBuffer, 0, read)
                downloaded += read
                if (contentLength > 0) onProgress(downloaded.toFloat() / contentLength)
            }
        }
        return apkFile
    }

    fun persistAutoCheckApp(enabled: Boolean) {
        prefs.edit { putBoolean("auto_check_app_updates", enabled) }
    }

    fun loadAutoCheckApp(): Boolean = prefs.getBoolean("auto_check_app_updates", false)

    companion object {
        /** Simple semver comparison: returns true if [latest] > [current]. */
        fun isNewerVersion(latest: String, current: String): Boolean {
            val l = latest.split(".").mapNotNull { it.toIntOrNull() }
            val c = current.split(".").mapNotNull { it.toIntOrNull() }
            for (i in 0 until maxOf(l.size, c.size)) {
                val lv = l.getOrElse(i) { 0 }
                val cv = c.getOrElse(i) { 0 }
                if (lv > cv) return true
                if (lv < cv) return false
            }
            return false
        }
    }
}
