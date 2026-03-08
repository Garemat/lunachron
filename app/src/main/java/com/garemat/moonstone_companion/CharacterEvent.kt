package com.garemat.moonstone_companion

sealed interface CharacterEvent {
    data object SaveCharacter : CharacterEvent
    data class DeleteCharacter(val character: Character) : CharacterEvent
    
    data object SaveTroupe : CharacterEvent
    data class SaveTroupeWithMetadata(
        val victoryPoints: Int,
        val equippedUpgrades: Map<Int, List<Int>>,
        val campaignCards: List<TroupeCampaignCard>
    ) : CharacterEvent
    
    data class DeleteTroupe(val troupe: Troupe) : CharacterEvent
    data class EditTroupe(val troupe: Troupe) : CharacterEvent

    data class SortCharacters(val sortType: SortType) : CharacterEvent
    
    // UI visibility events
    data object ShowCharacterDialog : CharacterEvent
    data object HideCharacterDialog : CharacterEvent
    data object ShowTroupeDialog : CharacterEvent
    data object HideTroupeDialog : CharacterEvent

    data object DismissError : CharacterEvent

    data class UpdateUserName(val name: String) : CharacterEvent
    data class ChangeTheme(val theme: AppTheme) : CharacterEvent
    data class ChangeLayoutDensity(val density: LayoutDensity) : CharacterEvent
    data class SetLocalModeDefault(val useLocal: Boolean) : CharacterEvent
    data class SetSinglePlayerMode(val enabled: Boolean) : CharacterEvent
    
    // Tutorial
    data class SetHasSeenTutorial(val tutorialKey: String, val seen: Boolean) : CharacterEvent

    // News
    data object RefreshNews : CharacterEvent

    // Gameplay Events
    data class UpdateCharacterHealth(val playerIndex: Int, val charIndex: Int, val health: Int) : CharacterEvent
    data class UpdateCharacterEnergy(val playerIndex: Int, val charIndex: Int, val energy: Int) : CharacterEvent
    data class ToggleAbilityUsed(val playerIndex: Int, val charIndex: Int, val abilityName: String, val used: Boolean) : CharacterEvent
    data class ToggleCharacterFlipped(val playerIndex: Int, val charIndex: Int, val flipped: Boolean) : CharacterEvent
    data class ToggleCharacterExpanded(val playerIndex: Int, val charIndex: Int, val expanded: Boolean) : CharacterEvent
    data object ResetGamePlayState : CharacterEvent
    
    // Turn and Moonstone Events
    data object NextTurn : CharacterEvent
    data object RewindTurn : CharacterEvent
    data class UpdateCharacterMoonstones(val playerIndex: Int, val charIndex: Int, val stones: Int) : CharacterEvent

    // Game Lifecycle
    data object AbandonGame : CharacterEvent
    data object EndGame : CharacterEvent

    // Game View / Tracking
    data class ChangeGameTrackingMode(val mode: GameTrackingMode) : CharacterEvent
    data class ChangeGameLayoutMode(val mode: GameLayoutMode) : CharacterEvent
    data class AddSummonedCharacter(val playerIndex: Int, val characterId: Int, val summonedByCharacterId: Int?) : CharacterEvent
    data class RemoveSummonedCharacter(val playerIndex: Int, val characterId: Int) : CharacterEvent
    data class UpdatePoolResource(val playerIndex: Int, val resourceName: String, val count: Int) : CharacterEvent
    data class UpdateCharacterPoolResource(val playerIndex: Int, val charIndex: Int, val resourceName: String, val count: Int) : CharacterEvent

    // Tournament Events
    data class CreateTournament(val tournamentName: String, val troupeSize: TroupeSizeSetting, val timer: Int, val hostParticipating: Boolean, val passcode: String, val hostMode: HostMode = HostMode.WIFI_NSD) : CharacterEvent
    data class JoinTournament(val sessionId: String) : CharacterEvent
}
