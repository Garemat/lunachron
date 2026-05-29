package io.github.garemat.lunachron

import android.app.Application
import android.content.Context
import android.util.Log
import android.util.Base64
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.garemat.lunachron.api.ApiResult
import io.github.garemat.lunachron.api.LunaChronApi
import io.github.garemat.lunachron.ui.CampaignSubScreen
import io.github.garemat.lunachron.ui.theme.ThemeRepository
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import androidx.core.content.edit
import java.util.UUID

class CharacterViewModel(
    application: Application,
    private val repository: CharacterRepository,
    private val dataUpdateRepository: DataUpdateRepository,
    newsClient: HttpClient? = null
) : AndroidViewModel(application) {

    private val prefs = run {
        // One-time migration from legacy "moonstone_prefs" key to "lunachron_prefs".
        val oldPrefs = application.getSharedPreferences("moonstone_prefs", Context.MODE_PRIVATE)
        val newPrefs = application.getSharedPreferences("lunachron_prefs", Context.MODE_PRIVATE)
        if (oldPrefs.all.isNotEmpty() && newPrefs.all.isEmpty()) {
            @Suppress("UseKtx") // Multi-op loop — KTX edit{} block can't accumulate inside forEach
            val editor = newPrefs.edit()
            @Suppress("UNCHECKED_CAST")
            oldPrefs.all.forEach { (key, value) ->
                when (value) {
                    is String  -> editor.putString(key, value)
                    is Int     -> editor.putInt(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Float   -> editor.putFloat(key, value)
                    is Long    -> editor.putLong(key, value)
                }
            }
            editor.apply()
            oldPrefs.edit { clear() }
        }
        newPrefs
    }
    private val themeRepository = ThemeRepository(application)
    private val nearbyManager = NearbyManager(application)
    private val apiClient = LunaChronApi(application)
    private val client = newsClient ?: HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 15000
        }
    }

    private val persistentDeviceId: String = prefs.getString("persistent_device_id", null) ?: run {
        val newId = UUID.randomUUID().toString()
        prefs.edit { putString("persistent_device_id", newId) }
        newId
    }

    private val _state = MutableStateFlow(CharacterState(
        name = prefs.getString("player_name", "") ?: "",
        deviceId = persistentDeviceId,
        activeThemeId = run {
            val raw = prefs.getString("app_theme", "moonstone") ?: "moonstone"
            // Migrate legacy ALL_CAPS enum names stored by older app versions.
            when (raw.uppercase()) { "MOONSTONE" -> "moonstone"; "DEFAULT" -> "default"; else -> raw }
        },
        layoutDensity = LayoutDensity.valueOf(prefs.getString("layout_density", LayoutDensity.COZY.name) ?: LayoutDensity.COZY.name),
        useLocalModeByDefault = prefs.getBoolean("use_local_mode_by_default", false),
        useSinglePlayerMode = prefs.getBoolean("use_single_player_mode", false),
        hasSeenGlobalTutorial = prefs.getBoolean("has_seen_global_tutorial", false),
        gameTrackingMode = GameTrackingMode.valueOf(prefs.getString("game_tracking_mode", GameTrackingMode.LOW_DETAIL.name) ?: GameTrackingMode.LOW_DETAIL.name),
        enableAnimations = prefs.getBoolean("enable_animations", true),
        defaultStartPage = prefs.getString("default_start_page", "home") ?: "home",
        cardDisplayMode = CardDisplayMode.valueOf(prefs.getString("card_display_mode", CardDisplayMode.AUTO.name) ?: CardDisplayMode.AUTO.name),
        skipCompendiumLanding = prefs.getBoolean("skip_compendium_landing", false),
        hideCampaignTab = prefs.getBoolean("hide_campaign_tab", false),
        newsItems = loadCachedNews(),
        autoFetchNews = prefs.getBoolean("auto_fetch_news", false),
        autoCheckDataUpdates = dataUpdateRepository.loadAutoCheck(),
        installedDataVersion = CharacterData.getInstalledVersion(application),
        imageDownloadPreference = dataUpdateRepository.loadImagePreference(),
        autoCheckAppUpdates = dataUpdateRepository.loadAutoCheckApp(),
        installerSource = dataUpdateRepository.getInstallerSource(),
        isRegistered = prefs.getString("api_session_token", null) != null,
        backendDeviceId = prefs.getString("api_backend_device_id", "") ?: ""
    ))
    
    private val _characters = repository.getCharacters()
    private val _troupes = repository.getTroupes()
    private val _upgrades = repository.getUpgradeCards()
    private val _campaignCards = repository.getCampaignCards()
    private val _campaigns = repository.getCampaigns()
    val gameResults = repository.getGameResults().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _rules = MutableStateFlow<List<RuleSection>>(emptyList())
    val rules = _rules.asStateFlow()

    val state = combine(
        _state, 
        _characters, 
        _troupes, 
        _upgrades, 
        _campaignCards
    ) { state, characters, troupes, upgrades, campaignCards ->
        state.copy(
            characters = characters,
            troupes = troupes,
            upgradeCards = upgrades,
            campaignCards = campaignCards
        )
    }.combine(_campaigns) { state, campaigns ->
        state.copy(campaigns = campaigns)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)

    val discoveredEndpoints = nearbyManager.discoveredEndpoints

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private val _scannedTroupeEvent = MutableSharedFlow<Pair<Int, Troupe>>()
    val scannedTroupeEvent = _scannedTroupeEvent.asSharedFlow()

    sealed class UiEvent {
        data object GameStarted : UiEvent()
        data class TroupeCreated(val troupe: Troupe, val playerIndex: Int?, val campaignPlayerId: String? = null) : UiEvent()
        data object TournamentJoined : UiEvent()
        data object TournamentDisbanded : UiEvent()
    }

    init {
        loadRules()
        if (_state.value.autoFetchNews) fetchNews()
        checkForUpdatesOnStartup()
        nearbyManager.setPayloadListener { endpointId, message -> handleSessionMessage(endpointId, message) }
        // Backfill backendDeviceId for users who registered before it was added to the auth response
        if (_state.value.isRegistered && _state.value.backendDeviceId.isEmpty()) {
            viewModelScope.launch {
                val result = apiClient.login(persistentDeviceId)
                if (result is ApiResult.Success && result.data.deviceId != null) {
                    _state.update { it.copy(backendDeviceId = result.data.deviceId) }
                }
            }
        }
    }

    private fun checkForUpdatesOnStartup() {
        viewModelScope.launch(Dispatchers.IO) {
            // Data update check
            if (_state.value.autoCheckDataUpdates) {
                dataUpdateRepository.checkForDataUpdate()
                    ?.let { release -> _state.update { it.copy(pendingDataUpdate = release) } }
            }

            // App update check (opt-in, off by default; suppressed on Play Store installs)
            if (_state.value.autoCheckAppUpdates && _state.value.installerSource != InstallerSource.PLAY_STORE) {
                dataUpdateRepository.checkForAppUpdate()
                    ?.let { release -> _state.update { it.copy(pendingAppUpdate = release) } }
            }

            // Image update check
            when (_state.value.imageDownloadPreference) {
                ImageDownloadPreference.PROMPT -> {
                    if (dataUpdateRepository.isFirstImageLaunch()) {
                        _state.update { it.copy(pendingImageUpdate = "FIRST_LAUNCH") }
                    }
                }
                ImageDownloadPreference.ENABLED -> {
                    dataUpdateRepository.checkForImageUpdate()
                        ?.let { tag -> _state.update { it.copy(pendingImageUpdate = tag) } }
                }
                ImageDownloadPreference.DISABLED -> { /* no-op */ }
            }
        }
    }

    private fun loadRules() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _rules.value = CharacterData.readCompendium(getApplication()).rules
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun loadCachedNews(): List<NewsItem> {
        val cached = prefs.getString("cached_news", null) ?: return emptyList()
        return try { Json.decodeFromString(cached) } catch (e: Exception) { emptyList() }
    }

    private fun fetchNews() {
        viewModelScope.launch {
            _state.update { it.copy(isFetchingNews = true) }
            try {
                val baseUrl = "https://www.moonstonethegame.com"
                val latestUrl = "$baseUrl/latest"
                val response = withContext(Dispatchers.IO) { client.get(latestUrl).bodyAsText() }
                val doc = Jsoup.parse(response)
                val articleElements = doc.select("article, .summary-item, .blog-item")
                if (articleElements.isNotEmpty()) {
                    val newItems = articleElements.mapNotNull { element ->
                        val aTag = element.select("a[href*='/latest/']").firstOrNull() ?: element.select("a").firstOrNull() ?: return@mapNotNull null
                        val urlRel = aTag.attr("href")
                        if (urlRel.contains("/category/") || urlRel.endsWith("/latest") || urlRel.endsWith("/latest/") || urlRel.contains("?category=")) return@mapNotNull null
                        val url = if (urlRel.startsWith("http")) urlRel else baseUrl + urlRel
                        val title = element.select("h1, h2, h3, .summary-title, .blog-title, .blog-item-title").firstOrNull()?.text()?.trim() ?: aTag.text().trim()
                        if (title.isEmpty()) return@mapNotNull null
                        val date = element.select("time, .summary-metadata-item--date, .blog-date, .blog-meta-item--date").firstOrNull()?.text()?.trim() ?: "Recently"
                        val summary = element.select(".summary-excerpt, .blog-excerpt, .blog-item-excerpt").firstOrNull()?.text()?.trim() ?: element.select("p").firstOrNull()?.text()?.trim() ?: ""
                        val img = element.select("img").firstOrNull()
                        var imageUrl = img?.let { it.attr("data-src").ifEmpty { it.attr("src").ifEmpty { it.attr("data-image") } } } ?: ""
                        if (imageUrl.isNotEmpty()) {
                            if (!imageUrl.startsWith("http")) imageUrl = baseUrl + if (imageUrl.startsWith("/")) "" else "/" + imageUrl
                            if (!imageUrl.contains("format=")) imageUrl += if (imageUrl.contains("?")) "&format=1000w" else "?format=1000w"
                        }
                        NewsItem(title, url, date, imageUrl.ifEmpty { null }, summary.ifEmpty { null })
                    }.distinctBy { it.url }.take(10)
                    if (newItems.isNotEmpty()) {
                        val currentItems = _state.value.newsItems
                        if (currentItems.isEmpty() || currentItems[0].url != newItems[0].url) {
                            _state.update { it.copy(newsItems = newItems) }
                            prefs.edit { putString("cached_news", Json.encodeToString(newItems)) }
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() } finally { _state.update { it.copy(isFetchingNews = false) } }
        }
    }

    var editingTroupeId by mutableStateOf<Int?>(null)
    var newTroupeName by mutableStateOf("")
    var selectedTroupeFaction by mutableStateOf(Faction.COMMONWEALTH)
    var selectedCharacterIds by mutableStateOf(setOf<Int>())
    var isTournamentList by mutableStateOf(false)
    var isCampaignTroupe by mutableStateOf(false)
    var pendingTroupePlayerIndex by mutableStateOf<Int?>(null)
    var pendingCampaignPlayerId by mutableStateOf<String?>(null)
    var showTroupeTypeSheet by mutableStateOf(false)
    var troupeDashboardActive by mutableStateOf(false)
    // TODO(dual-layout): Reserved for future dual-layout update — currently unused.
    // var troupeLayoutSingleColumn by mutableStateOf(true)

    @OptIn(ExperimentalCoroutinesApi::class)
    val playersWithCharacters = state.flatMapLatest { currentState ->
        val troupes = currentState.activeTroupes
        if (troupes.isEmpty()) return@flatMapLatest flowOf(emptyList<Pair<Troupe, List<Character>>>())
        val flows = troupes.map { troupe -> repository.getCharactersByIds(troupe.characterIds).map { troupe to it } }
        combine(flows) { troupePairs ->
            troupePairs.toList().map { (troupe, characters) ->
                val summonIds = characters.flatMap { it.summonsCharacterIds }
                if (summonIds.isNotEmpty()) {
                    val allCharacters = currentState.characters
                    val currentIds = characters.map { it.id }.toSet()
                    val summoned = summonIds.filter { !currentIds.contains(it) }.mapNotNull { sId -> allCharacters.find { it.id == sId } }
                    troupe to (characters + summoned)
                } else troupe to characters
            }
        }
    }.onEach { players ->
        if (players.isNotEmpty() && _state.value.characterPlayStates.isEmpty()) {
            val initial = mutableMapOf<String, CharacterPlayState>()
            val initialSummons = mutableMapOf<Int, List<SummonEntry>>()
            players.forEachIndexed { pIdx, (troupe, characters) ->
                val baseIds = troupe.characterIds.toSet()
                val summonEntries = mutableListOf<SummonEntry>()
                characters.forEachIndexed { cIdx, character ->
                    initial["${pIdx}_${cIdx}"] = CharacterPlayState(character.health, calculateReplenishedEnergy(character, character.health))
                    if (character.id !in baseIds) {
                        val summonedBy = characters.find { it.id in baseIds && character.id in it.summonsCharacterIds }
                        summonEntries.add(SummonEntry(character.id, summonedBy?.id))
                    }
                }
                if (summonEntries.isNotEmpty()) initialSummons[pIdx] = summonEntries
            }
            _state.update { it.copy(characterPlayStates = initial, activeSummons = initialSummons, currentTurn = it.currentTurn, turnHistory = it.turnHistory) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onEvent(event: CharacterEvent) {
        when (event) {
            is CharacterEvent.DeleteTroupe -> viewModelScope.launch { repository.deleteTroupe(event.troupe) }
            is CharacterEvent.ToggleTroupeFavourite -> viewModelScope.launch {
                val troupe = state.value.troupes.find { it.id == event.troupeId } ?: return@launch
                repository.upsertTroupe(troupe.copy(isFavourite = !troupe.isFavourite))
            }
            is CharacterEvent.EditTroupe -> {
                editingTroupeId = event.troupe.id; newTroupeName = event.troupe.troupeName
                selectedTroupeFaction = event.troupe.faction; selectedCharacterIds = event.troupe.characterIds.toSet()
                isTournamentList = event.troupe.isTournamentList
                isCampaignTroupe = event.troupe.isCampaignTroupe
            }
            CharacterEvent.SaveTroupe -> performSaveTroupe()
            is CharacterEvent.SaveTroupeWithMetadata -> performSaveTroupe(event.victoryPoints, event.equippedUpgrades, event.campaignCards)
            is CharacterEvent.SortCharacters -> _state.update { it.copy(sortType = event.sortType) }
            CharacterEvent.DismissError -> _state.update { it.copy(errorMessage = null) }
            is CharacterEvent.UpdateUserName -> {
                val old = _state.value.name; _state.update { it.copy(name = event.name) }
                prefs.edit { putString("player_name", event.name) }
                if (event.name != old) nearbyManager.sendPayloadToAll(MessageParser.encode(SessionMessage.PlayerInfoUpdate(persistentDeviceId, event.name)))
            }
            is CharacterEvent.RegisterDevice -> viewModelScope.launch {
                _state.update { it.copy(isRegisteringDevice = true, registrationError = null) }
                when (val result = apiClient.register(persistentDeviceId, event.username)) {
                    is ApiResult.Success -> {
                        // Persist username locally so it survives app restarts without re-hitting the API.
                        prefs.edit { putString("player_name", event.username) }
                        _state.update {
                            it.copy(
                                isRegisteringDevice = false,
                                isRegistered = true,
                                name = event.username,
                                backendDeviceId = result.data.deviceId ?: it.backendDeviceId
                            )
                        }
                    }
                    is ApiResult.Error -> _state.update { it.copy(isRegisteringDevice = false, registrationError = result.message) }
                }
            }
            CharacterEvent.DismissRegistrationError -> _state.update { it.copy(registrationError = null) }
            CharacterEvent.LoadOnlineCampaigns -> viewModelScope.launch {
                _state.update { it.copy(isLoadingOnlineCampaigns = true, onlineCampaignError = null) }
                when (val r = apiClient.getMyCampaigns()) {
                    is ApiResult.Success -> _state.update { it.copy(isLoadingOnlineCampaigns = false, onlineCampaigns = r.data) }
                    is ApiResult.Error   -> _state.update { it.copy(isLoadingOnlineCampaigns = false, onlineCampaignError = r.message) }
                }
            }
            is CharacterEvent.CreateOnlineCampaign -> viewModelScope.launch {
                _state.update { it.copy(isCreatingCampaign = true, onlineCampaignError = null) }
                when (val r = apiClient.createCampaign(event.name, event.description, event.settings)) {
                    is ApiResult.Success -> _state.update { it.copy(isCreatingCampaign = false, createdCampaignResult = CreatedCampaignResult(r.data.id, r.data.joinCode)) }
                    is ApiResult.Error   -> _state.update { it.copy(isCreatingCampaign = false, onlineCampaignError = r.message) }
                }
            }
            is CharacterEvent.RequestJoinCampaign -> viewModelScope.launch {
                _state.update { it.copy(isJoiningCampaign = true, onlineCampaignError = null) }
                when (val r = apiClient.joinCampaign(event.joinCode)) {
                    is ApiResult.Success -> _state.update { it.copy(isJoiningCampaign = false, pendingJoinCampaignId = r.data.campaignId) }
                    is ApiResult.Error   -> _state.update { it.copy(isJoiningCampaign = false, onlineCampaignError = r.message) }
                }
            }
            CharacterEvent.DismissPendingJoin           -> _state.update { it.copy(pendingJoinCampaignId = null) }
            CharacterEvent.DismissOnlineCampaignError   -> _state.update { it.copy(onlineCampaignError = null) }
            CharacterEvent.DismissCreatedCampaignResult -> _state.update { it.copy(createdCampaignResult = null) }
            is CharacterEvent.LoadOnlineCampaign -> viewModelScope.launch {
                _state.update { it.copy(isLoadingCampaignDetail = true, onlineCampaignError = null) }
                when (val r = apiClient.getCampaign(event.campaignId)) {
                    is ApiResult.Success -> {
                        // Derive pending count from member list so the hub badge stays current
                        // without any extra API calls or backend changes.
                        val pendingCount = r.data.members.count { it.status == "PENDING" }
                        val updatedSummaries = _state.value.onlineCampaigns.map { summary ->
                            if (summary.id == event.campaignId) summary.copy(pendingCount = pendingCount) else summary
                        }
                        _state.update { it.copy(
                            isLoadingCampaignDetail = false,
                            selectedOnlineCampaign = r.data,
                            onlineCampaigns = updatedSummaries
                        ) }
                    }
                    is ApiResult.Error -> _state.update { it.copy(isLoadingCampaignDetail = false, onlineCampaignError = r.message) }
                }
            }
            is CharacterEvent.ApproveMember -> viewModelScope.launch {
                _state.update { it.copy(isApprovingMember = true, onlineCampaignError = null) }
                when (val r = apiClient.approveMember(event.campaignId, event.memberId)) {
                    is ApiResult.Success -> {
                        _state.update { it.copy(isApprovingMember = false) }
                        onEvent(CharacterEvent.LoadOnlineCampaign(event.campaignId))
                    }
                    is ApiResult.Error -> _state.update { it.copy(isApprovingMember = false, onlineCampaignError = r.message) }
                }
            }
            is CharacterEvent.RejectMember -> viewModelScope.launch {
                _state.update { it.copy(isApprovingMember = true, onlineCampaignError = null) }
                when (val r = apiClient.rejectMember(event.campaignId, event.memberId)) {
                    is ApiResult.Success -> {
                        _state.update { it.copy(isApprovingMember = false) }
                        onEvent(CharacterEvent.LoadOnlineCampaign(event.campaignId))
                    }
                    is ApiResult.Error -> _state.update { it.copy(isApprovingMember = false, onlineCampaignError = r.message) }
                }
            }
            CharacterEvent.ClearSelectedOnlineCampaign -> _state.update { it.copy(selectedOnlineCampaign = null, pendingOnlineSchedule = null, onlineScheduleRoundCount = 0, showCampaignAdminPanel = false, showCampaignDeleteDialog = false) }
            is CharacterEvent.DeleteOnlineCampaign -> viewModelScope.launch {
                _state.update { it.copy(isDeletingCampaign = true, onlineCampaignError = null) }
                when (val r = apiClient.deleteOnlineCampaign(event.campaignId)) {
                    is ApiResult.Success -> _state.update { it.copy(isDeletingCampaign = false, onlineCampaignDeleted = true, selectedOnlineCampaign = null) }
                    is ApiResult.Error   -> _state.update { it.copy(isDeletingCampaign = false, onlineCampaignError = r.message) }
                }
            }
            CharacterEvent.DismissCampaignDeleted -> _state.update { it.copy(onlineCampaignDeleted = false) }
            CharacterEvent.ShowCampaignAdminPanel   -> _state.update { it.copy(showCampaignAdminPanel = true) }
            CharacterEvent.HideCampaignAdminPanel   -> _state.update { it.copy(showCampaignAdminPanel = false) }
            CharacterEvent.ShowCampaignDeleteDialog -> _state.update { it.copy(showCampaignDeleteDialog = true) }
            CharacterEvent.HideCampaignDeleteDialog -> _state.update { it.copy(showCampaignDeleteDialog = false) }
            is CharacterEvent.LockOnlineCampaign -> viewModelScope.launch {
                _state.update { it.copy(isLockingCampaign = true, onlineCampaignError = null) }
                apiClient.lockCampaign(event.campaignId)
                // Always refresh after lock attempt — the backend may have applied the lock even
                // if it returned an error code (race between OCI cold start and client timeout).
                _state.update { it.copy(isLockingCampaign = false) }
                onEvent(CharacterEvent.LoadOnlineCampaign(event.campaignId))
            }
            is CharacterEvent.UnlockOnlineCampaign -> viewModelScope.launch {
                _state.update { it.copy(isLockingCampaign = true, onlineCampaignError = null) }
                apiClient.unlockCampaign(event.campaignId)
                _state.update { it.copy(isLockingCampaign = false, pendingOnlineSchedule = null) }
                onEvent(CharacterEvent.LoadOnlineCampaign(event.campaignId))
            }
            is CharacterEvent.SetOnlineScheduleRoundCount -> _state.update { it.copy(onlineScheduleRoundCount = event.count) }
            is CharacterEvent.GenerateOnlineSchedule -> {
                val members = _state.value.selectedOnlineCampaign
                    ?.members?.filter { it.status == "APPROVED" } ?: return
                val playerIds = members.map { it.deviceId }
                val schedule = generateRoundRobinSchedule(playerIds, event.totalRounds)
                _state.update { it.copy(pendingOnlineSchedule = schedule, pendingScheduleError = null) }
            }
            is CharacterEvent.SwapScheduleBye -> {
                val schedule = _state.value.pendingOnlineSchedule ?: return
                val playerCount = _state.value.selectedOnlineCampaign
                    ?.members?.count { it.status == "APPROVED" } ?: return
                when (val result = swapScheduleBye(schedule, playerCount, event.roundNumber, event.newByePlayerId)) {
                    is ScheduleSwapResult.Success -> _state.update { it.copy(pendingOnlineSchedule = result.schedule, pendingScheduleError = null) }
                    is ScheduleSwapResult.Error -> _state.update { it.copy(pendingScheduleError = result.message) }
                }
            }
            is CharacterEvent.UploadOnlineTroupe -> viewModelScope.launch {
                _state.update { it.copy(isUploadingTroupe = true, onlineCampaignError = null) }
                when (val r = apiClient.uploadTroupe(event.campaignId, event.troupeData)) {
                    is ApiResult.Success -> {
                        _state.update { it.copy(isUploadingTroupe = false) }
                        onEvent(CharacterEvent.LoadOnlineCampaign(event.campaignId))
                    }
                    is ApiResult.Error -> _state.update { it.copy(isUploadingTroupe = false, onlineCampaignError = r.message) }
                }
            }
            is CharacterEvent.SetOnlineCampaignReady -> viewModelScope.launch {
                _state.update { it.copy(isSettingReady = true, onlineCampaignError = null) }
                apiClient.setReady(event.campaignId, event.isReady)
                // Always refresh — backend may have applied the change even on error response.
                _state.update { it.copy(isSettingReady = false) }
                onEvent(CharacterEvent.LoadOnlineCampaign(event.campaignId))
            }
            is CharacterEvent.AddLocalCampaignMember -> viewModelScope.launch {
                _state.update { it.copy(isAddingLocalMember = true, onlineCampaignError = null) }
                when (val r = apiClient.addLocalMember(event.campaignId, event.name)) {
                    is ApiResult.Success -> {
                        _state.update { it.copy(isAddingLocalMember = false) }
                        onEvent(CharacterEvent.LoadOnlineCampaign(event.campaignId))
                    }
                    is ApiResult.Error -> _state.update { it.copy(isAddingLocalMember = false, onlineCampaignError = r.message) }
                }
            }
            is CharacterEvent.UploadLocalMemberTroupe -> viewModelScope.launch {
                _state.update { it.copy(isUploadingTroupe = true, onlineCampaignError = null) }
                when (val r = apiClient.uploadTroupe(event.campaignId, event.troupeData, event.targetDeviceId)) {
                    is ApiResult.Success -> {
                        _state.update { it.copy(isUploadingTroupe = false) }
                        onEvent(CharacterEvent.LoadOnlineCampaign(event.campaignId))
                    }
                    is ApiResult.Error -> _state.update { it.copy(isUploadingTroupe = false, onlineCampaignError = r.message) }
                }
            }
            is CharacterEvent.SetLocalMemberReady -> viewModelScope.launch {
                _state.update { it.copy(isSettingReady = true, onlineCampaignError = null) }
                when (val r = apiClient.setReady(event.campaignId, event.isReady, event.targetDeviceId)) {
                    is ApiResult.Success -> {
                        _state.update { it.copy(isSettingReady = false) }
                        onEvent(CharacterEvent.LoadOnlineCampaign(event.campaignId))
                    }
                    is ApiResult.Error -> _state.update { it.copy(isSettingReady = false, onlineCampaignError = r.message) }
                }
            }
            is CharacterEvent.UpdateOnlineRankings -> viewModelScope.launch {
                _state.update { it.copy(isUpdatingRankings = true, onlineCampaignError = null) }
                when (val r = apiClient.updateRankings(event.campaignId)) {
                    is ApiResult.Success -> {
                        _state.update { it.copy(isUpdatingRankings = false) }
                        onEvent(CharacterEvent.LoadOnlineCampaign(event.campaignId))
                    }
                    is ApiResult.Error -> _state.update { it.copy(isUpdatingRankings = false, onlineCampaignError = r.message) }
                }
            }
            is CharacterEvent.AdvanceOnlineRound -> viewModelScope.launch {
                _state.update { it.copy(isAdvancingRound = true, onlineCampaignError = null) }
                when (val r = apiClient.advanceRound(event.campaignId)) {
                    is ApiResult.Success -> {
                        _state.update { it.copy(isAdvancingRound = false) }
                        onEvent(CharacterEvent.LoadOnlineCampaign(event.campaignId))
                    }
                    is ApiResult.Error -> _state.update { it.copy(isAdvancingRound = false, onlineCampaignError = r.message) }
                }
            }
            is CharacterEvent.SubmitOnlineMatchResult -> viewModelScope.launch {
                _state.update { it.copy(isSubmittingMatchResult = true, onlineCampaignError = null) }
                when (val r = apiClient.submitMatchResult(event.campaignId, event.roundNumber, event.gameNumber, event.playerStats, event.winnerId)) {
                    is ApiResult.Success -> {
                        _state.update { it.copy(isSubmittingMatchResult = false, matchResultSubmitted = true) }
                        onEvent(CharacterEvent.LoadOnlineCampaign(event.campaignId))
                    }
                    is ApiResult.Error -> {
                        // Both players submitted simultaneously — reload so the opponent sees the pending verification UI
                        if (r.code == "ALREADY_SUBMITTED") {
                            _state.update { it.copy(isSubmittingMatchResult = false) }
                            onEvent(CharacterEvent.LoadOnlineCampaign(event.campaignId))
                        } else {
                            _state.update { it.copy(isSubmittingMatchResult = false, onlineCampaignError = r.message) }
                        }
                    }
                }
            }
            CharacterEvent.DismissMatchResultSubmitted -> _state.update { it.copy(matchResultSubmitted = false) }
            is CharacterEvent.SubmitMachination -> viewModelScope.launch {
                _state.update { it.copy(isSubmittingMachination = true, onlineCampaignError = null) }
                when (val r = apiClient.submitMachination(event.campaignId, event.machinations, event.attack)) {
                    is ApiResult.Success -> {
                        _state.update { it.copy(isSubmittingMachination = false) }
                        onEvent(CharacterEvent.LoadOnlineCampaign(event.campaignId))
                    }
                    is ApiResult.Error -> _state.update { it.copy(isSubmittingMachination = false, onlineCampaignError = r.message) }
                }
            }
            is CharacterEvent.SubmitLocalMachination -> viewModelScope.launch {
                _state.update { it.copy(isSubmittingMachination = true, onlineCampaignError = null) }
                when (val r = apiClient.submitMachination(event.campaignId, event.machinations, event.attack, event.targetDeviceId)) {
                    is ApiResult.Success -> {
                        _state.update { it.copy(isSubmittingMachination = false) }
                        onEvent(CharacterEvent.LoadOnlineCampaign(event.campaignId))
                    }
                    is ApiResult.Error -> _state.update { it.copy(isSubmittingMachination = false, onlineCampaignError = r.message) }
                }
            }
            is CharacterEvent.VerifyMatchResult -> viewModelScope.launch {
                _state.update { it.copy(isVerifyingMatchResult = true, onlineCampaignError = null) }
                when (val r = apiClient.verifyMatchResult(event.campaignId, event.resultId, event.agree)) {
                    is ApiResult.Success -> {
                        _state.update { it.copy(isVerifyingMatchResult = false) }
                        onEvent(CharacterEvent.LoadOnlineCampaign(event.campaignId))
                    }
                    is ApiResult.Error -> _state.update { it.copy(isVerifyingMatchResult = false, onlineCampaignError = r.message) }
                }
            }
            is CharacterEvent.ConfirmRoundTroupe -> viewModelScope.launch {
                _state.update { it.copy(isConfirmingRoundTroupe = true, onlineCampaignError = null) }
                when (val r = apiClient.confirmRoundTroupe(event.campaignId, event.roundNumber, event.troupeData, event.targetDeviceId)) {
                    is ApiResult.Success -> {
                        _state.update { it.copy(isConfirmingRoundTroupe = false) }
                        onEvent(CharacterEvent.LoadOnlineCampaign(event.campaignId))
                    }
                    is ApiResult.Error -> _state.update { it.copy(isConfirmingRoundTroupe = false, onlineCampaignError = r.message) }
                }
            }
            is CharacterEvent.PublishOnlineSchedule -> viewModelScope.launch {
                val rounds = _state.value.pendingOnlineSchedule ?: return@launch
                val playerCount = _state.value.selectedOnlineCampaign
                    ?.members?.count { it.status == "APPROVED" } ?: return@launch
                val validationError = validateSchedule(rounds, playerCount)
                if (validationError != null) {
                    _state.update { it.copy(pendingScheduleError = validationError) }
                    return@launch
                }
                _state.update { it.copy(isPublishingSchedule = true, onlineCampaignError = null) }
                // Convert List<CampaignRound> → Map<String, Map<String, List<String>>>
                val backendSchedule = rounds.associate { round ->
                    "round${round.roundNumber}" to round.games.mapIndexed { idx, game ->
                        "game${idx + 1}" to game.playerIds
                    }.toMap()
                }
                when (val r = apiClient.publishSchedule(event.campaignId, backendSchedule)) {
                    is ApiResult.Success -> {
                        _state.update { it.copy(isPublishingSchedule = false, pendingOnlineSchedule = null) }
                        onEvent(CharacterEvent.LoadOnlineCampaign(event.campaignId))
                    }
                    is ApiResult.Error -> _state.update { it.copy(isPublishingSchedule = false, onlineCampaignError = r.message) }
                }
            }
            is CharacterEvent.SetActiveTheme -> {
                _state.update { it.copy(activeThemeId = event.themeId) }
                prefs.edit { putString("app_theme", event.themeId) }
                // Clear override flag so theme preferences apply fresh on this switch.
                prefs.edit { putBoolean("tracking_mode_overridden", false) }
                val gp = runCatching { themeRepository.resolve(event.themeId).gameplayPreferences }.getOrNull()
                gp?.defaultTrackingMode?.let { name ->
                    runCatching { GameTrackingMode.valueOf(name) }.getOrNull()?.let { mode ->
                        _state.update { it.copy(gameTrackingMode = mode) }
                        prefs.edit { putString("game_tracking_mode", mode.name) }
                    }
                }
            }
            is CharacterEvent.ChangeLayoutDensity -> {
                _state.update { it.copy(layoutDensity = event.density) }
                prefs.edit { putString("layout_density", event.density.name) }
            }
            is CharacterEvent.SetLocalModeDefault -> {
                _state.update { it.copy(useLocalModeByDefault = event.useLocal) }
                prefs.edit { putBoolean("use_local_mode_by_default", event.useLocal) }
            }
            is CharacterEvent.SetSinglePlayerMode -> {
                _state.update { it.copy(useSinglePlayerMode = event.enabled) }
                prefs.edit { putBoolean("use_single_player_mode", event.enabled) }
            }
            is CharacterEvent.SetHasSeenTutorial -> {
                if (event.tutorialKey == "global") _state.update { it.copy(hasSeenGlobalTutorial = event.seen) }
                prefs.edit { putBoolean(if (event.tutorialKey == "global") "has_seen_global_tutorial" else "has_seen_${event.tutorialKey}_tutorial", event.seen) }
            }
            CharacterEvent.StartTutorial -> {
                _state.update { it.copy(isTutorialActive = true, currentTutorialStep = 0) }
            }
            CharacterEvent.AdvanceTutorial -> {
                val next = _state.value.currentTutorialStep + 1
                if (next >= io.github.garemat.lunachron.ui.appTutorialSteps.size) {
                    _state.update { it.copy(isTutorialActive = false, hasSeenGlobalTutorial = true) }
                    prefs.edit { putBoolean("has_seen_global_tutorial", true) }
                } else {
                    _state.update { it.copy(currentTutorialStep = next) }
                }
            }
            CharacterEvent.SkipTutorial -> {
                _state.update { it.copy(isTutorialActive = false, hasSeenGlobalTutorial = true) }
                prefs.edit { putBoolean("has_seen_global_tutorial", true) }
            }
            CharacterEvent.RefreshNews -> fetchNews()
            is CharacterEvent.SetAutoFetchNews -> {
                _state.update { it.copy(autoFetchNews = event.enabled) }
                prefs.edit { putBoolean("auto_fetch_news", event.enabled) }
            }
            is CharacterEvent.UpdateCharacterHealth -> {
                updateCharacterState(event.playerIndex, event.charIndex) { it.copy(currentHealth = event.health) }
                broadcastGameplayUpdate(SessionMessage.GameplayUpdate(event.playerIndex, event.charIndex, health = event.health))
                if (event.health == 0) {
                    val cur = _state.value
                    val baseSize = cur.activeTroupes.getOrNull(event.playerIndex)?.characterIds?.size ?: 0
                    val summons = cur.activeSummons[event.playerIndex] ?: emptyList()
                    val summonIndex = event.charIndex - baseSize
                    if (summonIndex >= 0 && summonIndex < summons.size) {
                        onEvent(CharacterEvent.RemoveSummonedCharacter(event.playerIndex, summons[summonIndex].characterId))
                    }
                }
            }
            is CharacterEvent.UpdateCharacterEnergy -> {
                updateCharacterState(event.playerIndex, event.charIndex) { it.copy(currentEnergy = event.energy) }
                broadcastGameplayUpdate(SessionMessage.GameplayUpdate(event.playerIndex, event.charIndex, energy = event.energy))
            }
            is CharacterEvent.ToggleAbilityUsed -> {
                updateCharacterState(event.playerIndex, event.charIndex) { 
                    val new = it.usedAbilities.toMutableMap(); new[event.abilityName] = event.used; it.copy(usedAbilities = new)
                }
                broadcastGameplayUpdate(SessionMessage.GameplayUpdate(event.playerIndex, event.charIndex, abilityName = event.abilityName, abilityUsed = event.used))
            }
            is CharacterEvent.ToggleCharacterFlipped -> updateCharacterState(event.playerIndex, event.charIndex) { it.copy(isFlipped = event.flipped) }
            is CharacterEvent.ToggleCharacterExpanded -> updateCharacterState(event.playerIndex, event.charIndex) { it.copy(isExpanded = event.expanded) }
            CharacterEvent.ResetGamePlayState -> _state.update { it.copy(characterPlayStates = emptyMap(), currentTurn = 1, turnHistory = emptyList(), winnerName = null, isTie = false, activeSummons = emptyMap(), poolResourceCounts = emptyMap()) }
            is CharacterEvent.ChangeGameTrackingMode -> {
                _state.update { it.copy(gameTrackingMode = event.mode) }
                prefs.edit {
                    putString("game_tracking_mode", event.mode.name)
                    putBoolean("tracking_mode_overridden", true)
                }
            }
            is CharacterEvent.SetEnableAnimations -> {
                _state.update { it.copy(enableAnimations = event.enabled) }
                prefs.edit { putBoolean("enable_animations", event.enabled) }
            }
            is CharacterEvent.SetDefaultStartPage -> {
                _state.update { it.copy(defaultStartPage = event.route) }
                prefs.edit { putString("default_start_page", event.route) }
            }
            is CharacterEvent.SetCardDisplayMode -> {
                _state.update { it.copy(cardDisplayMode = event.mode) }
                prefs.edit { putString("card_display_mode", event.mode.name) }
            }
            is CharacterEvent.SetSkipCompendiumLanding -> {
                _state.update { it.copy(skipCompendiumLanding = event.skip) }
                prefs.edit { putBoolean("skip_compendium_landing", event.skip) }
            }
            is CharacterEvent.SetHideCampaignTab -> {
                _state.update { it.copy(hideCampaignTab = event.hide) }
                prefs.edit { putBoolean("hide_campaign_tab", event.hide) }
            }
            is CharacterEvent.AddSummonedCharacter -> {
                _state.update { cur ->
                    val summons = cur.activeSummons.toMutableMap()
                    val list = (summons[event.playerIndex] ?: emptyList()).toMutableList()
                    if (list.none { it.characterId == event.characterId }) {
                        list.add(SummonEntry(event.characterId, event.summonedByCharacterId))
                        summons[event.playerIndex] = list
                        val baseSize = cur.activeTroupes.getOrNull(event.playerIndex)?.characterIds?.size ?: 0
                        val charIndex = baseSize + list.size - 1
                        val allChars = cur.characters
                        val char = allChars.find { it.id == event.characterId }
                        val newPlayStates = cur.characterPlayStates.toMutableMap()
                        if (char != null) newPlayStates["${event.playerIndex}_$charIndex"] = CharacterPlayState(char.health, calculateReplenishedEnergy(char, char.health))
                        cur.copy(activeSummons = summons, characterPlayStates = newPlayStates)
                    } else cur
                }
            }
            is CharacterEvent.RemoveSummonedCharacter -> {
                _state.update { cur ->
                    val summons = cur.activeSummons.toMutableMap()
                    val list = (summons[event.playerIndex] ?: emptyList()).toMutableList()
                    val idx = list.indexOfFirst { it.characterId == event.characterId }
                    if (idx >= 0) {
                        list.removeAt(idx)
                        summons[event.playerIndex] = list
                        val baseSize = cur.activeTroupes.getOrNull(event.playerIndex)?.characterIds?.size ?: 0
                        val removedCharIndex = baseSize + idx
                        val newPlayStates = cur.characterPlayStates.toMutableMap()
                        newPlayStates.remove("${event.playerIndex}_$removedCharIndex")
                        // Re-index subsequent summon states
                        for (i in idx until list.size) {
                            val oldKey = "${event.playerIndex}_${baseSize + i + 1}"
                            val newKey = "${event.playerIndex}_${baseSize + i}"
                            newPlayStates[newKey] = newPlayStates.remove(oldKey) ?: continue
                        }
                        cur.copy(activeSummons = summons, characterPlayStates = newPlayStates)
                    } else cur
                }
            }
            is CharacterEvent.TransformCharacter -> {
                _state.update { cur ->
                    val troupe = cur.activeTroupes.getOrNull(event.playerIndex) ?: return@update cur
                    val newIds = troupe.characterIds.toMutableList()
                    if (event.charIndex !in newIds.indices) return@update cur
                    newIds[event.charIndex] = event.targetCharacterId
                    val newTroupes = cur.activeTroupes.toMutableList()
                    newTroupes[event.playerIndex] = troupe.copy(characterIds = newIds)
                    val targetChar = cur.characters.find { it.id == event.targetCharacterId }
                    val newPlayStates = cur.characterPlayStates.toMutableMap()
                    if (targetChar != null) {
                        val key = "${event.playerIndex}_${event.charIndex}"
                        newPlayStates[key] = CharacterPlayState(targetChar.health, calculateReplenishedEnergy(targetChar, targetChar.health))
                    }
                    cur.copy(activeTroupes = newTroupes, characterPlayStates = newPlayStates)
                }
            }
            is CharacterEvent.UpdatePoolResource -> {
                _state.update { cur ->
                    val pools = cur.poolResourceCounts.toMutableMap()
                    val playerPool = (pools[event.playerIndex] ?: emptyMap()).toMutableMap()
                    playerPool[event.resourceName] = event.count
                    pools[event.playerIndex] = playerPool
                    cur.copy(poolResourceCounts = pools)
                }
            }
            is CharacterEvent.UpdateCharacterPoolResource -> {
                updateCharacterState(event.playerIndex, event.charIndex) { ps ->
                    val held = ps.heldPoolResources.toMutableMap()
                    held[event.resourceName] = event.count
                    ps.copy(heldPoolResources = held)
                }
            }
            CharacterEvent.NextTurn -> handleReadyAction(GameAction.NEXT_TURN)
            CharacterEvent.RewindTurn -> handleReadyAction(GameAction.REWIND)
            is CharacterEvent.UpdateCharacterMoonstones -> {
                updateCharacterState(event.playerIndex, event.charIndex) { it.copy(moonstones = event.stones) }
                broadcastGameplayUpdate(SessionMessage.GameplayUpdate(event.playerIndex, event.charIndex, moonstones = event.stones))
            }
            is CharacterEvent.ToggleActivated -> {
                updateCharacterState(event.playerIndex, event.charIndex) { it.copy(isActivatedThisTurn = !it.isActivatedThisTurn) }
            }
            is CharacterEvent.SetStatusToken -> {
                updateCharacterState(event.playerIndex, event.charIndex) { it.copy(statusTokens = it.statusTokens + (event.token to event.value)) }
            }
            CharacterEvent.AbandonGame -> {
                _state.update { it.copy(activeTroupes = emptyList(), characterPlayStates = emptyMap(), currentTurn = 1, gameSession = null, turnHistory = emptyList(), winnerName = null, isTie = false) }
                nearbyManager.stopAll()
            }
            CharacterEvent.EndGame -> handleReadyAction(GameAction.NEXT_TURN, forceEnd = true)
            is CharacterEvent.CreateTournament -> startHostingTournament(event.tournamentName, event.troupeSize, event.timer, event.hostParticipating, event.passcode, event.hostMode)

            // App update events
            is CharacterEvent.SetAutoCheckAppUpdates -> {
                _state.update { it.copy(autoCheckAppUpdates = event.enabled) }
                dataUpdateRepository.persistAutoCheckApp(event.enabled)
            }
            CharacterEvent.CheckForAppUpdate -> {
                if (_state.value.installerSource != InstallerSource.PLAY_STORE) {
                    viewModelScope.launch(Dispatchers.IO) {
                        dataUpdateRepository.checkForAppUpdate()
                            ?.let { release -> _state.update { it.copy(pendingAppUpdate = release) } }
                    }
                }
            }
            CharacterEvent.DismissAppUpdate -> _state.update { it.copy(pendingAppUpdate = null) }
            CharacterEvent.InstallAppUpdate -> {
                if (!BuildConfig.CAN_SELF_UPDATE) return
                val release = _state.value.pendingAppUpdate ?: return
                viewModelScope.launch(Dispatchers.IO) {
                    _state.update { it.copy(isDownloadingApk = true, apkDownloadProgress = 0f) }
                    try {
                        val file = dataUpdateRepository.downloadApk(release) { progress ->
                            _state.update { it.copy(apkDownloadProgress = progress) }
                        }
                        _state.update { it.copy(isDownloadingApk = false, pendingApkInstall = file.absolutePath) }
                    } catch (e: Exception) {
                        Log.e("CharacterViewModel", "APK download failed", e)
                        _state.update { it.copy(isDownloadingApk = false, apkDownloadProgress = 0f) }
                    }
                }
            }
            CharacterEvent.ClearPendingApkInstall -> _state.update { it.copy(pendingApkInstall = null) }

            // Sync toggle — collapses news, data, and portrait auto-update into one setting
            is CharacterEvent.SetAutoSynchronise -> {
                val enabled = event.enabled
                val pref = if (enabled) ImageDownloadPreference.ENABLED else ImageDownloadPreference.DISABLED
                _state.update { it.copy(autoFetchNews = enabled, autoCheckDataUpdates = enabled, imageDownloadPreference = pref) }
                prefs.edit { putBoolean("auto_fetch_news", enabled) }
                dataUpdateRepository.persistAutoCheck(enabled)
                dataUpdateRepository.persistImagePreference(pref)
                if (enabled) fetchNews()
            }

            // Data update events
            is CharacterEvent.SetAutoCheckDataUpdates -> {
                _state.update { it.copy(autoCheckDataUpdates = event.enabled) }
                dataUpdateRepository.persistAutoCheck(event.enabled)
            }
            CharacterEvent.CheckForDataUpdate -> {
                viewModelScope.launch(Dispatchers.IO) {
                    dataUpdateRepository.checkForDataUpdate()
                        ?.let { release -> _state.update { it.copy(pendingDataUpdate = release) } }
                }
            }
            is CharacterEvent.SkipDataVersion -> {
                dataUpdateRepository.markDataVersionSkipped(event.tag)
                _state.update { it.copy(pendingDataUpdate = null) }
            }
            CharacterEvent.DismissDataUpdate -> _state.update { it.copy(pendingDataUpdate = null) }
            is CharacterEvent.InstallDataUpdate -> {
                viewModelScope.launch(Dispatchers.IO) {
                    _state.update { it.copy(isInstallingDataUpdate = true) }
                    try {
                        dataUpdateRepository.applyDataUpdate(event.release)
                    } catch (e: Exception) {
                        Log.e("CharacterViewModel", "Data update failed", e)
                        _state.update { it.copy(errorMessage = "Data update failed: ${e.message}") }
                    } finally {
                        _state.update { it.copy(isInstallingDataUpdate = false, pendingDataUpdate = null) }
                    }
                }
            }

            // Image download events
            is CharacterEvent.SetImageDownloadPreference -> {
                _state.update { it.copy(imageDownloadPreference = event.pref) }
                dataUpdateRepository.persistImagePreference(event.pref)
            }
            CharacterEvent.DownloadCharacterImages -> {
                viewModelScope.launch(Dispatchers.IO) {
                    _state.update { it.copy(isDownloadingImages = true, imageDownloadedBytes = 0L, imageTotalBytes = -1L, imageDownloadSpeedBps = 0L) }
                    try {
                        val tag = _state.value.pendingImageUpdate?.takeIf { it != "FIRST_LAUNCH" }
                            ?: dataUpdateRepository.checkForImageUpdate()
                            ?: run {
                                // fallback: use latest release tag
                                dataUpdateRepository.checkForDataUpdate()?.tagName
                            }
                        if (tag != null) {
                            dataUpdateRepository.downloadImages(tag) { downloaded, total, speedBps ->
                                _state.update { s ->
                                    s.copy(
                                        imageDownloadedBytes = downloaded,
                                        imageTotalBytes = total,
                                        imageDownloadSpeedBps = if (speedBps >= 0L) speedBps else s.imageDownloadSpeedBps
                                    )
                                }
                            }
                        }
                        _state.update { it.copy(pendingImageUpdate = null) }
                    } catch (e: Exception) {
                        _state.update { it.copy(errorMessage = "Image download failed: ${e.message}") }
                    } finally {
                        _state.update { it.copy(isDownloadingImages = false, imageDownloadedBytes = 0L, imageTotalBytes = -1L, imageDownloadSpeedBps = 0L) }
                    }
                }
            }
            is CharacterEvent.SkipImageVersion -> {
                dataUpdateRepository.markImageVersionSkipped(event.tag)
                _state.update { it.copy(pendingImageUpdate = null) }
            }
            CharacterEvent.DismissImageUpdate -> _state.update { it.copy(pendingImageUpdate = null) }

            CharacterEvent.GenerateMigrationExport -> viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val payload = MigrationPayload(
                        username = _state.value.name,
                        troupes = state.value.troupes,
                        campaigns = state.value.campaigns,
                        gameResults = gameResults.value,
                        sessionToken = prefs.getString("api_session_token", null),
                        backendDeviceId = prefs.getString("api_backend_device_id", null),
                        expiresAt = System.currentTimeMillis() + 15 * 60 * 1000L
                    )
                    DataMigration.encode(payload)
                }.onSuccess { code ->
                    _state.update { it.copy(migrationExportCode = code) }
                }.onFailure { e ->
                    _state.update { it.copy(errorMessage = "Export failed: ${e.message}") }
                }
            }

            is CharacterEvent.ImportMigrationData -> viewModelScope.launch(Dispatchers.IO) {
                _state.update { it.copy(migrationImportStatus = MigrationImportStatus.Loading) }
                runCatching {
                    val payload = DataMigration.decode(event.code)
                    val idMap = mutableMapOf<Int, Int>()
                    payload.troupes.forEach { troupe ->
                        val newId = repository.upsertTroupe(troupe.copy(id = 0)).toInt()
                        idMap[troupe.id] = newId
                    }
                    payload.campaigns.forEach { campaign ->
                        val remappedPlayers = campaign.players.map { player ->
                            player.copy(troupeId = idMap[player.troupeId] ?: player.troupeId)
                        }
                        repository.upsertCampaign(campaign.copy(id = 0, players = remappedPlayers))
                    }
                    payload.gameResults.forEach { result ->
                        repository.upsertGameResult(result.copy(id = 0))
                    }
                    if (payload.username.isNotBlank()) {
                        prefs.edit { putString("player_name", payload.username) }
                        _state.update { it.copy(name = payload.username) }
                    }
                    if (event.transferRegistration && !payload.sessionToken.isNullOrBlank()) {
                        prefs.edit {
                            putString("api_session_token", payload.sessionToken)
                            putString("api_backend_device_id", payload.backendDeviceId)
                        }
                        _state.update { it.copy(isRegistered = true, backendDeviceId = payload.backendDeviceId ?: "") }
                    }
                }.onSuccess {
                    _state.update { it.copy(migrationImportStatus = MigrationImportStatus.Success) }
                }.onFailure { e ->
                    _state.update { it.copy(migrationImportStatus = MigrationImportStatus.Error(e.message ?: "Invalid code")) }
                }
            }

            CharacterEvent.ClearMigrationState -> _state.update {
                it.copy(migrationExportCode = null, migrationImportStatus = null)
            }

            else -> {}
        }
    }

    private fun performSaveTroupe(
        victoryPoints: Int = 0,
        equippedUpgrades: Map<Int, List<Int>> = emptyMap(),
        campaignCards: List<TroupeCampaignCard> = emptyList()
    ) {
        val troupe = Troupe(
            id = editingTroupeId ?: 0,
            troupeName = newTroupeName,
            faction = selectedTroupeFaction,
            characterIds = selectedCharacterIds.toList(),
            shareCode = "",
            isTournamentList = isTournamentList,
            isCampaignTroupe = isCampaignTroupe,
            victoryPoints = victoryPoints,
            equippedUpgrades = equippedUpgrades,
            campaignCards = campaignCards
        )
        viewModelScope.launch {
            val id = repository.upsertTroupe(troupe)
            val savedTroupe = troupe.copy(id = id.toInt())
            if (pendingCampaignPlayerId != null) {
                broadcastTroupeSelectionForCampaign(savedTroupe, pendingCampaignPlayerId!!)
            }
            _uiEvent.emit(UiEvent.TroupeCreated(savedTroupe, pendingTroupePlayerIndex, pendingCampaignPlayerId))
            pendingTroupePlayerIndex = null
            pendingCampaignPlayerId = null
        }
        resetNewTroupeFields()
    }

    private fun updateCharacterState(playerIndex: Int, charIndex: Int, update: (CharacterPlayState) -> CharacterPlayState) {
        val key = "${playerIndex}_$charIndex"
        _state.update { currentState ->
            val playStates = currentState.characterPlayStates.toMutableMap()
            playStates[key] = update(playStates[key] ?: CharacterPlayState(0))
            currentState.copy(characterPlayStates = playStates)
        }
    }

    private fun calculateReplenishedEnergy(character: Character, currentHealth: Int): Int {
        if (currentHealth <= 0) return 0
        return character.energyTrack.count { currentHealth >= it }
    }

    private fun handleReadyAction(action: GameAction, forceEnd: Boolean = false) {
        if (_state.value.gameSession == null) {
            if (action == GameAction.NEXT_TURN) attemptNextTurn(forceEnd) else handleRewindTurn()
            return
        }
        val isReady = when(action) {
            GameAction.NEXT_TURN -> !_state.value.readyForNextTurn.contains(persistentDeviceId)
            GameAction.REWIND -> !_state.value.readyForRewind.contains(persistentDeviceId)
        }
        val readyMsg = SessionMessage.ReadyForAction(action, persistentDeviceId, isReady)
        nearbyManager.sendPayloadToAll(MessageParser.encode(readyMsg))
        handleSessionMessage("LOCAL", MessageParser.encode(readyMsg))
    }

    private fun attemptNextTurn(forceEnd: Boolean = false) {
        val cur = _state.value; val pData = playersWithCharacters.value
        if (pData.isEmpty()) return
        if (cur.currentTurn >= 4 || forceEnd) {
            val scores = pData.mapIndexed { pIdx, (t, chars) -> t.troupeName to chars.indices.sumOf { cIdx -> cur.characterPlayStates["${pIdx}_${cIdx}"]?.moonstones ?: 0 } }
            val max = scores.maxOf { it.second }
            val winners = scores.mapIndexedNotNull { i, s -> if (s.second == max) i else null }
            if (winners.size == 1 || forceEnd) {
                if (winners.size == 1) { _state.update { it.copy(winnerName = scores[winners[0]].first) }; saveGameResult(winners[0]) }
                else { _state.update { it.copy(isTie = true) }; saveGameResult(null) }
                broadcastTurnUpdate(_state.value.currentTurn, _state.value.characterPlayStates); return
            } else if (cur.currentTurn == 5) { _state.update { it.copy(isTie = true) }; saveGameResult(null); broadcastTurnUpdate(_state.value.currentTurn, _state.value.characterPlayStates); return }
        }
        handleNextTurn()
    }

    private fun saveGameResult(winnerIndex: Int?) {
        val cur = _state.value; val pData = playersWithCharacters.value
        if (pData.isEmpty()) return
        viewModelScope.launch {
            val playerStats = pData.mapIndexed { pIdx, (troupe, characters) ->
                val charStats = characters.mapIndexed { cIdx, char -> CharacterGameStat(char.id, char.name, cur.characterPlayStates["${pIdx}_${cIdx}"]?.moonstones ?: 0, (cur.characterPlayStates["${pIdx}_${cIdx}"]?.currentHealth ?: 0) <= 0) }
                PlayerStat(if (cur.gameSession != null) cur.gameSession.players.getOrNull(pIdx)?.name else (if (pIdx == 0) cur.name.ifEmpty { null } else "Player ${pIdx + 1}"), troupe.troupeName, troupe.faction, charStats.sumOf { it.stones }, charStats)
            }
            repository.upsertGameResult(GameResult(timestamp = System.currentTimeMillis(), playerStats = playerStats, winnerIndex = winnerIndex))
        }
    }

    private fun handleNextTurn() {
        val cur = _state.value; val pData = playersWithCharacters.value
        if (pData.isEmpty()) return
        val newStates = cur.characterPlayStates.toMutableMap()
        pData.forEachIndexed { pIdx, (_, characters) ->
            characters.forEachIndexed { cIdx, character ->
                val key = "${pIdx}_$cIdx"; val ps = newStates[key]
                if (ps != null && ps.currentHealth > 0) newStates[key] = ps.copy(currentEnergy = calculateReplenishedEnergy(character, ps.currentHealth), usedAbilities = emptyMap(), isActivatedThisTurn = false)
            }
        }
        _state.update { it.copy(characterPlayStates = newStates, currentTurn = it.currentTurn + 1, turnHistory = it.turnHistory + listOf(cur.characterPlayStates), readyForNextTurn = emptySet(), readyForRewind = emptySet()) }
        broadcastTurnUpdate(_state.value.currentTurn, newStates)
    }

    private fun handleRewindTurn() {
        _state.update { cur ->
            if (cur.turnHistory.isEmpty()) return@update cur
            cur.copy(characterPlayStates = cur.turnHistory.last(), currentTurn = (cur.currentTurn - 1).coerceAtLeast(1), turnHistory = cur.turnHistory.dropLast(1), readyForNextTurn = emptySet(), readyForRewind = emptySet(), winnerName = null, isTie = false)
        }
        broadcastTurnUpdate(_state.value.currentTurn, _state.value.characterPlayStates)
    }

    fun startNewGame(troupes: List<Troupe>) {
        _state.update { it.copy(characterPlayStates = emptyMap(), activeSummons = emptyMap(), currentTurn = 1, activeTroupes = troupes, turnHistory = emptyList(), readyForNextTurn = emptySet(), readyForRewind = emptySet(), winnerName = null, isTie = false) }
    }

    fun saveTroupe(troupe: Troupe) {
        viewModelScope.launch { 
            // If the troupe already has a valid database ID, use it to update instead of inserting a new one
            val id = repository.upsertTroupe(troupe)
            val savedTroupe = troupe.copy(id = id.toInt())
            if (pendingCampaignPlayerId != null) {
                broadcastTroupeSelectionForCampaign(savedTroupe, pendingCampaignPlayerId!!)
            }
            _uiEvent.emit(UiEvent.TroupeCreated(savedTroupe, pendingTroupePlayerIndex, pendingCampaignPlayerId))
            pendingTroupePlayerIndex = null
            pendingCampaignPlayerId = null
        }
    }

    fun onTroupeScanned(playerIndex: Int, troupe: Troupe) {
        viewModelScope.launch { _scannedTroupeEvent.emit(playerIndex to troupe) }
    }

    fun resetNewTroupeFields(isTournament: Boolean = false, isCampaign: Boolean = false) {
        editingTroupeId = null; newTroupeName = ""; selectedTroupeFaction = Faction.COMMONWEALTH; selectedCharacterIds = emptySet(); isTournamentList = isTournament; isCampaignTroupe = isCampaign
    }

    fun generateFullShareCode(troupe: Troupe, characters: List<Character>, upgrades: List<UpgradeCard>): String {
        val factionCode = when (troupe.faction) { Faction.COMMONWEALTH -> "A"; Faction.DOMINION -> "B"; Faction.LESHAVULT -> "C"; Faction.SHADES -> "D" }
        val items = buildString {
            for (charId in troupe.characterIds) {
                val char = characters.find { it.id == charId } ?: continue
                append(char.shareCode)
                for (upgradeId in troupe.equippedUpgrades[charId].orEmpty()) {
                    val upgrade = upgrades.find { it.id == upgradeId } ?: continue
                    append(upgrade.shareCode)
                }
            }
        }
        val rawCode = "${troupe.troupeName}|$factionCode${if (troupe.isTournamentList) "1" else "0"}$items"
        return Base64.encodeToString(rawCode.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun importTroupe(fullCode: String, allCharacters: List<Character>, allUpgrades: List<UpgradeCard>): Troupe? {
        try {
            val decoded = String(Base64.decode(fullCode, Base64.DEFAULT), Charsets.UTF_8)
            val parts = decoded.split("|"); if (parts.size != 2 || parts[1].isEmpty()) return null
            val codeBody = parts[1]
            val faction = when (codeBody[0]) { 'A' -> Faction.COMMONWEALTH; 'B' -> Faction.DOMINION; 'C' -> Faction.LESHAVULT; 'D' -> Faction.SHADES; else -> return null }
            val isTournament = codeBody[1] == '1'
            val characterIds = mutableListOf<Int>()
            val equippedUpgrades = mutableMapOf<Int, MutableList<Int>>()
            var currentCharId: Int? = null
            for (code in codeBody.substring(2).chunked(5)) {
                if (code.length != 5) continue
                when (code[0]) {
                    'A' -> { // character
                        val char = allCharacters.find { it.shareCode == code } ?: continue
                        characterIds.add(char.id); currentCharId = char.id
                    }
                    'B' -> { // upgrade card
                        val upgrade = allUpgrades.find { it.shareCode == code } ?: continue
                        currentCharId?.let { equippedUpgrades.getOrPut(it) { mutableListOf() }.add(upgrade.id) }
                    }
                }
            }
            return Troupe(0, parts[0], faction, characterIds, fullCode, isTournament, equippedUpgrades = equippedUpgrades)
        } catch (e: Exception) { return null }
    }

    fun startHosting(hostName: String, mode: HostMode = HostMode.WIFI_NSD) {
        nearbyManager.stopAll(); val actual = _state.value.name.ifEmpty { hostName }
        _state.update { it.copy(gameSession = GameSession(listOf(GamePlayer(actual, deviceId = persistentDeviceId)), true, UUID.randomUUID().toString().take(8))) }
        nearbyManager.startAdvertising(actual, mode)
    }

    fun startHostingTournament(name: String, size: TroupeSizeSetting, timer: Int, participating: Boolean, passcode: String, mode: HostMode = HostMode.WIFI_NSD) {
        nearbyManager.stopAll(); val actual = _state.value.name.ifEmpty { "Host" }
        _state.update { it.copy(isTournamentHost = true, tournamentSettings = TournamentSettings(name, size, timer, participating, UUID.randomUUID().toString().take(8), passcode), tournamentPlayers = if (participating) listOf(TournamentPlayer(actual, persistentDeviceId)) else emptyList()) }
        nearbyManager.startAdvertising(name, mode)
    }

    fun startDiscovering() { nearbyManager.startDiscovery() }

    private var joiningTournamentEndpointId: String? = null

    fun requestTournamentJoin(endpointId: String, passcode: String) {
        joiningTournamentEndpointId = endpointId
        nearbyManager.requestConnection(_state.value.name.ifEmpty { "Player" }, endpointId)
        nearbyManager.setConnectionListener { if (it == endpointId) nearbyManager.sendPayload(endpointId, MessageParser.encode(SessionMessage.JoinRequest(_state.value.name.ifEmpty { "Player" }, persistentDeviceId, passcode))) }
    }

    private fun handleSessionMessage(endpointId: String, jsonString: String) {
        val msg = try { MessageParser.decode(jsonString) } catch (e: Exception) { return }
        if (msg is SessionMessage.SessionSync) { _state.update { it.copy(gameSession = GameSession(msg.players, false, msg.sessionId)) }; return }
        if (msg is SessionMessage.TournamentSync) {
            if (_state.value.isLeaving) return
            val trigger = joiningTournamentEndpointId == endpointId
            _state.update { it.copy(tournamentSettings = msg.settings, tournamentPlayers = msg.players, isTournamentHost = false, currentTournamentRound = msg.currentRound, tournamentHistory = msg.history) }
            if (trigger) { joiningTournamentEndpointId = null; viewModelScope.launch { _uiEvent.emit(UiEvent.TournamentJoined) } }; return
        }
        val cur = _state.value; val isHost = cur.isTournamentHost
        when (msg) {
            is SessionMessage.JoinRequest -> {
                if (cur.gameSession?.isHost == true) {
                    if (cur.gameSession.players.none { it.deviceId == msg.deviceId } && cur.gameSession.players.size < 4) {
                        _state.update { it.copy(gameSession = it.gameSession!!.copy(players = it.gameSession.players + GamePlayer(msg.playerName, deviceId = msg.deviceId))) }
                        syncSessionToAll()
                    } else if (cur.gameSession.players.any { it.deviceId == msg.deviceId }) syncSessionToAll()
                } else if (isHost && cur.tournamentSettings?.passcode == msg.tournamentPasscode) {
                    _state.update { it.copy(tournamentPlayers = it.tournamentPlayers + TournamentPlayer(msg.playerName, msg.deviceId)) }; syncTournamentToAll()
                }
            }
            is SessionMessage.PlayerInfoUpdate -> {
                if (isHost) { _state.update { s -> s.copy(tournamentPlayers = s.tournamentPlayers.map { if (it.deviceId == msg.deviceId) it.copy(name = msg.newName) else it }) }; syncTournamentToAll() }
                else if (cur.gameSession?.isHost == true) { _state.update { s -> s.copy(gameSession = s.gameSession!!.copy(players = s.gameSession.players.map { if (it.deviceId == msg.deviceId) it.copy(name = msg.newName) else it })) }; syncSessionToAll() }
            }
            is SessionMessage.LeaveMessage -> if (isHost) { _state.update { s -> s.copy(tournamentPlayers = s.tournamentPlayers.filter { it.deviceId != msg.deviceId }) }; syncTournamentToAll() }
            is SessionMessage.TroupeSelected -> {
                if (isHost) { _state.update { s -> s.copy(tournamentPlayers = s.tournamentPlayers.map { if (it.deviceId == msg.deviceId) it.copy(troupe = Troupe(0, msg.troupeName, msg.faction, msg.characterIds, ""), isReady = false) else it }) }; syncTournamentToAll() }
                else if (cur.gameSession?.isHost == true) { _state.update { s -> s.copy(gameSession = s.gameSession!!.copy(players = s.gameSession.players.map { if (it.deviceId == msg.deviceId) it.copy(troupe = Troupe(0, msg.troupeName, msg.faction, msg.characterIds, "")) else it })) }; syncSessionToAll() }
            }
            is SessionMessage.TournamentPlayerReady -> if (isHost) { _state.update { s -> s.copy(tournamentPlayers = s.tournamentPlayers.map { if (it.deviceId == msg.deviceId) it.copy(isReady = msg.isReady) else it }) }; syncTournamentToAll() }
            is SessionMessage.TournamentDisbanded -> viewModelScope.launch { Toast.makeText(getApplication(), msg.message, Toast.LENGTH_LONG).show(); _uiEvent.emit(UiEvent.TournamentDisbanded); leaveSession() }
            is SessionMessage.TournamentPairingUpdate -> _state.update { s -> s.copy(currentTournamentRound = s.currentTournamentRound?.copy(pairings = s.currentTournamentRound.pairings.map { if (it.player1Id == msg.pairing.player1Id && it.player2Id == msg.pairing.player2Id) msg.pairing else it })) }
            is SessionMessage.StartGame -> { val ts = cur.gameSession?.players?.mapNotNull { it.troupe } ?: emptyList(); if (ts.isNotEmpty()) { startNewGame(ts); viewModelScope.launch { _uiEvent.emit(UiEvent.GameStarted) } } }
            else -> {}
        }
    }

    private fun syncSessionToAll() { val s = _state.value.gameSession ?: return; if (s.isHost) nearbyManager.sendPayloadToAll(MessageParser.encode(SessionMessage.SessionSync(s.players, s.sessionId))) }
    private fun syncTournamentToAll() { val st = _state.value.tournamentSettings ?: return; nearbyManager.sendPayloadToAll(MessageParser.encode(SessionMessage.TournamentSync(st, _state.value.tournamentPlayers, _state.value.currentTournamentRound, _state.value.tournamentHistory))) }

    fun broadcastTroupeSelection(troupe: Troupe, targetDeviceId: String? = null) {
        val cur = _state.value; val id = targetDeviceId ?: persistentDeviceId
        val json = MessageParser.encode(SessionMessage.TroupeSelected(id, troupe.troupeName, troupe.faction, troupe.characterIds))
        if (cur.tournamentSettings != null) { _state.update { s -> s.copy(tournamentPlayers = s.tournamentPlayers.map { if (it.deviceId == id) it.copy(troupe = troupe, isReady = id.startsWith("manual_")) else it }) }; if (cur.isTournamentHost) syncTournamentToAll() else nearbyManager.sendPayloadToAll(json) }
        else if (cur.gameSession != null) { _state.update { s -> s.copy(gameSession = s.gameSession!!.copy(players = s.gameSession.players.map { if (it.deviceId == persistentDeviceId) it.copy(troupe = troupe) else it })) }; if (cur.gameSession.isHost) syncSessionToAll() else nearbyManager.sendPayloadToAll(json) }
    }

    fun toggleTournamentReady(isReady: Boolean) {
        if (_state.value.tournamentSettings == null) return
        _state.update { s -> s.copy(tournamentPlayers = s.tournamentPlayers.map { if (it.deviceId == persistentDeviceId) it.copy(isReady = isReady) else it }) }
        val json = MessageParser.encode(SessionMessage.TournamentPlayerReady(persistentDeviceId, isReady))
        if (_state.value.isTournamentHost) syncTournamentToAll() else nearbyManager.sendPayloadToAll(json)
    }

    fun updateTournamentSettings(name: String, size: TroupeSizeSetting, timer: Int, participating: Boolean) {
        val cur = _state.value; val s = cur.tournamentSettings ?: return
        val updated = if (participating && cur.tournamentPlayers.none { it.deviceId == persistentDeviceId }) cur.tournamentPlayers + TournamentPlayer(cur.name.ifEmpty { "Host" }, persistentDeviceId) else if (!participating) cur.tournamentPlayers.filter { it.deviceId != persistentDeviceId } else cur.tournamentPlayers
        _state.update { it.copy(tournamentSettings = s.copy(tournamentName = name, troupeSize = size, roundTimerMinutes = timer, hostParticipating = participating), tournamentPlayers = updated) }; syncTournamentToAll()
    }

    fun startTournamentFirstRound() {
        if (!_state.value.isTournamentHost) return
        val ps = _state.value.tournamentPlayers.shuffled(); val pairings = mutableListOf<TournamentPairing>()
        for (i in 0 until ps.size step 2) { if (i + 1 < ps.size) pairings.add(TournamentPairing(ps[i].deviceId, ps[i + 1].deviceId)) }
        _state.update { it.copy(currentTournamentRound = TournamentRound(1, pairings, TournamentRoundStatus.SELECTION)) }; syncTournamentToAll()
    }

    fun addManualTournamentPlayer(name: String) { if (_state.value.isTournamentHost) { _state.update { it.copy(tournamentPlayers = it.tournamentPlayers + TournamentPlayer(name, "manual_${UUID.randomUUID()}", null, true)) }; syncTournamentToAll() } }
    fun updateManualPlayerName(id: String, name: String) { if (_state.value.isTournamentHost && id.startsWith("manual_")) { _state.update { it.copy(tournamentPlayers = it.tournamentPlayers.map { if (it.deviceId == id) it.copy(name = name) else it }) }; syncTournamentToAll() } }
    fun removeTournamentPlayer(id: String) { if (_state.value.isTournamentHost) { _state.update { it.copy(tournamentPlayers = it.tournamentPlayers.filter { it.deviceId != id }) }; syncTournamentToAll() } }

    fun confirmTournamentCharacterSelection(ids: List<Int>, targetId: String? = null) {
        val cur = _state.value; val r = cur.currentTournamentRound ?: return; val id = targetId ?: persistentDeviceId
        val ups = r.pairings.map { if (it.player1Id == id) it.copy(player1CharacterIds = ids, player1Confirmed = true) else if (it.player2Id == id) it.copy(player2CharacterIds = ids, player2Confirmed = true) else it }
        _state.update { it.copy(currentTournamentRound = r.copy(pairings = ups)) }
        ups.find { it.player1Id == id || it.player2Id == id }?.let { nearbyManager.sendPayloadToAll(MessageParser.encode(SessionMessage.TournamentPairingUpdate(it))); if (cur.isTournamentHost) syncTournamentToAll() }
    }

    fun setTournamentInitiative(pairing: TournamentPairing, winnerId: String) {
        val cur = _state.value; val r = cur.currentTournamentRound ?: return
        val ups = r.pairings.map { if (it.player1Id == pairing.player1Id && it.player2Id == pairing.player2Id) {
            if (cur.isTournamentHost && (it.player1Id.startsWith("manual_") || it.player2Id.startsWith("manual_"))) it.copy(player1InitiativeSelection = winnerId, player2InitiativeSelection = winnerId, initiativePlayerId = winnerId)
            else { val p1 = if (persistentDeviceId == it.player1Id) winnerId else it.player1InitiativeSelection; val p2 = if (persistentDeviceId == it.player2Id) winnerId else it.player2InitiativeSelection; it.copy(player1InitiativeSelection = p1, player2InitiativeSelection = p2, initiativePlayerId = if (p1 != null && p1 == p2) p1 else null) }
        } else it }
        _state.update { it.copy(currentTournamentRound = r.copy(pairings = ups)) }
        ups.find { it.player1Id == pairing.player1Id && it.player2Id == pairing.player2Id }?.let { nearbyManager.sendPayloadToAll(MessageParser.encode(SessionMessage.TournamentPairingUpdate(it))); if (cur.isTournamentHost) syncTournamentToAll() }
    }

    fun confirmTournamentDeployment(pairing: TournamentPairing) {
        val cur = _state.value; val r = cur.currentTournamentRound ?: return
        val ups = r.pairings.map { if (it.player1Id == pairing.player1Id && it.player2Id == pairing.player2Id) {
            var p1 = it.player1DeploymentReady; var p2 = it.player2DeploymentReady
            if (persistentDeviceId == it.player1Id || (cur.isTournamentHost && it.player1Id.startsWith("manual_"))) p1 = true
            if (persistentDeviceId == it.player2Id || (cur.isTournamentHost && it.player2Id.startsWith("manual_"))) p2 = true
            it.copy(player1DeploymentReady = p1, player2DeploymentReady = p2)
        } else it }
        _state.update { it.copy(currentTournamentRound = r.copy(pairings = ups)) }
        ups.find { it.player1Id == pairing.player1Id && it.player2Id == pairing.player2Id }?.let { nearbyManager.sendPayloadToAll(MessageParser.encode(SessionMessage.TournamentPairingUpdate(it))); if (cur.isTournamentHost) syncTournamentToAll() }
    }

    fun startTournamentActiveGames() { if (_state.value.isTournamentHost) { _state.update { it.copy(currentTournamentRound = it.currentTournamentRound?.copy(status = TournamentRoundStatus.ACTIVE_GAME)) }; syncTournamentToAll() } }
    fun disbandTournament() { nearbyManager.sendPayloadToAll(MessageParser.encode(SessionMessage.TournamentDisbanded("Tournament has been cancelled, please speak with your TO"))); viewModelScope.launch { delay(300); _uiEvent.emit(UiEvent.TournamentDisbanded); leaveSession() } }

    fun leaveSession() {
        viewModelScope.launch {
            _state.update { it.copy(isLeaving = true) }
            if (_state.value.tournamentSettings != null && !_state.value.isTournamentHost) nearbyManager.sendPayloadToAll(MessageParser.encode(SessionMessage.LeaveMessage(persistentDeviceId)))
            nearbyManager.stopAll(); joiningTournamentEndpointId = null; delay(500)
            _state.update { it.copy(gameSession = null, tournamentSettings = null, isTournamentHost = false, tournamentPlayers = emptyList(), activeTroupes = emptyList(), characterPlayStates = emptyMap(), isLeaving = false, currentTournamentRound = null, tournamentHistory = emptyList()) }
        }
    }

    override fun onCleared() { super.onCleared(); nearbyManager.stopAll() }
    fun broadcastGameplayUpdate(update: SessionMessage.GameplayUpdate) { nearbyManager.sendPayloadToAll(MessageParser.encode(update)) }
    fun broadcastTurnUpdate(turn: Int, states: Map<String, CharacterPlayState>) { nearbyManager.sendPayloadToAll(MessageParser.encode(SessionMessage.TurnUpdate(turn, states))) }
    fun broadcastStartGame() { nearbyManager.sendPayloadToAll(MessageParser.encode(SessionMessage.StartGame)) }

    var editingCampaignId by mutableStateOf<Int?>(null)
    var newCampaignName by mutableStateOf("")
    var newCampaignDescription by mutableStateOf("")
    var newCampaignAttacksEnabled by mutableStateOf(false)
    var newCampaignTotalRounds by mutableIntStateOf(0)
    var newCampaignGameSize by mutableIntStateOf(6)
    var newCampaignStartingCharacters by mutableIntStateOf(6)
    var newCampaignCharacterGrowthEvery by mutableIntStateOf(1)
    var newCampaignUpgradeGrowthEvery by mutableIntStateOf(3)
    var selectedCampaignPlayers = mutableStateListOf<CampaignPlayer>()
    var currentCampaignSubScreen by mutableStateOf<CampaignSubScreen?>(null)

    // Machination phase draft state — persists across navigation within session
    var isCampaignMachinating: Boolean by mutableStateOf(false)
    var isViewingCardDraw: Boolean by mutableStateOf(false)
    val machinationType1: SnapshotStateMap<String, MachinationType?> = mutableStateMapOf()
    val machinationType2: SnapshotStateMap<String, MachinationType?> = mutableStateMapOf()
    val machinationType3: SnapshotStateMap<String, MachinationType?> = mutableStateMapOf()
    val machinationTarget1: SnapshotStateMap<String, String> = mutableStateMapOf()
    val machinationTarget2: SnapshotStateMap<String, String> = mutableStateMapOf()
    val machinationTarget3: SnapshotStateMap<String, String> = mutableStateMapOf()
    val machinationIsAttacking: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    val machinationAttackType: SnapshotStateMap<String, AttackType> = mutableStateMapOf()
    val machinationAttackTargetPlayer: SnapshotStateMap<String, String> = mutableStateMapOf()
    val machinationAttackTargetChar: SnapshotStateMap<String, Int> = mutableStateMapOf()

    fun initMachinationDraft(campaign: Campaign) {
        machinationType1.clear(); machinationType2.clear(); machinationType3.clear()
        machinationTarget1.clear(); machinationTarget2.clear(); machinationTarget3.clear()
        machinationIsAttacking.clear(); machinationAttackType.clear()
        machinationAttackTargetPlayer.clear(); machinationAttackTargetChar.clear()
        campaign.players.forEach { machinationType1[it.id] = null; machinationType2[it.id] = null; machinationType3[it.id] = null }
        isCampaignMachinating = true
    }

    fun clearMachinationDraft() {
        machinationType1.clear(); machinationType2.clear(); machinationType3.clear()
        machinationTarget1.clear(); machinationTarget2.clear(); machinationTarget3.clear()
        machinationIsAttacking.clear(); machinationAttackType.clear()
        machinationAttackTargetPlayer.clear(); machinationAttackTargetChar.clear()
        isCampaignMachinating = false
    }

    fun saveMachinationDraft(campaign: Campaign) {
        val draft = campaign.players.associate { player ->
            player.id to PlayerMachinationDraft(
                machType1 = machinationType1[player.id],
                machType2 = machinationType2[player.id],
                machType3 = machinationType3[player.id],
                target1 = machinationTarget1[player.id] ?: "",
                target2 = machinationTarget2[player.id] ?: "",
                target3 = machinationTarget3[player.id] ?: "",
                isAttacking = machinationIsAttacking[player.id] == true,
                attackType = machinationAttackType[player.id] ?: AttackType.ASSAULT,
                attackTargetPlayerId = machinationAttackTargetPlayer[player.id] ?: "",
                attackTargetCharId = machinationAttackTargetChar[player.id] ?: -1
            )
        }
        updateCampaign(campaign.copy(machinationPhaseActive = true, machinationDraft = draft))
    }

    fun loadMachinationDraft(campaign: Campaign) {
        val draft = campaign.machinationDraft ?: return
        machinationType1.clear(); machinationType2.clear(); machinationType3.clear()
        machinationTarget1.clear(); machinationTarget2.clear(); machinationTarget3.clear()
        machinationIsAttacking.clear(); machinationAttackType.clear()
        machinationAttackTargetPlayer.clear(); machinationAttackTargetChar.clear()
        draft.forEach { (playerId, pd) ->
            machinationType1[playerId] = pd.machType1
            machinationType2[playerId] = pd.machType2
            machinationType3[playerId] = pd.machType3
            if (pd.target1.isNotEmpty()) machinationTarget1[playerId] = pd.target1
            if (pd.target2.isNotEmpty()) machinationTarget2[playerId] = pd.target2
            if (pd.target3.isNotEmpty()) machinationTarget3[playerId] = pd.target3
            machinationIsAttacking[playerId] = pd.isAttacking
            machinationAttackType[playerId] = pd.attackType
            if (pd.attackTargetPlayerId.isNotEmpty()) machinationAttackTargetPlayer[playerId] = pd.attackTargetPlayerId
            if (pd.attackTargetCharId != -1) machinationAttackTargetChar[playerId] = pd.attackTargetCharId
        }
        isCampaignMachinating = true
    }

    fun resetNewCampaignFields() {
        editingCampaignId = null
        newCampaignName = ""
        newCampaignDescription = ""
        newCampaignAttacksEnabled = false
        newCampaignTotalRounds = 0
        newCampaignGameSize = 6
        newCampaignStartingCharacters = 6
        newCampaignCharacterGrowthEvery = 1
        newCampaignUpgradeGrowthEvery = 3
        selectedCampaignPlayers.clear()
        currentCampaignSubScreen = null
        clearMachinationDraft()
    }

    fun editCampaign(campaignId: Int) {
        if (editingCampaignId == campaignId) return
        val campaign = state.value.campaigns.find { it.id == campaignId } ?: return
        editingCampaignId = campaign.id
        newCampaignName = campaign.name
        newCampaignDescription = campaign.description
        newCampaignAttacksEnabled = campaign.attacksEnabled
        newCampaignTotalRounds = campaign.totalRounds
        newCampaignGameSize = campaign.gameSize
        newCampaignStartingCharacters = campaign.startingCharacters
        newCampaignCharacterGrowthEvery = campaign.characterGrowthEvery
        newCampaignUpgradeGrowthEvery = campaign.upgradeGrowthEvery
        selectedCampaignPlayers.clear()
        selectedCampaignPlayers.addAll(campaign.players)
    }

    fun createCampaign(
        name: String,
        description: String,
        players: List<CampaignPlayer>,
        attacksEnabled: Boolean,
        totalRounds: Int,
        gameSize: Int,
        startingCharacters: Int,
        characterGrowthEvery: Int,
        upgradeGrowthEvery: Int
    ) {
        viewModelScope.launch {
            val existing = state.value.campaigns.find { it.id == editingCampaignId }
            val campaign = existing?.copy(
                name = name,
                description = description,
                players = players,
                attacksEnabled = attacksEnabled,
                totalRounds = totalRounds,
                gameSize = gameSize,
                startingCharacters = startingCharacters,
                characterGrowthEvery = characterGrowthEvery,
                upgradeGrowthEvery = upgradeGrowthEvery
            ) ?: run {
                val rounds = generateRoundRobinSchedule(players.map { it.id }, totalRounds)
                // Players with a bye in round 1 miss the pre-round-1 machination phase (it doesn't
                // exist), so they can't get the normal 3-slot bye bonus there. Give them +1 MP now
                // as a one-time compensating credit they can spend at any point in the campaign.
                val round1ByeIds = rounds.firstOrNull()?.skipPlayerIds?.toSet() ?: emptySet()
                val seededPlayers = if (round1ByeIds.isEmpty()) players else
                    players.map { if (it.id in round1ByeIds) it.copy(machinationPoints = it.machinationPoints + 1) else it }
                Campaign(
                    id = 0,
                    name = name,
                    description = description,
                    players = seededPlayers,
                    attacksEnabled = attacksEnabled,
                    totalRounds = totalRounds,
                    gameSize = gameSize,
                    startingCharacters = startingCharacters,
                    characterGrowthEvery = characterGrowthEvery,
                    upgradeGrowthEvery = upgradeGrowthEvery,
                    rounds = rounds
                )
            }

            repository.upsertCampaign(campaign)
            resetNewCampaignFields()
        }
    }

    private fun generateRoundRobinSchedule(playerIds: List<String>, totalRounds: Int): List<CampaignRound> {
        if (playerIds.size < 2 || totalRounds <= 0) return emptyList()

        val bye = "__bye__"
        val players = if (playerIds.size % 2 == 0) playerIds else playerIds + bye
        val m = players.size
        val rotatable = players.drop(1)

        return (1..totalRounds).map { roundNumber ->
            val rotationIndex = (roundNumber - 1) % (m - 1)
            val rotated = rotatable.subList(rotationIndex, m - 1) + rotatable.subList(0, rotationIndex)
            val circle = listOf(players[0]) + rotated

            val games = mutableListOf<CampaignGame>()
            val skipIds = mutableListOf<String>()

            for (i in 0 until m / 2) {
                val p1 = circle[i]
                val p2 = circle[m - 1 - i]
                when {
                    p1 == bye -> skipIds.add(p2)
                    p2 == bye -> skipIds.add(p1)
                    else -> games.add(CampaignGame(playerIds = listOf(p1, p2)))
                }
            }

            CampaignRound(roundNumber = roundNumber, games = games, skipPlayerIds = skipIds)
        }
    }

    private sealed interface ScheduleSwapResult {
        data class Success(val schedule: List<CampaignRound>) : ScheduleSwapResult
        data class Error(val message: String) : ScheduleSwapResult
    }

    private fun swapScheduleBye(
        schedule: List<CampaignRound>,
        playerCount: Int,
        targetRoundNumber: Int,
        newByePlayerId: String
    ): ScheduleSwapResult {
        if (playerCount % 2 == 0) return ScheduleSwapResult.Error("No byes exist in an even-player schedule")
        val targetRound = schedule.find { it.roundNumber == targetRoundNumber }
            ?: return ScheduleSwapResult.Error("Round $targetRoundNumber not found")
        val currentByeHolder = targetRound.skipPlayerIds.firstOrNull()
            ?: return ScheduleSwapResult.Error("Round $targetRoundNumber has no bye")
        if (currentByeHolder == newByePlayerId) return ScheduleSwapResult.Success(schedule)

        // Find newByePlayerId's bye in the same schedule cycle as targetRound
        val cycleLength = playerCount
        val targetCycle = (targetRoundNumber - 1) / cycleLength
        val sourceRound = schedule.find { r ->
            r.skipPlayerIds.contains(newByePlayerId) && (r.roundNumber - 1) / cycleLength == targetCycle
        } ?: return ScheduleSwapResult.Error("That player's bye is in a different cycle and can't be swapped here")

        // targetRound: swap newByePlayerId out of their game, put currentByeHolder in
        val newTargetGames = targetRound.games.map { game ->
            if (newByePlayerId in game.playerIds)
                game.copy(playerIds = game.playerIds.map { if (it == newByePlayerId) currentByeHolder else it })
            else game
        }
        // sourceRound: swap currentByeHolder out of their game, put newByePlayerId in
        val newSourceGames = sourceRound.games.map { game ->
            if (currentByeHolder in game.playerIds)
                game.copy(playerIds = game.playerIds.map { if (it == currentByeHolder) newByePlayerId else it })
            else game
        }
        val newSchedule = schedule.map { r ->
            when (r.roundNumber) {
                targetRoundNumber -> targetRound.copy(games = newTargetGames, skipPlayerIds = listOf(newByePlayerId))
                sourceRound.roundNumber -> sourceRound.copy(games = newSourceGames, skipPlayerIds = listOf(currentByeHolder))
                else -> r
            }
        }
        return ScheduleSwapResult.Success(newSchedule)
    }

    private fun validateSchedule(schedule: List<CampaignRound>, playerCount: Int): String? {
        if (playerCount % 2 == 0) return null
        val cycleLength = playerCount
        val allPlayerIds = (schedule.flatMap { it.games.flatMap { g -> g.playerIds } } + schedule.flatMap { it.skipPlayerIds }).distinct()
        val cycles = schedule.groupBy { (it.roundNumber - 1) / cycleLength }
        for ((_, rounds) in cycles) {
            val byeCounts = mutableMapOf<String, Int>()
            for (round in rounds) {
                for (id in round.skipPlayerIds) byeCounts[id] = (byeCounts[id] ?: 0) + 1
            }
            for ((_, count) in byeCounts) {
                if (count > 1) return "A player has more than one bye in a cycle"
            }
            if (rounds.size == cycleLength) {
                for (playerId in allPlayerIds) {
                    if ((byeCounts[playerId] ?: 0) == 0) return "A player is missing their bye in a full cycle"
                }
            }
        }
        val gameCounts = mutableMapOf<String, Int>()
        for (round in schedule) {
            for (game in round.games) {
                for (id in game.playerIds) gameCounts[id] = (gameCounts[id] ?: 0) + 1
            }
        }
        val counts = gameCounts.values
        if (counts.isNotEmpty() && counts.max() - counts.min() > 1) {
            return "Players have unequal numbers of games"
        }
        return null
    }

    fun updateCampaign(campaign: Campaign) {
        viewModelScope.launch { repository.upsertCampaign(campaign) }
    }

    fun deleteCampaign(campaign: Campaign) {
        viewModelScope.launch { repository.deleteCampaign(campaign) }
    }

    fun recordCampaignGameResult(campaign: Campaign, game: CampaignGame, winnerId: String?) {
        val newRounds = campaign.rounds.map { round ->
            if (round.roundNumber == campaign.currentRound) {
                round.copy(games = round.games.map { if (it.playerIds == game.playerIds) it.copy(winnerId = winnerId, isPlayed = true) else it })
            } else round
        }
        updateCampaign(campaign.copy(rounds = newRounds))
    }

    fun recordCampaignGameResultWithVP(
        campaign: Campaign,
        game: CampaignGame,
        playerVictoryPoints: Map<String, Int>,
        droppedPlayerIds: List<String> = emptyList()
    ) {
        val maxVp = playerVictoryPoints.values.maxOrNull() ?: 0
        val topPlayers = playerVictoryPoints.filter { it.value == maxVp }.keys.toList()
        val winnerId = if (topPlayers.size == 1) topPlayers.first() else null
        val newRounds = campaign.rounds.map { round ->
            if (round.roundNumber == campaign.currentRound) {
                round.copy(games = round.games.map { g ->
                    if (g.playerIds == game.playerIds)
                        g.copy(winnerId = winnerId, isPlayed = true, playerVictoryPoints = playerVictoryPoints, droppedPlayerIds = droppedPlayerIds)
                    else g
                })
            } else round
        }
        updateCampaign(campaign.copy(rounds = newRounds))
        // Add VP to each player's troupe — skip gracefully if troupe not assigned
        viewModelScope.launch {
            playerVictoryPoints.forEach { (playerId, vp) ->
                if (vp > 0) {
                    val troupeId = campaign.players.find { it.id == playerId }?.troupeId ?: return@forEach
                    val troupe = state.value.troupes.find { it.id == troupeId } ?: return@forEach
                    repository.upsertTroupe(troupe.copy(victoryPoints = troupe.victoryPoints + vp))
                }
            }
        }
    }

    fun recordCampaignAttack(campaign: Campaign, sourcePlayerId: String, targetPlayerId: String, characterId: Int, type: AttackType) {
        val source = campaign.players.find { it.id == sourcePlayerId } ?: return
        if (source.attackPoints < type.cost) return

        val newPlayers = campaign.players.map { 
            if (it.id == sourcePlayerId) it.copy(attackPoints = it.attackPoints - type.cost) else it 
        }

        val newRounds = campaign.rounds.toMutableList()
        val roundIdx = newRounds.indexOfFirst { it.roundNumber == campaign.currentRound }
        val attack = CampaignAttack(sourcePlayerId, targetPlayerId, characterId, type)
        
        if (roundIdx != -1) {
            val round = newRounds[roundIdx]
            newRounds[roundIdx] = round.copy(attacks = round.attacks + attack)
        } else {
            newRounds.add(CampaignRound(campaign.currentRound, emptyList(), emptyList(), listOf(attack)))
        }

        updateCampaign(campaign.copy(players = newPlayers, rounds = newRounds))
    }

    /** Computes MP deltas from machinations for the current round without mutating state. */
    fun computePendingMpDeltas(campaign: Campaign): Map<String, Int> {
        val round = campaign.rounds.find { it.roundNumber == campaign.currentRound }
            ?: return emptyMap()
        return computeMpDeltasForRound(campaign, round)
    }

    fun saveManualMpAdjustments(campaign: Campaign, adjustments: Map<String, Int>) {
        val updatedRounds = campaign.rounds.map { round ->
            if (round.roundNumber == campaign.currentRound)
                round.copy(manualMpAdjustments = adjustments)
            else round
        }
        updateCampaign(campaign.copy(rounds = updatedRounds))
    }

    private fun computeMpDeltasForRound(campaign: Campaign, round: CampaignRound): Map<String, Int> {
        val currentTroupes = state.value.troupes

        // Tier calculation — power = VP + MP at the START of this game week (before this round's
        // results). VP in troupes already includes this round's gains, so subtract them back out.
        val roundVpGained = campaign.players.associate { cp ->
            cp.id to round.games
                .filter { cp.id in it.playerIds }
                .sumOf { it.playerVictoryPoints[cp.id] ?: 0 }
        }
        data class PlayerPower(val playerId: String, val power: Int)
        val playerPowers = campaign.players.map { cp ->
            val currentVp = currentTroupes.find { it.id == cp.troupeId }?.victoryPoints ?: 0
            val preRoundVp = currentVp - (roundVpGained[cp.id] ?: 0)
            PlayerPower(cp.id, preRoundVp + cp.machinationPoints)
        }.sortedByDescending { it.power }

        val n = playerPowers.size
        val tierSize = (n / 3).coerceAtLeast(1)
        val topBoundaryPower = playerPowers.getOrNull(tierSize - 1)?.power ?: 0
        val actualTopCount = playerPowers.count { it.power >= topBoundaryPower }
        val remaining = playerPowers.drop(actualTopCount)
        val bottomIdx = (remaining.size - tierSize).coerceAtLeast(0)
        val bottomBoundaryPower = remaining.getOrNull(bottomIdx)?.power ?: Int.MIN_VALUE
        val tiedAtBottom = remaining.getOrNull(bottomIdx - 1)?.power == bottomBoundaryPower

        fun getTier(playerId: String): String {
            val power = playerPowers.find { it.playerId == playerId }?.power ?: 0
            return when {
                power >= topBoundaryPower -> "TOP"
                tiedAtBottom && power == bottomBoundaryPower -> "MIDDLE"
                power <= bottomBoundaryPower -> "BOTTOM"
                else -> "MIDDLE"
            }
        }

        val droppedPlayerIds = round.games.flatMap { it.droppedPlayerIds }.toSet()

        return campaign.players.associate { player ->
            var mpDelta = 0

            round.machinations.filter { it.sourcePlayerId == player.id }.forEach { mach ->
                val targetTier = getTier(mach.targetPlayerId)
                val targetWon = round.games.any {
                    it.playerIds.contains(mach.targetPlayerId) && it.winnerId == mach.targetPlayerId
                }
                val targetLost = round.games.any {
                    it.playerIds.contains(mach.targetPlayerId) && it.isPlayed &&
                    it.winnerId != mach.targetPlayerId && it.winnerId != null
                }

                if (mach.type == MachinationType.SUPPORT) {
                    when (targetTier) {
                        "TOP"    -> if (targetWon) mpDelta += 1 else if (targetLost) mpDelta -= 2
                        "MIDDLE" -> if (targetWon) mpDelta += 1 else if (targetLost) mpDelta -= 1
                        "BOTTOM" -> if (targetWon) mpDelta += 1
                    }
                } else {
                    when (targetTier) {
                        "TOP"    -> if (targetLost) mpDelta += 1
                        "MIDDLE" -> if (targetWon) mpDelta -= 1 else if (targetLost) mpDelta += 1
                        "BOTTOM" -> if (targetWon) mpDelta -= 1
                    }
                }
            }

            val supportsReceived = if (player.id in droppedPlayerIds) 0 else
                round.machinations.count { it.targetPlayerId == player.id && it.type == MachinationType.SUPPORT }
            val sabotagesReceived = if (player.id in droppedPlayerIds) 0 else
                round.machinations.count { it.targetPlayerId == player.id && it.type == MachinationType.SABOTAGE }
            val rawCards = 2 + supportsReceived - sabotagesReceived
            val cardOverflow = (rawCards - 3).coerceAtLeast(0)
            mpDelta += cardOverflow

            player.id to mpDelta
        }
    }

    fun progressCampaignRound(campaign: Campaign) {
        val currentRound = campaign.rounds.find { it.roundNumber == campaign.currentRound } ?: return

        // 1. Compute MP deltas from machinations
        val mpDeltaMap = computeMpDeltasForRound(campaign, currentRound)

        // 2. Apply machination deltas + manual adjustments, update card draw counts
        data class PlayerResult(val updated: CampaignPlayer, val totalMpDelta: Int)
        val results = campaign.players.map { player ->
            val machinationDelta = mpDeltaMap[player.id] ?: 0
            val manualDelta = currentRound.manualMpAdjustments[player.id] ?: 0
            val totalDelta = machinationDelta + manualDelta

            val droppedPlayerIds = currentRound.games.flatMap { it.droppedPlayerIds }.toSet()
            val supportsReceived = if (player.id in droppedPlayerIds) 0 else
                currentRound.machinations.count { it.targetPlayerId == player.id && it.type == MachinationType.SUPPORT }
            val sabotagesReceived = if (player.id in droppedPlayerIds) 0 else
                currentRound.machinations.count { it.targetPlayerId == player.id && it.type == MachinationType.SABOTAGE }
            val rawCards = 2 + supportsReceived - sabotagesReceived
            val cardDraw = rawCards.coerceIn(1, 3)

            PlayerResult(
                player.copy(
                    machinationPoints = player.machinationPoints + totalDelta,
                    campaignCardDraw = cardDraw
                ),
                totalDelta
            )
        }

        val newPlayers = results.map { it.updated }
        val mpDeltas = results.filter { it.totalMpDelta != 0 }.associate { it.updated.id to it.totalMpDelta }

        // 3. Stamp the completed round with its MP results, then advance
        val updatedRounds = campaign.rounds.map { round ->
            if (round.roundNumber == campaign.currentRound) round.copy(mpDeltas = mpDeltas) else round
        }
        updateCampaign(campaign.copy(
            players = newPlayers,
            currentRound = campaign.currentRound + 1,
            rounds = updatedRounds
        ))
    }

    fun overrideCampaignGamePlayerName(campaign: Campaign, game: CampaignGame, playerId: String, overrideName: String) {
        val newRounds = campaign.rounds.map { round ->
            if (round.roundNumber == campaign.currentRound) {
                round.copy(games = round.games.map { g ->
                    if (g.playerIds == game.playerIds) {
                        val overrides = g.playerNameOverrides.toMutableMap()
                        if (overrideName.isBlank()) overrides.remove(playerId) else overrides[playerId] = overrideName
                        g.copy(playerNameOverrides = overrides)
                    } else g
                })
            } else round
        }
        updateCampaign(campaign.copy(rounds = newRounds))
    }

    fun broadcastTroupeSelectionForCampaign(troupe: Troupe, targetPlayerId: String) {
        val playerIdx = selectedCampaignPlayers.indexOfFirst { it.id == targetPlayerId }
        if (playerIdx != -1) {
            selectedCampaignPlayers[playerIdx] = selectedCampaignPlayers[playerIdx].copy(troupeId = troupe.id)
        }
    }
}
