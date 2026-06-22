package io.github.garemat.lunachron.api

import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import io.ktor.client.*
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.github.garemat.lunachron.BuildConfig
import io.github.garemat.lunachron.OnlineCampaignDetail
import io.github.garemat.lunachron.OnlineCampaignSettings
import io.github.garemat.lunachron.OnlineCampaignSummary
import io.github.garemat.lunachron.OnlineMachinationAttack
import io.github.garemat.lunachron.OnlineMachinationChoice
import io.github.garemat.lunachron.OnlinePlayerStat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PREFS_NAME = "lunachron_prefs"
private const val KEY_SESSION_TOKEN = "api_session_token"
private const val KEY_SESSION_EXPIRES = "api_session_expires"
private const val KEY_BACKEND_DEVICE_ID = "api_backend_device_id"
private const val KEY_ANDROID_DEVICE_ID = "persistent_device_id"

// Resolved from BuildConfig at compile time:
//   debug   → http://10.0.2.2:3000  (emulator) — change to your LAN IP for a physical device
//   release → https://api.garemat.co.uk
private val BASE_URL = BuildConfig.LUNACHRON_API_URL

/** Wraps an API call result — either a parsed success body or an error with a machine-readable code. */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: String, val message: String) : ApiResult<Nothing>()
}

// ── Request / response models ─────────────────────────────────────────────────

@Serializable
private data class RegisterRequest(val deviceId: String, val username: String)

@Serializable
private data class LoginRequest(val deviceId: String)

@Serializable
data class AuthResponse(
    val token: String,
    @SerialName("expiresAt") val expiresAt: String,
    /** Present on login responses; absent on fresh registration. */
    val username: String? = null,
    /** The backend's internal device UUID (devices.id). Used to identify this device in schedules/results. */
    val deviceId: String? = null
)

@Serializable
private data class CreateCampaignRequest(
    val name: String,
    val description: String? = null,
    val settings: OnlineCampaignSettings? = null
)

@Serializable
data class CreateCampaignResponse(val id: String, val joinCode: String)

@Serializable
private data class JoinCampaignRequest(val joinCode: String)

@Serializable
data class JoinCampaignResponse(val campaignId: String, val status: String)

@Serializable
data class UnlockCampaignResponse(val status: String, val joinCode: String)

@Serializable
data class AddLocalMemberResponse(val id: String, val deviceId: String, val username: String)

@Serializable
private data class MatchResultRequest(
    val roundNumber: Int,
    val gameNumber: Int,
    val playerStats: List<OnlinePlayerStat>,
    val winnerId: String?
)

@Serializable
private data class AdjustPointsRequest(
    val targetDeviceId: String,
    val mpDelta: Int,
    val vpDelta: Int,
    val note: String
)

@Serializable
data class AdjustPointsResponse(
    val deviceId: String,
    val vpAdjustment: Int,
    val mpAdjustment: Int,
    val powerPoints: Int
)

@Serializable
private data class ApiErrorBody(
    val error: String = "Unknown error",
    val code: String = "UNKNOWN",
    val details: List<String> = emptyList()
) {
    /** Human-readable message, appending AJV detail lines when present. */
    val message: String get() = if (details.isEmpty()) error else "$error\n${details.joinToString("\n")}"
}

// ── Client ────────────────────────────────────────────────────────────────────

/**
 * Thin wrapper around the LunaChron backend REST API.
 *
 * Responsibilities:
 *  - Serialize/deserialize request and response bodies
 *  - Persist the session token in SharedPreferences
 *  - Return [ApiResult] — callers never see raw HTTP or exceptions
 *
 * Adding new endpoints: add a method here, a request/response model above, and
 * wire the call through a new [CharacterEvent] in the ViewModel.
 */
class LunaChronApi(
    context: Context,
    /** Inject a mock client in tests; production code uses the default. */
    client: HttpClient? = null
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }
    private val http = client ?: HttpClient(Android) {
        install(HttpTimeout) {
            // ORDS runs inside ADB — no cold starts, so 15s is ample.
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis  = 15_000
        }
        install(HttpRequestRetry) {
            maxRetries = 1
            retryOnException(retryOnTimeout = true)
            delayMillis { 1_000 }
        }
    }

    /** The currently stored session token, or null if the device is not registered. */
    val savedToken: String? get() = prefs.getString(KEY_SESSION_TOKEN, null)

    /** The backend's internal device UUID (devices.id), stored after first successful auth. */
    val savedBackendDeviceId: String? get() = prefs.getString(KEY_BACKEND_DEVICE_ID, null)

    /**
     * Returns true if the stored session token expires within [thresholdDays] days,
     * or if no expiry date is stored. Callers should silently refresh via [login] when true.
     */
    fun isTokenExpiringSoon(thresholdDays: Long = 10): Boolean {
        val raw = prefs.getString(KEY_SESSION_EXPIRES, null) ?: return true
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val expiresAt = sdf.parse(raw.take(19)) ?: return true
            expiresAt.time < System.currentTimeMillis() + thresholdDays * 86_400_000L
        } catch (_: Exception) {
            true
        }
    }

    /** Attaches the saved session token as a Bearer header. */
    private fun HttpRequestBuilder.authorize() {
        val token = savedToken ?: return
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private fun saveToken(token: String, expiresAt: String, backendDeviceId: String? = null) {
        prefs.edit {
            putString(KEY_SESSION_TOKEN, token)
            putString(KEY_SESSION_EXPIRES, expiresAt)
            if (backendDeviceId != null) putString(KEY_BACKEND_DEVICE_ID, backendDeviceId)
        }
    }

    /**
     * Register this device with [username].
     *
     * If the device is already registered (DEVICE_EXISTS), automatically falls
     * through to [login] so the caller always gets a fresh token on success.
     *
     * Errors surfaced to callers:
     *  - USERNAME_TAKEN  — choose a different username
     *  - NETWORK_ERROR   — connectivity problem
     */
    suspend fun register(deviceId: String, username: String): ApiResult<AuthResponse> = try {
        val body = json.encodeToString(RegisterRequest(deviceId, username))
        val response = http.post("$BASE_URL/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        handleAuthResponse(response, onDeviceExists = { login(deviceId) })
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    }

    /**
     * Refresh the session token for an already-registered device.
     * Returns DEVICE_NOT_FOUND if the device has never been registered.
     */
    suspend fun login(deviceId: String): ApiResult<AuthResponse> = try {
        val body = json.encodeToString(LoginRequest(deviceId))
        val response = http.post("$BASE_URL/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        handleAuthResponse(response, onDeviceExists = null)
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    }

    /**
     * Transfers this account to [newDeviceId] using the stored (possibly expired)
     * session token as proof of identity. Called automatically when [login] returns
     * DEVICE_NOT_FOUND — which happens when the persistent device ID changed between
     * installs (e.g. GitHub APK → Play Store) and the old token has since expired.
     *
     * On success the backend updates the device ID hash and issues a fresh token,
     * which [handleAuthResponse] persists via [saveToken].
     */
    suspend fun adoptDevice(newDeviceId: String): ApiResult<AuthResponse> = try {
        val body = """{"newDeviceId":"$newDeviceId"}"""
        val response = http.post("$BASE_URL/auth/adopt-device") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        handleAuthResponse(response, onDeviceExists = null)
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    }

    // ── Campaign endpoints ────────────────────────────────────────────────────

    /** Create a new campaign and return its ID and 6-char join code. */
    suspend fun createCampaign(
        name: String,
        description: String?,
        settings: OnlineCampaignSettings
    ): ApiResult<CreateCampaignResponse> = withSession { try {
        val body = json.encodeToString(CreateCampaignRequest(name, description, settings))
        val response = http.post("$BASE_URL/campaigns") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.bodyAsText()
        when (response.status.value) {
            201 -> ApiResult.Success(json.decodeFromString<CreateCampaignResponse>(text))
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /** Request to join a campaign via its 6-char join code. */
    suspend fun joinCampaign(joinCode: String): ApiResult<JoinCampaignResponse> = withSession { try {
        val body = json.encodeToString(JoinCampaignRequest(joinCode))
        val response = http.post("$BASE_URL/campaigns/join") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.bodyAsText()
        when (response.status.value) {
            201 -> ApiResult.Success(json.decodeFromString<JoinCampaignResponse>(text))
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /** Returns full detail for a single campaign, including its member list. */
    suspend fun getCampaign(campaignId: String): ApiResult<OnlineCampaignDetail> = withSession { try {
        val response = http.get("$BASE_URL/campaigns/$campaignId") { authorize() }
        val text = response.bodyAsText()
        when (response.status.value) {
            200 -> ApiResult.Success(json.decodeFromString<OnlineCampaignDetail>(text))
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /** Approve a pending member (host only). */
    suspend fun approveMember(campaignId: String, memberId: String): ApiResult<Unit> = withSession { try {
        val body = """{"memberId":"$memberId"}"""
        val response = http.post("$BASE_URL/campaigns/$campaignId/approve-member") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.bodyAsText()
        when (response.status.value) {
            200 -> ApiResult.Success(Unit)
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /** Reject a pending member (host only). */
    suspend fun rejectMember(campaignId: String, memberId: String): ApiResult<Unit> = withSession { try {
        val body = """{"memberId":"$memberId"}"""
        val response = http.post("$BASE_URL/campaigns/$campaignId/reject-member") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.bodyAsText()
        when (response.status.value) {
            200 -> ApiResult.Success(Unit)
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /** Lock the campaign — auto-rejects pending members, prevents new joins. */
    suspend fun lockCampaign(campaignId: String): ApiResult<Unit> = withSession { try {
        val response = http.post("$BASE_URL/campaigns/$campaignId/lock") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        val text = response.bodyAsText()
        when (response.status.value) {
            200 -> ApiResult.Success(Unit)
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /** Re-open a locked campaign with a fresh join code. */
    suspend fun unlockCampaign(campaignId: String): ApiResult<UnlockCampaignResponse> = withSession { try {
        val response = http.post("$BASE_URL/campaigns/$campaignId/unlock") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        val text = response.bodyAsText()
        when (response.status.value) {
            200 -> ApiResult.Success(json.decodeFromString<UnlockCampaignResponse>(text))
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /** Publish a round-robin schedule for a locked campaign. */
    suspend fun publishSchedule(
        campaignId: String,
        schedule: Map<String, Map<String, List<String>>>
    ): ApiResult<Unit> = withSession { try {
        val body = buildString {
            append("""{"schedule":{""")
            schedule.entries.joinTo(this, ",") { (roundKey, games) ->
                """"$roundKey":{${games.entries.joinToString(",") { (gameKey, players) ->
                    """"$gameKey":[${players.joinToString(",") { "\"$it\"" }}]"""
                }}}"""
            }
            append("}}")
        }
        val response = http.post("$BASE_URL/campaigns/$campaignId/schedule") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.bodyAsText()
        when (response.status.value) {
            200 -> ApiResult.Success(Unit)
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /** Upload troupe data for a campaign. Pass [targetDeviceId] (host only) to upload for a local player. */
    suspend fun uploadTroupe(campaignId: String, troupeData: String, targetDeviceId: String? = null): ApiResult<Unit> = withSession { try {
        val bodyMap = buildMap<String, String> {
            put("troupeData", troupeData)
            if (targetDeviceId != null) put("targetDeviceId", targetDeviceId)
        }
        val body = json.encodeToString(bodyMap)
        val response = http.put("$BASE_URL/campaigns/$campaignId/troupe") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.bodyAsText()
        when (response.status.value) {
            200 -> ApiResult.Success(Unit)
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /** Set or clear ready state. Pass [targetDeviceId] (host only) to act on a local player. */
    suspend fun setReady(campaignId: String, isReady: Boolean, targetDeviceId: String? = null): ApiResult<Unit> = withSession { try {
        val body = if (targetDeviceId != null)
            """{"isReady":$isReady,"targetDeviceId":"$targetDeviceId"}"""
        else
            """{"isReady":$isReady}"""
        val response = http.post("$BASE_URL/campaigns/$campaignId/ready") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.bodyAsText()
        when (response.status.value) {
            200 -> ApiResult.Success(Unit)
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /** Trigger server-side ranking recalculation (host only). */
    suspend fun updateRankings(campaignId: String): ApiResult<Unit> = withSession { try {
        val response = http.post("$BASE_URL/campaigns/$campaignId/rankings") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        val text = response.bodyAsText()
        when (response.status.value) {
            200 -> ApiResult.Success(Unit)
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /** Apply a manual MP/VP delta to a member (host/Wizard Chamberlain only). */
    suspend fun adjustPlayerPoints(
        campaignId: String,
        targetDeviceId: String,
        mpDelta: Int,
        vpDelta: Int,
        note: String
    ): ApiResult<AdjustPointsResponse> = withSession { try {
        val body = json.encodeToString(AdjustPointsRequest(targetDeviceId, mpDelta, vpDelta, note))
        val response = http.post("$BASE_URL/campaigns/$campaignId/adjust-points") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.bodyAsText()
        when (response.status.value) {
            200 -> ApiResult.Success(json.decodeFromString<AdjustPointsResponse>(text))
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /** Advance to the next round (host only). */
    suspend fun advanceRound(campaignId: String): ApiResult<Unit> = withSession { try {
        val response = http.post("$BASE_URL/campaigns/$campaignId/advance-round") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        val text = response.bodyAsText()
        when (response.status.value) {
            200 -> ApiResult.Success(Unit)
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /** Delete a campaign (host only). */
    suspend fun deleteOnlineCampaign(campaignId: String): ApiResult<Unit> = withSession { try {
        val response = http.delete("$BASE_URL/campaigns/$campaignId") { authorize() }
        when (response.status.value) {
            204 -> ApiResult.Success(Unit)
            else -> {
                val text = response.bodyAsText()
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /** Submit a match result for a game in the current round. */
    suspend fun submitMatchResult(
        campaignId: String,
        roundNumber: Int,
        gameNumber: Int,
        playerStats: List<OnlinePlayerStat>,
        winnerId: String?
    ): ApiResult<Unit> = withSession { try {
        val body = json.encodeToString(MatchResultRequest(roundNumber, gameNumber, playerStats, winnerId))
        val response = http.post("$BASE_URL/campaigns/$campaignId/match-result") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.bodyAsText()
        when (response.status.value) {
            200, 201 -> ApiResult.Success(Unit)
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /** Agree or dispute a pending match result as the opponent. */
    suspend fun verifyMatchResult(campaignId: String, resultId: String, agree: Boolean): ApiResult<Unit> = withSession { try {
        val body = """{"resultId":"$resultId","agree":$agree}"""
        val response = http.post("$BASE_URL/campaigns/$campaignId/verify-result") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.bodyAsText()
        when (response.status.value) {
            200 -> ApiResult.Success(Unit)
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /** Host-only: force-verify a pending match result without opponent confirmation. */
    suspend fun overrideMatchResult(campaignId: String, resultId: String): ApiResult<Unit> = withSession { try {
        val body = """{"resultId":"$resultId"}"""
        val response = http.post("$BASE_URL/campaigns/$campaignId/override-result") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.bodyAsText()
        when (response.status.value) {
            200 -> ApiResult.Success(Unit)
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /** Add a named local (unattended) player to a campaign (host only). */
    suspend fun addLocalMember(campaignId: String, name: String): ApiResult<AddLocalMemberResponse> = withSession { try {
        val body = """{"name":${json.encodeToString(name)}}"""
        val response = http.post("$BASE_URL/campaigns/$campaignId/add-local-member") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.bodyAsText()
        when (response.status.value) {
            200, 201 -> ApiResult.Success(json.decodeFromString<AddLocalMemberResponse>(text))
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /**
     * Confirm this player's troupe snapshot for [roundNumber] before their match.
     * Once both players in the scheduled game confirm, each can see the other's troupe
     * via [OnlineCampaignDetail.roundTroupes] returned by [getCampaign].
     * Pass [targetDeviceId] (host only) to confirm on behalf of a local player.
     */
    suspend fun confirmRoundTroupe(
        campaignId: String,
        roundNumber: Int,
        troupeData: String,
        targetDeviceId: String? = null
    ): ApiResult<Unit> = withSession { try {
        val body = buildString {
            append("""{"roundNumber":$roundNumber,"troupeData":""")
            append(json.encodeToString(troupeData))
            if (targetDeviceId != null) { append(""","targetDeviceId":"""); append(json.encodeToString(targetDeviceId)) }
            append("}")
        }
        val response = http.post("$BASE_URL/campaigns/$campaignId/confirm-round-troupe") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.bodyAsText()
        when (response.status.value) {
            200 -> ApiResult.Success(Unit)
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /** Submit SUPPORT/SABOTAGE machination choices (and optional attack) for the machinations phase. */
    suspend fun submitMachination(
        campaignId: String,
        machinations: List<OnlineMachinationChoice>,
        attack: OnlineMachinationAttack? = null,
        targetDeviceId: String? = null
    ): ApiResult<Unit> = withSession { try {
        val machinationsJson = json.encodeToString(machinations)
        val attackJson = if (attack != null) json.encodeToString(attack) else null
        val body = buildString {
            append("""{"machinations":$machinationsJson""")
            if (attackJson != null) append(""","attack":$attackJson""")
            if (targetDeviceId != null) append(""","targetDeviceId":"$targetDeviceId"""")
            append("}")
        }
        val response = http.post("$BASE_URL/campaigns/$campaignId/submit-machination") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.bodyAsText()
        when (response.status.value) {
            200, 201 -> ApiResult.Success(Unit)
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    /** Returns all campaigns the device is an approved member of. */
    suspend fun getMyCampaigns(): ApiResult<List<OnlineCampaignSummary>> = withSession { try {
        val response = http.get("$BASE_URL/campaigns/mine") { authorize() }
        val text = response.bodyAsText()
        when (response.status.value) {
            200 -> ApiResult.Success(json.decodeFromString<List<OnlineCampaignSummary>>(text))
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
    } }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Runs [block] and, if the response carries AUTH_EXPIRED, silently re-logs in and
     * retries once. Covers the case where the startup renewal failed (e.g. no network at
     * launch) and the token has since fully expired mid-session.
     */
    private suspend fun <T> withSession(block: suspend () -> ApiResult<T>): ApiResult<T> {
        val result = block()
        if (result is ApiResult.Error && result.code == "AUTH_EXPIRED") {
            val deviceId = prefs.getString(KEY_ANDROID_DEVICE_ID, null) ?: return result
            val loginResult = login(deviceId)
            if (loginResult is ApiResult.Success) return block()
            if (loginResult is ApiResult.Error && loginResult.code == "DEVICE_NOT_FOUND") {
                if (adoptDevice(deviceId) is ApiResult.Success) return block()
            }
            return result
        }
        return result
    }

    private suspend fun handleAuthResponse(
        response: HttpResponse,
        onDeviceExists: (suspend () -> ApiResult<AuthResponse>)?
    ): ApiResult<AuthResponse> {
        val text = response.bodyAsText()
        return when (response.status.value) {
            200, 201 -> {
                val result = json.decodeFromString<AuthResponse>(text)
                saveToken(result.token, result.expiresAt, result.deviceId)
                ApiResult.Success(result)
            }
            409 -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }
                    .getOrDefault(ApiErrorBody())
                if (err.code == "DEVICE_EXISTS" && onDeviceExists != null) {
                    onDeviceExists()
                } else {
                    ApiResult.Error(err.code, err.message)
                }
            }
            else -> {
                val err = runCatching { json.decodeFromString<ApiErrorBody>(text) }
                    .getOrDefault(ApiErrorBody())
                ApiResult.Error(err.code, err.message)
            }
        }
    }
}
