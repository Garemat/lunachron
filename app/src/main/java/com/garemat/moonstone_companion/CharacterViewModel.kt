package com.garemat.moonstone_companion

import android.app.Application
import android.content.Context
import android.util.Base64
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import java.util.UUID

class CharacterViewModel(
    application: Application,
    private val dao: CharacterDAO
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("moonstone_prefs", Context.MODE_PRIVATE)
    private val nearbyManager = NearbyManager(application)
    private val client = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 15000
        }
    }

    private val persistentDeviceId: String = prefs.getString("persistent_device_id", null) ?: run {
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString("persistent_device_id", newId).apply()
        newId
    }

    private val _state = MutableStateFlow(CharacterState(
        name = prefs.getString("player_name", "") ?: "",
        deviceId = persistentDeviceId,
        theme = AppTheme.valueOf(prefs.getString("app_theme", AppTheme.MOONSTONE.name) ?: AppTheme.DEFAULT.name),
        layoutDensity = LayoutDensity.valueOf(prefs.getString("layout_density", LayoutDensity.COZY.name) ?: LayoutDensity.COZY.name),
        useLocalModeByDefault = prefs.getBoolean("use_local_mode_by_default", false),
        hasSeenGlobalTutorial = prefs.getBoolean("has_seen_global_tutorial", false),
        newsItems = loadCachedNews()
    ))
    
    private val _characters = dao.getCharactersOrderedByName()
    private val _troupes = dao.getTroupes()
    val gameResults = dao.getGameResults().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _rules = MutableStateFlow<List<RuleSection>>(emptyList())
    val rules = _rules.asStateFlow()

    val state = combine(_state, _characters, _troupes) { state, characters, troupes ->
        state.copy(characters = characters, troupes = troupes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)

    val discoveredEndpoints = nearbyManager.discoveredEndpoints

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private val _scannedTroupeEvent = MutableSharedFlow<Pair<Int, Troupe>>()
    val scannedTroupeEvent = _scannedTroupeEvent.asSharedFlow()

    sealed class UiEvent {
        data object GameStarted : UiEvent()
        data class TroupeCreated(val troupe: Troupe, val playerIndex: Int?) : UiEvent()
        data object TournamentJoined : UiEvent()
        data object TournamentDisbanded : UiEvent()
    }

    init {
        loadRules()
        fetchNews()
        nearbyManager.setPayloadListener { endpointId, message -> handleSessionMessage(endpointId, message) }
    }

    private fun loadRules() {
        viewModelScope.launch {
            try {
                val jsonString = getApplication<Application>().assets.open("rules.json").bufferedReader().use { it.readText() }
                _rules.value = Json.decodeFromString<List<RuleSection>>(jsonString)
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
                            prefs.edit().putString("cached_news", Json.encodeToString(newItems)).apply()
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
    var pendingTroupePlayerIndex by mutableStateOf<Int?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val playersWithCharacters = state.flatMapLatest { currentState ->
        val troupes = currentState.activeTroupes
        if (troupes.isEmpty()) return@flatMapLatest flowOf(emptyList<Pair<Troupe, List<Character>>>())
        val flows = troupes.map { troupe -> dao.getCharactersByIds(troupe.characterIds).map { troupe to it } }
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
            players.forEachIndexed { pIdx, (_, characters) ->
                characters.forEachIndexed { cIdx, character ->
                    initial["${pIdx}_${cIdx}"] = CharacterPlayState(character.health, calculateReplenishedEnergy(character, character.health))
                }
            }
            _state.update { it.copy(characterPlayStates = initial, currentTurn = 1, turnHistory = emptyList()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onEvent(event: CharacterEvent) {
        when (event) {
            is CharacterEvent.DeleteTroupe -> viewModelScope.launch { dao.deleteTroupe(event.troupe) }
            is CharacterEvent.EditTroupe -> {
                editingTroupeId = event.troupe.id; newTroupeName = event.troupe.troupeName
                selectedTroupeFaction = event.troupe.faction; selectedCharacterIds = event.troupe.characterIds.toSet()
                isTournamentList = event.troupe.isTournamentList
            }
            CharacterEvent.SaveTroupe -> {
                val troupe = Troupe(editingTroupeId ?: 0, newTroupeName, selectedTroupeFaction, selectedCharacterIds.toList(), "", isTournamentList)
                viewModelScope.launch { 
                    val id = dao.upsertTroupe(troupe)
                    _uiEvent.emit(UiEvent.TroupeCreated(troupe.copy(id = id.toInt()), pendingTroupePlayerIndex))
                    pendingTroupePlayerIndex = null
                }
                resetNewTroupeFields()
            }
            is CharacterEvent.SortCharacters -> _state.update { it.copy(sortType = event.sortType) }
            CharacterEvent.DismissError -> _state.update { it.copy(errorMessage = null) }
            is CharacterEvent.UpdateUserName -> {
                val old = _state.value.name; _state.update { it.copy(name = event.name) }
                prefs.edit().putString("player_name", event.name).apply()
                if (event.name != old) nearbyManager.sendPayloadToAll(MessageParser.encode(SessionMessage.PlayerInfoUpdate(persistentDeviceId, event.name)))
            }
            is CharacterEvent.ChangeTheme -> {
                _state.update { it.copy(theme = event.theme) }
                prefs.edit().putString("app_theme", event.theme.name).apply()
            }
            is CharacterEvent.ChangeLayoutDensity -> {
                _state.update { it.copy(layoutDensity = event.density) }
                prefs.edit().putString("layout_density", event.density.name).apply()
            }
            is CharacterEvent.SetLocalModeDefault -> {
                _state.update { it.copy(useLocalModeByDefault = event.useLocal) }
                prefs.edit().putBoolean("use_local_mode_by_default", event.useLocal).apply()
            }
            is CharacterEvent.SetHasSeenTutorial -> {
                if (event.tutorialKey == "global") _state.update { it.copy(hasSeenGlobalTutorial = event.seen) }
                prefs.edit().putBoolean(if (event.tutorialKey == "global") "has_seen_global_tutorial" else "has_seen_${event.tutorialKey}_tutorial", event.seen).apply()
            }
            CharacterEvent.RefreshNews -> fetchNews()
            is CharacterEvent.UpdateCharacterHealth -> {
                updateCharacterState(event.playerIndex, event.charIndex) { it.copy(currentHealth = event.health) }
                broadcastGameplayUpdate(SessionMessage.GameplayUpdate(event.playerIndex, event.charIndex, health = event.health))
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
            CharacterEvent.ResetGamePlayState -> _state.update { it.copy(characterPlayStates = emptyMap(), currentTurn = 1, turnHistory = emptyList(), winnerName = null, isTie = false) }
            CharacterEvent.NextTurn -> handleReadyAction(GameAction.NEXT_TURN)
            CharacterEvent.RewindTurn -> handleReadyAction(GameAction.REWIND)
            is CharacterEvent.UpdateCharacterMoonstones -> {
                updateCharacterState(event.playerIndex, event.charIndex) { it.copy(moonstones = event.stones) }
                broadcastGameplayUpdate(SessionMessage.GameplayUpdate(event.playerIndex, event.charIndex, moonstones = event.stones))
            }
            CharacterEvent.AbandonGame -> {
                _state.update { it.copy(activeTroupes = emptyList(), characterPlayStates = emptyMap(), currentTurn = 1, gameSession = null, turnHistory = emptyList(), winnerName = null, isTie = false) }
                nearbyManager.stopAll()
            }
            CharacterEvent.EndGame -> handleReadyAction(GameAction.NEXT_TURN, forceEnd = true)
            is CharacterEvent.CreateTournament -> startHostingTournament(event.tournamentName, event.troupeSize, event.timer, event.hostParticipating, event.passcode)
            else -> {}
        }
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
            dao.upsertGameResult(GameResult(timestamp = System.currentTimeMillis(), playerStats = playerStats, winnerIndex = winnerIndex))
        }
    }

    private fun handleNextTurn() {
        val cur = _state.value; val pData = playersWithCharacters.value
        if (pData.isEmpty()) return
        val newStates = cur.characterPlayStates.toMutableMap()
        pData.forEachIndexed { pIdx, (_, characters) ->
            characters.forEachIndexed { cIdx, character ->
                val key = "${pIdx}_$cIdx"; val ps = newStates[key]
                if (ps != null && ps.currentHealth > 0) newStates[key] = ps.copy(currentEnergy = calculateReplenishedEnergy(character, ps.currentHealth), usedAbilities = emptyMap())
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
        _state.update { it.copy(characterPlayStates = emptyMap(), currentTurn = 1, activeTroupes = troupes, turnHistory = emptyList(), readyForNextTurn = emptySet(), readyForRewind = emptySet(), winnerName = null, isTie = false) }
    }

    fun saveTroupe(troupe: Troupe) {
        viewModelScope.launch { dao.upsertTroupe(troupe.copy(id = 0)) }
    }

    fun onTroupeScanned(playerIndex: Int, troupe: Troupe) {
        viewModelScope.launch { _scannedTroupeEvent.emit(playerIndex to troupe) }
    }

    fun resetNewTroupeFields(isTournament: Boolean = false) {
        editingTroupeId = null; newTroupeName = ""; selectedTroupeFaction = Faction.COMMONWEALTH; selectedCharacterIds = emptySet(); isTournamentList = isTournament
    }

    fun generateFullShareCode(troupe: Troupe, characters: List<Character>): String {
        val factionCode = when (troupe.faction) { Faction.COMMONWEALTH -> "A"; Faction.DOMINION -> "B"; Faction.LESHAVULT -> "C"; Faction.SHADES -> "D" }
        val selectedCodes = troupe.characterIds.mapNotNull { id -> characters.find { it.id == id }?.shareCode }.joinToString("")
        val rawCode = "${troupe.troupeName}|$factionCode${if (troupe.isTournamentList) "1" else "0"}$selectedCodes"
        return Base64.encodeToString(rawCode.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun importTroupe(fullCode: String, allCharacters: List<Character>): Troupe? {
        try {
            val decoded = String(Base64.decode(fullCode, Base64.DEFAULT), Charsets.UTF_8)
            val parts = decoded.split("|"); if (parts.size != 2 || parts[1].isEmpty()) return null
            val codeBody = parts[1]
            val faction = when (codeBody[0]) { 'A' -> Faction.COMMONWEALTH; 'B' -> Faction.DOMINION; 'C' -> Faction.LESHAVULT; 'D' -> Faction.SHADES; else -> return null }
            val ids = codeBody.substring(2).chunked(3).mapNotNull { code -> allCharacters.find { it.shareCode == code }?.id }
            return Troupe(0, parts[0], faction, ids, fullCode, codeBody[1] == '1')
        } catch (e: Exception) { return null }
    }

    fun startHosting(hostName: String) {
        nearbyManager.stopAll(); val actual = _state.value.name.ifEmpty { hostName }
        _state.update { it.copy(gameSession = GameSession(listOf(GamePlayer(actual, deviceId = persistentDeviceId)), true, UUID.randomUUID().toString().take(8))) }
        nearbyManager.startAdvertising(actual)
    }

    fun startHostingTournament(name: String, size: TroupeSizeSetting, timer: Int, participating: Boolean, passcode: String) {
        nearbyManager.stopAll(); val actual = _state.value.name.ifEmpty { "Host" }
        _state.update { it.copy(isTournamentHost = true, tournamentSettings = TournamentSettings(name, size, timer, participating, UUID.randomUUID().toString().take(8), passcode), tournamentPlayers = if (participating) listOf(TournamentPlayer(actual, persistentDeviceId)) else emptyList()) }
        nearbyManager.startAdvertising(name)
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
}
