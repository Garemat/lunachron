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

    // Persistent Unique Device ID for session rejoin
    private val persistentDeviceId: String = prefs.getString("persistent_device_id", null) ?: run {
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString("persistent_device_id", newId).apply()
        newId
    }

    private val _state = MutableStateFlow(CharacterState(
        name = prefs.getString("player_name", "") ?: "",
        deviceId = persistentDeviceId,
        theme = AppTheme.valueOf(prefs.getString("app_theme", AppTheme.MOONSTONE.name) ?: AppTheme.DEFAULT.name),
        useLocalModeByDefault = prefs.getBoolean("use_local_mode_by_default", false),
        hasSeenGlobalTutorial = prefs.getBoolean("has_seen_global_tutorial", false),
        newsItems = loadCachedNews()
    ))
    
    private val _characters = dao.getCharactersOrderedByName()
    private val _troupes = dao.getTroupes()
    val gameResults = dao.getGameResults().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Rules logic
    private val _rules = MutableStateFlow<List<RuleSection>>(emptyList())
    val rules = _rules.asStateFlow()

    val state = combine(_state, _characters, _troupes) { state, characters, troupes ->
        state.copy(
            characters = characters,
            troupes = troupes
        )
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
        nearbyManager.setPayloadListener { endpointId, message ->
            handleSessionMessage(endpointId, message)
        }
        
        nearbyManager.setConnectionListener { endpointId ->
            // Connection logic handled by specific request methods
        }
    }

    private fun loadRules() {
        viewModelScope.launch {
            try {
                val jsonString = getApplication<Application>().assets.open("rules.json").bufferedReader().use { it.readText() }
                val loadedRules = Json.decodeFromString<List<RuleSection>>(jsonString)
                _rules.value = loadedRules
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- News Feed Logic ---

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
                        val aTag = element.select("a[href*='/latest/']").firstOrNull() 
                            ?: element.select("a").firstOrNull() 
                            ?: return@mapNotNull null
                            
                        val urlRel = aTag.attr("href")
                        
                        if (urlRel.contains("/category/") || 
                            urlRel.endsWith("/latest") || 
                            urlRel.endsWith("/latest/") ||
                            urlRel.contains("?category=")
                        ) {
                            return@mapNotNull null
                        }
                        
                        val url = if (urlRel.startsWith("http")) urlRel else baseUrl + urlRel
                        
                        val title = element.select("h1, h2, h3, .summary-title, .blog-title, .blog-item-title").firstOrNull()?.text()?.trim() 
                            ?: aTag.text().trim()
                        
                        if (title.isEmpty()) return@mapNotNull null

                        val date = element.select("time, .summary-metadata-item--date, .blog-date, .blog-meta-item--date")
                            .firstOrNull()?.text()?.trim() ?: "Recently"
                            
                        val summary = element.select(".summary-excerpt, .blog-excerpt, .blog-item-excerpt")
                            .firstOrNull()?.text()?.trim() 
                            ?: element.select("p").firstOrNull()?.text()?.trim()
                            ?: ""
                        
                        val img = element.select("img").firstOrNull()
                        var imageUrl = img?.let {
                            it.attr("data-src").ifEmpty { 
                                it.attr("src").ifEmpty { 
                                    it.attr("data-image") 
                                }
                            }
                        } ?: ""
                        
                        if (imageUrl.isNotEmpty()) {
                            if (!imageUrl.startsWith("http")) {
                                imageUrl = baseUrl + if (imageUrl.startsWith("/")) "" else "/" + imageUrl
                            }
                            if (!imageUrl.contains("format=")) {
                                imageUrl += if (imageUrl.contains("?")) "&format=1000w" else "?format=1000w"
                            }
                        }

                        NewsItem(
                            title = title,
                            url = url,
                            date = date,
                            imageUrl = imageUrl.ifEmpty { null },
                            summary = summary.ifEmpty { null }
                        )
                    }.distinctBy { it.url }.take(10)

                    if (newItems.isNotEmpty()) {
                        val currentItems = _state.value.newsItems
                        val isSameAsCached = currentItems.isNotEmpty() && currentItems[0].url == newItems[0].url
                        
                        if (!isSameAsCached || currentItems.isEmpty()) {
                            _state.update { it.copy(newsItems = newItems) }
                            prefs.edit().putString("cached_news", Json.encodeToString(newItems)).apply()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _state.update { it.copy(isFetchingNews = false) }
            }
        }
    }

    // Troupe Draft State
    var editingTroupeId by mutableStateOf<Int?>(null)
    var newTroupeName by mutableStateOf("")
    var selectedTroupeFaction by mutableStateOf(Faction.COMMONWEALTH)
    var selectedCharacterIds by mutableStateOf(setOf<Int>())
    var isTournamentList by mutableStateOf(false)
    var pendingTroupePlayerIndex by mutableStateOf<Int?>(null)

    // Active Game State
    val playersWithCharacters = state.flatMapLatest { currentState ->
        val troupes = currentState.activeTroupes
        if (troupes.isEmpty()) return@flatMapLatest flowOf(emptyList<Pair<Troupe, List<Character>>>())
        
        val flows = troupes.map { troupe ->
            dao.getCharactersByIds(troupe.characterIds).map { troupe to it }
        }
        combine(flows) { troupePairs ->
            troupePairs.toList().map { (troupe, characters) ->
                val summonIds = characters.flatMap { it.summonsCharacterIds }
                if (summonIds.isNotEmpty()) {
                    val allCharacters = currentState.characters
                    val currentIdsInTroupe = characters.map { it.id }.toSet()
                    
                    val summonedCharacters = summonIds.filter { sId ->
                        !currentIdsInTroupe.contains(sId)
                    }.mapNotNull { sId ->
                        allCharacters.find { it.id == sId }
                    }
                    
                    troupe to (characters + summonedCharacters)
                } else {
                    troupe to characters
                }
            }
        }
    }.onEach { players ->
        if (players.isNotEmpty() && _state.value.characterPlayStates.isEmpty()) {
            val initialStates = mutableMapOf<String, CharacterPlayState>()
            players.forEachIndexed { pIdx, (troupe, characters) ->
                characters.forEachIndexed { cIdx, character ->
                    val replenishedEnergy = calculateReplenishedEnergy(character, character.health)
                    initialStates["${pIdx}_${cIdx}"] = CharacterPlayState(
                        currentHealth = character.health,
                        currentEnergy = replenishedEnergy
                    )
                }
            }
            _state.update { it.copy(characterPlayStates = initialStates, currentTurn = 1, turnHistory = emptyList()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onEvent(event: CharacterEvent) {
        when (event) {
            is CharacterEvent.DeleteTroupe -> {
                viewModelScope.launch { dao.deleteTroupe(event.troupe) }
            }
            is CharacterEvent.EditTroupe -> {
                editingTroupeId = event.troupe.id
                newTroupeName = event.troupe.troupeName
                selectedTroupeFaction = event.troupe.faction
                selectedCharacterIds = event.troupe.characterIds.toSet()
                isTournamentList = event.troupe.isTournamentList
            }
            CharacterEvent.SaveTroupe -> {
                val troupe = Troupe(
                    id = editingTroupeId ?: 0,
                    troupeName = newTroupeName,
                    faction = selectedTroupeFaction,
                    characterIds = selectedCharacterIds.toList(),
                    shareCode = "",
                    isTournamentList = isTournamentList
                )
                viewModelScope.launch { 
                    val id = dao.upsertTroupe(troupe)
                    val savedTroupe = troupe.copy(id = id.toInt())
                    _uiEvent.emit(UiEvent.TroupeCreated(savedTroupe, pendingTroupePlayerIndex))
                    pendingTroupePlayerIndex = null
                }
                resetNewTroupeFields()
            }
            is CharacterEvent.SortCharacters -> {
                _state.update { it.copy(sortType = event.sortType) }
            }
            CharacterEvent.DismissError -> {
                _state.update { it.copy(errorMessage = null) }
            }
            is CharacterEvent.UpdateUserName -> {
                val oldName = _state.value.name
                _state.update { it.copy(name = event.name) }
                prefs.edit().putString("player_name", event.name).apply()
                
                if (event.name != oldName) {
                    val updateMsg = SessionMessage.PlayerInfoUpdate(persistentDeviceId, event.name)
                    nearbyManager.sendPayloadToAll(MessageParser.encode(updateMsg))
                }
            }
            is CharacterEvent.ChangeTheme -> {
                _state.update { it.copy(theme = event.theme) }
                prefs.edit().putString("app_theme", event.theme.name).apply()
            }
            is CharacterEvent.SetLocalModeDefault -> {
                _state.update { it.copy(useLocalModeByDefault = event.useLocal) }
                prefs.edit().putBoolean("use_local_mode_by_default", event.useLocal).apply()
            }
            is CharacterEvent.SetHasSeenTutorial -> {
                val prefKey = if (event.tutorialKey == "global") "has_seen_global_tutorial" else "has_seen_${event.tutorialKey}_tutorial"
                _state.update { 
                    if (event.tutorialKey == "global") {
                        it.copy(hasSeenGlobalTutorial = event.seen)
                    } else it
                }
                prefs.edit().putBoolean(prefKey, event.seen).apply()
            }
            
            CharacterEvent.RefreshNews -> {
                fetchNews()
            }

            is CharacterEvent.UpdateCharacterHealth -> {
                updateCharacterState(event.playerIndex, event.charIndex) { 
                    it.copy(currentHealth = event.health) 
                }
                broadcastGameplayUpdate(SessionMessage.GameplayUpdate(event.playerIndex, event.charIndex, health = event.health))
            }
            is CharacterEvent.UpdateCharacterEnergy -> {
                updateCharacterState(event.playerIndex, event.charIndex) { 
                    it.copy(currentEnergy = event.energy) 
                }
                broadcastGameplayUpdate(SessionMessage.GameplayUpdate(event.playerIndex, event.charIndex, energy = event.energy))
            }
            is CharacterEvent.ToggleAbilityUsed -> {
                updateCharacterState(event.playerIndex, event.charIndex) { 
                    val newAbilities = it.usedAbilities.toMutableMap()
                    newAbilities[event.abilityName] = event.used
                    it.copy(usedAbilities = newAbilities)
                }
                broadcastGameplayUpdate(SessionMessage.GameplayUpdate(event.playerIndex, event.charIndex, abilityName = event.abilityName, abilityUsed = event.used))
            }
            is CharacterEvent.ToggleCharacterFlipped -> {
                updateCharacterState(event.playerIndex, event.charIndex) { 
                    it.copy(isFlipped = event.flipped) 
                }
            }
            is CharacterEvent.ToggleCharacterExpanded -> {
                updateCharacterState(event.playerIndex, event.charIndex) { 
                    it.copy(isExpanded = event.expanded) 
                }
            }
            CharacterEvent.ResetGamePlayState -> {
                _state.update { it.copy(characterPlayStates = emptyMap(), currentTurn = 1, turnHistory = emptyList(), winnerName = null, isTie = false) }
            }
            CharacterEvent.NextTurn -> {
                handleReadyAction(GameAction.NEXT_TURN)
            }
            CharacterEvent.RewindTurn -> {
                handleReadyAction(GameAction.REWIND)
            }
            is CharacterEvent.UpdateCharacterMoonstones -> {
                updateCharacterState(event.playerIndex, event.charIndex) { 
                    it.copy(moonstones = event.stones) 
                }
                broadcastGameplayUpdate(SessionMessage.GameplayUpdate(event.playerIndex, event.charIndex, moonstones = event.stones))
            }
            CharacterEvent.AbandonGame -> {
                _state.update { it.copy(
                    activeTroupes = emptyList(),
                    characterPlayStates = emptyMap(),
                    currentTurn = 1,
                    gameSession = null,
                    turnHistory = emptyList(),
                    winnerName = null,
                    isTie = false
                )}
                nearbyManager.stopAll()
            }
            CharacterEvent.EndGame -> {
                handleReadyAction(GameAction.NEXT_TURN, forceEnd = true)
            }
            
            is CharacterEvent.CreateTournament -> {
                startHostingTournament(event.tournamentName, event.troupeSize, event.timer, event.hostParticipating, event.passcode)
            }
            else -> {}
        }
    }

    private fun updateCharacterState(playerIndex: Int, charIndex: Int, update: (CharacterPlayState) -> CharacterPlayState) {
        val key = "${playerIndex}_$charIndex"
        _state.update { currentState ->
            val currentPlayStates = currentState.characterPlayStates.toMutableMap()
            val charState = currentPlayStates[key] ?: CharacterPlayState(currentHealth = 0)
            currentPlayStates[key] = update(charState)
            currentState.copy(characterPlayStates = currentPlayStates)
        }
    }

    private fun calculateReplenishedEnergy(character: Character, currentHealth: Int): Int {
        if (currentHealth <= 0) return 0
        val thresholdsMet = character.energyTrack.count { currentHealth >= it }
        return thresholdsMet
    }

    private fun handleReadyAction(action: GameAction, forceEnd: Boolean = false) {
        val session = _state.value.gameSession ?: run {
            if (action == GameAction.NEXT_TURN) attemptNextTurn(forceEnd) else handleRewindTurn()
            return
        }

        val deviceId = persistentDeviceId
        val isReady = when(action) {
            GameAction.NEXT_TURN -> !_state.value.readyForNextTurn.contains(deviceId)
            GameAction.REWIND -> !_state.value.readyForRewind.contains(deviceId)
        }

        val readyMsg = SessionMessage.ReadyForAction(action, deviceId, isReady)
        nearbyManager.sendPayloadToAll(MessageParser.encode(readyMsg))
        handleSessionMessage("LOCAL", MessageParser.encode(readyMsg))
    }

    private fun attemptNextTurn(forceEnd: Boolean = false) {
        val currentState = _state.value
        val playersData = playersWithCharacters.value
        if (playersData.isEmpty()) return

        if (currentState.currentTurn >= 4 || forceEnd) {
            val playerStones = playersData.mapIndexed { pIdx, (troupe, characters) ->
                val total = characters.indices.sumOf { cIdx ->
                    currentState.characterPlayStates["${pIdx}_${cIdx}"]?.moonstones ?: 0
                }
                troupe.troupeName to total
            }

            val maxStones = playerStones.maxOf { it.second }
            val winners = playerStones.mapIndexedNotNull { index, pair -> if (pair.second == maxStones) index else null }

            if (winners.size == 1 || forceEnd) {
                if (winners.size == 1) {
                    val winnerIdx = winners[0]
                    _state.update { it.copy(winnerName = playerStones[winnerIdx].first) }
                    saveGameResult(winnerIdx)
                } else {
                    _state.update { it.copy(isTie = true) }
                    saveGameResult(null)
                }
                broadcastTurnUpdate(_state.value.currentTurn, _state.value.characterPlayStates)
                return
            } else if (currentState.currentTurn == 5) {
                _state.update { it.copy(isTie = true) }
                saveGameResult(null)
                broadcastTurnUpdate(_state.value.currentTurn, _state.value.characterPlayStates)
                return
            }
        }

        handleNextTurn()
    }

    private fun saveGameResult(winnerIndex: Int?) {
        val currentState = _state.value
        val playersData = playersWithCharacters.value
        if (playersData.isEmpty()) return

        viewModelScope.launch {
            val session = currentState.gameSession
            val playerStats = playersData.mapIndexed { pIdx, (troupe, characters) ->
                val charStats = characters.mapIndexed { cIdx, character ->
                    val playState = currentState.characterPlayStates["${pIdx}_${cIdx}"]
                    CharacterGameStat(
                        characterId = character.id,
                        name = character.name,
                        stones = playState?.moonstones ?: 0,
                        died = (playState?.currentHealth ?: 0) <= 0
                    )
                }
                
                val pName = if (session != null) {
                    session.players.getOrNull(pIdx)?.name
                } else {
                    if (pIdx == 0) currentState.name.ifEmpty { null } else "Player ${pIdx + 1}"
                }

                PlayerStat(
                    playerName = pName,
                    troupeName = troupe.troupeName,
                    faction = troupe.faction,
                    totalStones = charStats.sumOf { it.stones },
                    characterStats = charStats
                )
            }

            val gameResult = GameResult(
                timestamp = System.currentTimeMillis(),
                playerStats = playerStats,
                winnerIndex = winnerIndex
            )
            dao.upsertGameResult(gameResult)
        }
    }

    private fun handleNextTurn() {
        val currentState = _state.value
        val playersData = playersWithCharacters.value
        if (playersData.isEmpty()) return

        val updatedHistory = currentState.turnHistory + listOf(currentState.characterPlayStates)
        val newPlayStates = currentState.characterPlayStates.toMutableMap()
        
        playersData.forEachIndexed { pIdx, (_, characters) ->
            characters.forEachIndexed { cIdx, character ->
                val key = "${pIdx}_$cIdx"
                val playState = newPlayStates[key]
                if (playState != null && playState.currentHealth > 0) {
                    val replenishedEnergy = calculateReplenishedEnergy(character, playState.currentHealth)
                    newPlayStates[key] = playState.copy(
                        currentEnergy = replenishedEnergy,
                        usedAbilities = emptyMap()
                    )
                }
            }
        }

        _state.update { it.copy(
            characterPlayStates = newPlayStates,
            currentTurn = it.currentTurn + 1,
            turnHistory = updatedHistory,
            readyForNextTurn = emptySet(),
            readyForRewind = emptySet()
        ) }

        broadcastTurnUpdate(_state.value.currentTurn, newPlayStates)
    }

    private fun handleRewindTurn() {
        _state.update { currentState ->
            if (currentState.turnHistory.isEmpty()) return@update currentState
            
            val previousStates = currentState.turnHistory.last()
            val newHistory = currentState.turnHistory.dropLast(1)
            
            currentState.copy(
                characterPlayStates = previousStates,
                currentTurn = (currentState.currentTurn - 1).coerceAtLeast(1),
                turnHistory = newHistory,
                readyForNextTurn = emptySet(),
                readyForRewind = emptySet(),
                winnerName = null,
                isTie = false
            )
        }
        
        broadcastTurnUpdate(_state.value.currentTurn, _state.value.characterPlayStates)
    }

    fun startNewGame(troupes: List<Troupe>) {
        _state.update { it.copy(
            characterPlayStates = emptyMap(), 
            currentTurn = 1,
            activeTroupes = troupes,
            turnHistory = emptyList(),
            readyForNextTurn = emptySet(),
            readyForRewind = emptySet(),
            winnerName = null,
            isTie = false
        ) }
    }

    fun saveTroupe(troupe: Troupe) {
        viewModelScope.launch {
            dao.upsertTroupe(troupe.copy(id = 0))
        }
    }

    fun onTroupeScanned(playerIndex: Int, troupe: Troupe) {
        viewModelScope.launch {
            _scannedTroupeEvent.emit(playerIndex to troupe)
        }
    }

    fun resetNewTroupeFields(isTournament: Boolean = false) {
        editingTroupeId = null
        newTroupeName = ""
        selectedTroupeFaction = Faction.COMMONWEALTH
        selectedCharacterIds = emptySet()
        isTournamentList = isTournament
    }

    fun generateFullShareCode(troupe: Troupe, characters: List<Character>): String {
        val factionCode = when (troupe.faction) {
            Faction.COMMONWEALTH -> "A"
            Faction.DOMINION -> "B"
            Faction.LESHAVULT -> "C"
            Faction.SHADES -> "D"
        }
        val selectedCodes = troupe.characterIds.mapNotNull { id ->
            characters.find { it.id == id }?.shareCode
        }.joinToString("")
        val isTournamentFlag = if (troupe.isTournamentList) "1" else "0"
        val rawCode = "${troupe.troupeName}|$factionCode$isTournamentFlag$selectedCodes"
        return Base64.encodeToString(rawCode.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun importTroupe(fullCode: String, allCharacters: List<Character>): Troupe? {
        try {
            val decodedBytes = Base64.decode(fullCode, Base64.DEFAULT)
            val decodedString = String(decodedBytes, Charsets.UTF_8)
            val parts = decodedString.split("|")
            if (parts.size != 2 || parts[1].isEmpty()) return null
            
            val name = parts[0]
            val codeBody = parts[1]
            val faction = when (codeBody[0]) {
                'A' -> Faction.COMMONWEALTH
                'B' -> Faction.DOMINION
                'C' -> Faction.LESHAVULT
                'D' -> Faction.SHADES
                else -> return null
            }
            val autoSelect = codeBody[1] == '1'
            val characterCodes = codeBody.substring(2).chunked(3)

            val characterIds = characterCodes.mapNotNull { code ->
                allCharacters.find { it.shareCode == code }?.id
            }
            
            return Troupe(0, name, faction, characterIds, fullCode, isTournamentList = autoSelect)
        } catch (e: Exception) {
            return null
        }
    }

    // --- Nearby Session Logic ---

    fun startHosting(hostName: String) {
        nearbyManager.stopAll()
        val sessionId = UUID.randomUUID().toString().take(8)
        val actualName = _state.value.name.ifEmpty { hostName }
        _state.update { it.copy(
            gameSession = GameSession(
                players = listOf(GamePlayer(name = actualName, deviceId = persistentDeviceId)),
                isHost = true,
                sessionId = sessionId
            )
        )}
        nearbyManager.startAdvertising(actualName)
    }

    fun startHostingTournament(tournamentName: String, troupeSize: TroupeSizeSetting, timer: Int, hostParticipating: Boolean, passcode: String) {
        nearbyManager.stopAll()
        val sessionId = UUID.randomUUID().toString().take(8)
        val actualName = _state.value.name.ifEmpty { "Host" }
        _state.update { it.copy(
            isTournamentHost = true,
            tournamentSettings = TournamentSettings(
                tournamentName = tournamentName,
                troupeSize = troupeSize,
                roundTimerMinutes = timer,
                hostParticipating = hostParticipating,
                sessionId = sessionId,
                passcode = passcode
            ),
            tournamentPlayers = if (hostParticipating) listOf(TournamentPlayer(name = actualName, deviceId = persistentDeviceId)) else emptyList()
        )}
        nearbyManager.startAdvertising(tournamentName)
    }

    fun startDiscovering() {
        nearbyManager.startDiscovery()
    }

    private var joiningTournamentEndpointId: String? = null

    fun requestTournamentJoin(endpointId: String, passcode: String) {
        joiningTournamentEndpointId = endpointId
        val joinMsg = SessionMessage.JoinRequest(
            playerName = _state.value.name.ifEmpty { "Player" },
            deviceId = persistentDeviceId,
            tournamentPasscode = passcode
        )
        nearbyManager.requestConnection(_state.value.name.ifEmpty { "Player" }, endpointId)
        
        nearbyManager.setConnectionListener { connectedEndpointId ->
            if (connectedEndpointId == endpointId) {
                nearbyManager.sendPayload(endpointId, MessageParser.encode(joinMsg))
            }
        }
    }

    private fun handleSessionMessage(endpointId: String, jsonString: String) {
        val message = try { MessageParser.decode(jsonString) } catch (e: Exception) { return }
        
        if (message is SessionMessage.SessionSync) {
            val newSession = GameSession(
                players = message.players,
                isHost = false,
                sessionId = message.sessionId
            )
            _state.update { it.copy(gameSession = newSession) }
            return
        }

        if (message is SessionMessage.TournamentSync) {
            val currentState = _state.value
            if (currentState.isLeaving) return

            val shouldTriggerJoin = joiningTournamentEndpointId == endpointId
            
            _state.update { it.copy(
                tournamentSettings = message.settings,
                tournamentPlayers = message.players,
                isTournamentHost = false,
                currentTournamentRound = message.currentRound,
                tournamentHistory = message.history
            ) }
            
            if (shouldTriggerJoin) {
                joiningTournamentEndpointId = null
                viewModelScope.launch { _uiEvent.emit(UiEvent.TournamentJoined) }
            }
            return
        }

        val currentState = _state.value
        val isTournamentHost = currentState.isTournamentHost

        when (message) {
            is SessionMessage.JoinRequest -> {
                val currentSession = currentState.gameSession 
                if (currentSession != null && currentSession.isHost) {
                    val existingPlayerIndex = currentSession.players.indexOfFirst { it.deviceId == message.deviceId }
                    
                    if (existingPlayerIndex != -1) {
                        syncSessionToAll()
                    } else if (currentSession.players.size < 4) {
                        val newPlayer = GamePlayer(name = message.playerName, deviceId = message.deviceId)
                        _state.update { it.copy(
                            gameSession = currentSession.copy(players = currentSession.players + newPlayer)
                        )}
                        syncSessionToAll()
                    }
                } else if (isTournamentHost) {
                    val settings = currentState.tournamentSettings
                    if (settings != null && settings.passcode == message.tournamentPasscode) {
                        val newPlayer = TournamentPlayer(name = message.playerName, deviceId = message.deviceId)
                        _state.update { it.copy(
                            tournamentPlayers = it.tournamentPlayers + newPlayer
                        )}
                        syncTournamentToAll()
                    }
                }
            }
            is SessionMessage.PlayerInfoUpdate -> {
                if (isTournamentHost) {
                    _state.update { state ->
                        val updatedPlayers = state.tournamentPlayers.map { player ->
                            if (player.deviceId == message.deviceId) {
                                player.copy(name = message.newName)
                            } else player
                        }
                        state.copy(tournamentPlayers = updatedPlayers)
                    }
                    syncTournamentToAll()
                } else if (currentState.gameSession?.isHost == true) {
                    val currentSession = currentState.gameSession
                    val updatedPlayers = currentSession.players.map { player ->
                        if (player.deviceId == message.deviceId) {
                            player.copy(name = message.newName)
                        } else player
                    }
                    _state.update { it.copy(gameSession = currentSession.copy(players = updatedPlayers)) }
                    syncSessionToAll()
                }
            }
            is SessionMessage.LeaveMessage -> {
                if (isTournamentHost) {
                    _state.update { state ->
                        val updatedPlayers = state.tournamentPlayers.filter { it.deviceId != message.deviceId }
                        state.copy(tournamentPlayers = updatedPlayers)
                    }
                    syncTournamentToAll()
                }
            }
            is SessionMessage.TroupeSelected -> {
                if (isTournamentHost) {
                    _state.update { state ->
                        val updatedPlayers = state.tournamentPlayers.map { player ->
                            if (player.deviceId == message.deviceId) {
                                player.copy(troupe = Troupe(0, message.troupeName, message.faction, message.characterIds, ""), isReady = false)
                            } else player
                        }
                        state.copy(tournamentPlayers = updatedPlayers)
                    }
                    syncTournamentToAll()
                } else if (currentState.gameSession?.isHost == true) {
                    val currentSession = currentState.gameSession
                    val updatedPlayers = currentSession.players.map { player ->
                        if (player.deviceId == message.deviceId) {
                            player.copy(troupe = Troupe(0, message.troupeName, message.faction, message.characterIds, ""))
                        } else player
                    }
                    _state.update { it.copy(gameSession = currentSession.copy(players = updatedPlayers)) }
                    syncSessionToAll()
                }
            }
            is SessionMessage.TournamentPlayerReady -> {
                if (isTournamentHost) {
                    _state.update { state ->
                        val updatedPlayers = state.tournamentPlayers.map { player ->
                            if (player.deviceId == message.deviceId) {
                                player.copy(isReady = message.isReady)
                            } else player
                        }
                        state.copy(tournamentPlayers = updatedPlayers)
                    }
                    syncTournamentToAll()
                }
            }
            is SessionMessage.TournamentDisbanded -> {
                viewModelScope.launch {
                    Toast.makeText(getApplication(), message.message, Toast.LENGTH_LONG).show()
                    _uiEvent.emit(UiEvent.TournamentDisbanded)
                    leaveSession()
                }
            }
            is SessionMessage.TournamentPairingUpdate -> {
                _state.update { currentState ->
                    val currentRound = currentState.currentTournamentRound ?: return@update currentState
                    val updatedPairings = currentRound.pairings.map { pairing ->
                        if (pairing.player1Id == message.pairing.player1Id && pairing.player2Id == message.pairing.player2Id) {
                            message.pairing
                        } else pairing
                    }
                    currentState.copy(currentTournamentRound = currentRound.copy(pairings = updatedPairings))
                }
            }
            is SessionMessage.StartGame -> {
                val troupes = currentState.gameSession?.players?.mapNotNull { it.troupe } ?: emptyList()
                if (troupes.isNotEmpty()) {
                    startNewGame(troupes)
                    viewModelScope.launch { _uiEvent.emit(UiEvent.GameStarted) }
                }
            }
            else -> {}
        }
    }

    private fun syncSessionToAll() {
        val session = _state.value.gameSession ?: return
        if (session.isHost) {
            val syncMsg = SessionMessage.SessionSync(session.players, session.sessionId)
            nearbyManager.sendPayloadToAll(MessageParser.encode(syncMsg))
        }
    }

    private fun syncTournamentToAll() {
        val settings = _state.value.tournamentSettings ?: return
        val players = _state.value.tournamentPlayers
        val currentRound = _state.value.currentTournamentRound
        val history = _state.value.tournamentHistory
        val syncMsg = SessionMessage.TournamentSync(settings, players, currentRound, history)
        nearbyManager.sendPayloadToAll(MessageParser.encode(syncMsg))
    }

    fun broadcastTroupeSelection(troupe: Troupe, targetDeviceId: String? = null) {
        val currentState = _state.value
        val deviceIdToUpdate = targetDeviceId ?: persistentDeviceId
        
        val msg = SessionMessage.TroupeSelected(
            deviceId = deviceIdToUpdate,
            troupeName = troupe.troupeName,
            faction = troupe.faction,
            characterIds = troupe.characterIds
        )
        val json = MessageParser.encode(message = msg)
        
        if (currentState.tournamentSettings != null) {
            val updatedPlayers = currentState.tournamentPlayers.map { 
                if (it.deviceId == deviceIdToUpdate) it.copy(troupe = troupe, isReady = deviceIdToUpdate.startsWith("manual_")) else it 
            }
            _state.update { it.copy(tournamentPlayers = updatedPlayers) }
            if (currentState.isTournamentHost) syncTournamentToAll() else nearbyManager.sendPayloadToAll(json)
        } else if (currentState.gameSession != null) {
            val session = currentState.gameSession
            val updatedPlayers = session.players.map { 
                if (it.deviceId == persistentDeviceId) it.copy(troupe = troupe) else it 
            }
            _state.update { it.copy(gameSession = session.copy(players = updatedPlayers)) }
            if (session.isHost) syncSessionToAll() else nearbyManager.sendPayloadToAll(json)
        }
    }

    fun toggleTournamentReady(isReady: Boolean) {
        val currentState = _state.value
        if (currentState.tournamentSettings == null) return

        val updatedPlayers = currentState.tournamentPlayers.map { 
            if (it.deviceId == persistentDeviceId) it.copy(isReady = isReady) else it 
        }
        _state.update { it.copy(tournamentPlayers = updatedPlayers) }

        val msg = SessionMessage.TournamentPlayerReady(persistentDeviceId, isReady)
        val json = MessageParser.encode(msg)
        
        if (currentState.isTournamentHost) {
            syncTournamentToAll()
        } else {
            nearbyManager.sendPayloadToAll(json)
        }
    }

    fun updateTournamentSettings(name: String, size: TroupeSizeSetting, timer: Int, hostParticipating: Boolean) {
        val currentState = _state.value
        val settings = currentState.tournamentSettings ?: return
        
        val newSettings = settings.copy(
            tournamentName = name,
            troupeSize = size,
            roundTimerMinutes = timer,
            hostParticipating = hostParticipating
        )
        
        // If host was not participating but now is, or vice versa, update player list
        val actualName = currentState.name.ifEmpty { "Host" }
        val updatedPlayers = if (hostParticipating && currentState.tournamentPlayers.none { it.deviceId == persistentDeviceId }) {
            currentState.tournamentPlayers + TournamentPlayer(name = actualName, deviceId = persistentDeviceId)
        } else if (!hostParticipating) {
            currentState.tournamentPlayers.filter { it.deviceId != persistentDeviceId }
        } else {
            currentState.tournamentPlayers
        }

        _state.update { it.copy(
            tournamentSettings = newSettings,
            tournamentPlayers = updatedPlayers
        ) }
        
        syncTournamentToAll()
    }

    fun startTournamentFirstRound() {
        val currentState = _state.value
        if (!currentState.isTournamentHost) return
        
        val players = currentState.tournamentPlayers.shuffled()
        val pairings = mutableListOf<TournamentPairing>()
        
        for (i in 0 until players.size step 2) {
            if (i + 1 < players.size) {
                pairings.add(TournamentPairing(players[i].deviceId, players[i + 1].deviceId))
            }
        }
        
        val firstRound = TournamentRound(roundNumber = 1, pairings = pairings, status = TournamentRoundStatus.SELECTION)
        _state.update { it.copy(currentTournamentRound = firstRound) }
        syncTournamentToAll()
    }

    fun addManualTournamentPlayer(name: String) {
        val currentState = _state.value
        if (!currentState.isTournamentHost) return
        
        val newPlayer = TournamentPlayer(
            name = name,
            deviceId = "manual_${UUID.randomUUID()}",
            isReady = true
        )
        
        _state.update { it.copy(
            tournamentPlayers = it.tournamentPlayers + newPlayer
        )}
        syncTournamentToAll()
    }

    fun updateManualPlayerName(deviceId: String, newName: String) {
        val currentState = _state.value
        if (!currentState.isTournamentHost || !deviceId.startsWith("manual_")) return
        
        val updatedPlayers = currentState.tournamentPlayers.map {
            if (it.deviceId == deviceId) it.copy(name = newName) else it
        }
        
        _state.update { it.copy(tournamentPlayers = updatedPlayers) }
        syncTournamentToAll()
    }

    fun removeTournamentPlayer(deviceId: String) {
        val currentState = _state.value
        if (!currentState.isTournamentHost) return
        
        val updatedPlayers = currentState.tournamentPlayers.filter { it.deviceId != deviceId }
        _state.update { it.copy(tournamentPlayers = updatedPlayers) }
        syncTournamentToAll()
    }

    fun confirmTournamentCharacterSelection(selectedIds: List<Int>, targetDeviceId: String? = null) {
        val currentState = _state.value
        val currentRound = currentState.currentTournamentRound ?: return
        val deviceIdToUpdate = targetDeviceId ?: persistentDeviceId
        
        val updatedPairings = currentRound.pairings.map { pairing ->
            if (pairing.player1Id == deviceIdToUpdate) {
                pairing.copy(player1CharacterIds = selectedIds, player1Confirmed = true)
            } else if (pairing.player2Id == deviceIdToUpdate) {
                pairing.copy(player2CharacterIds = selectedIds, player2Confirmed = true)
            } else pairing
        }
        
        val newRound = currentRound.copy(pairings = updatedPairings)
        _state.update { it.copy(currentTournamentRound = newRound) }
        
        // Broadcast the update
        val pairing = updatedPairings.find { it.player1Id == deviceIdToUpdate || it.player2Id == deviceIdToUpdate }
        if (pairing != null) {
            val msg = SessionMessage.TournamentPairingUpdate(pairing)
            nearbyManager.sendPayloadToAll(MessageParser.encode(msg))
            
            if (currentState.isTournamentHost) {
                syncTournamentToAll()
            }
        }
    }

    fun setTournamentInitiative(pairing: TournamentPairing, winnerId: String) {
        val currentState = _state.value
        val currentRound = currentState.currentTournamentRound ?: return
        
        val updatedPairings = currentRound.pairings.map { p ->
            if (p.player1Id == pairing.player1Id && p.player2Id == pairing.player2Id) {
                val p1Sel = if (persistentDeviceId == p.player1Id) winnerId else p.player1InitiativeSelection
                val p2Sel = if (persistentDeviceId == p.player2Id) winnerId else p.player2InitiativeSelection
                
                // If host is setting it for a manual player, or just setting it directly
                val isHostSettingManual = currentState.isTournamentHost && (p.player1Id.startsWith("manual_") || p.player2Id.startsWith("manual_"))
                
                if (isHostSettingManual) {
                    p.copy(
                        player1InitiativeSelection = winnerId,
                        player2InitiativeSelection = winnerId,
                        initiativePlayerId = winnerId
                    )
                } else {
                    val finalInitiativeId = if (p1Sel != null && p1Sel == p2Sel) p1Sel else null
                    p.copy(
                        player1InitiativeSelection = p1Sel,
                        player2InitiativeSelection = p2Sel,
                        initiativePlayerId = finalInitiativeId
                    )
                }
            } else p
        }
        
        val newRound = currentRound.copy(pairings = updatedPairings)
        _state.update { it.copy(currentTournamentRound = newRound) }
        
        val updatedPairing = updatedPairings.find { it.player1Id == pairing.player1Id && it.player2Id == pairing.player2Id }
        if (updatedPairing != null) {
            val msg = SessionMessage.TournamentPairingUpdate(updatedPairing)
            nearbyManager.sendPayloadToAll(MessageParser.encode(msg))
            
            if (currentState.isTournamentHost) {
                syncTournamentToAll()
            }
        }
    }

    fun confirmTournamentDeployment(pairing: TournamentPairing) {
        val currentState = _state.value
        val currentRound = currentState.currentTournamentRound ?: return
        
        val updatedPairings = currentRound.pairings.map { p ->
            if (p.player1Id == pairing.player1Id && p.player2Id == pairing.player2Id) {
                val isP1 = persistentDeviceId == pairing.player1Id
                val isP2 = persistentDeviceId == pairing.player2Id
                val isHost = currentState.isTournamentHost
                
                var p1Ready = p.player1DeploymentReady
                var p2Ready = p.player2DeploymentReady
                
                if (isP1) p1Ready = true
                if (isP2) p2Ready = true
                
                // Host confirms for manual players
                if (isHost) {
                    if (p.player1Id.startsWith("manual_")) p1Ready = true
                    if (p.player2Id.startsWith("manual_")) p2Ready = true
                }
                
                p.copy(player1DeploymentReady = p1Ready, player2DeploymentReady = p2Ready)
            } else p
        }
        
        val newRound = currentRound.copy(pairings = updatedPairings)
        _state.update { it.copy(currentTournamentRound = newRound) }
        
        val updatedPairing = updatedPairings.find { it.player1Id == pairing.player1Id && it.player2Id == pairing.player2Id }
        if (updatedPairing != null) {
            val msg = SessionMessage.TournamentPairingUpdate(updatedPairing)
            nearbyManager.sendPayloadToAll(MessageParser.encode(msg))
            
            if (currentState.isTournamentHost) {
                syncTournamentToAll()
            }
        }
    }

    fun startTournamentActiveGames() {
        val currentState = _state.value
        if (!currentState.isTournamentHost) return
        
        val currentRound = currentState.currentTournamentRound ?: return
        _state.update { it.copy(currentTournamentRound = currentRound.copy(status = TournamentRoundStatus.ACTIVE_GAME)) }
        syncTournamentToAll()
    }

    fun disbandTournament() {
        val msg = SessionMessage.TournamentDisbanded("Tournament has been cancelled, please speak with your TO")
        nearbyManager.sendPayloadToAll(MessageParser.encode(msg))
        
        // Brief delay to ensure message is sent
        viewModelScope.launch {
            delay(300)
            _uiEvent.emit(UiEvent.TournamentDisbanded)
            leaveSession()
        }
    }

    fun leaveSession() {
        viewModelScope.launch {
            _state.update { it.copy(isLeaving = true) }
            val currentState = _state.value
            if (currentState.tournamentSettings != null && !currentState.isTournamentHost) {
                val leaveMsg = SessionMessage.LeaveMessage(persistentDeviceId)
                nearbyManager.sendPayloadToAll(MessageParser.encode(leaveMsg))
            }
            nearbyManager.stopAll()
            joiningTournamentEndpointId = null
            
            // Keep isLeaving true for a bit to swallow ghost messages
            delay(500)
            
            _state.update { it.copy(
                gameSession = null, 
                tournamentSettings = null, 
                isTournamentHost = false, 
                tournamentPlayers = emptyList(),
                activeTroupes = emptyList(),
                characterPlayStates = emptyMap(),
                isLeaving = false,
                currentTournamentRound = null,
                tournamentHistory = emptyList()
            ) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        nearbyManager.stopAll()
    }
    
    fun broadcastGameplayUpdate(update: SessionMessage.GameplayUpdate) {
        nearbyManager.sendPayloadToAll(MessageParser.encode(update))
    }
    fun broadcastTurnUpdate(turn: Int, states: Map<String, CharacterPlayState>) {
        nearbyManager.sendPayloadToAll(MessageParser.encode(SessionMessage.TurnUpdate(turn, states)))
    }
    fun broadcastStartGame() {
        nearbyManager.sendPayloadToAll(MessageParser.encode(SessionMessage.StartGame))
    }
}
