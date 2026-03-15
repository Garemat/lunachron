package io.github.garemat.lunachron

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

// Kept for any fields that may still receive int or string from legacy JSON
object IntOrStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("IntOrString", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    override fun deserialize(decoder: Decoder): String {
        val input = decoder as? JsonDecoder ?: return decoder.decodeString()
        val element = input.decodeJsonElement()
        return if (element is JsonPrimitive) element.content else ""
    }
}

@Serializable
enum class Faction {
    COMMONWEALTH, DOMINION, LESHAVULT, SHADES
}

// ── Ability model ─────────────────────────────────────────────────────────────

@Serializable
data class ValidCard(
    val colour: String,
    val value: String
)

@Serializable
data class ArcaneOutcome(
    val text: String,
    val validCards: List<ValidCard>
)

/** Single unified ability class covering Passive, Active, and Arcane. */
@Serializable
data class Ability(
    val name: String,
    val description: String = "",
    val abilityType: String,         // "Passive" | "Active" | "Arcane"
    val energyCost: Int? = null,     // null for Passive
    val range: String? = null,       // null for Passive / self-targeting
    val pulse: Boolean? = null,      // null for Passive
    val oncePerTurn: Boolean = false,
    val oncePerGame: Boolean = false,
    val arcaneOutcomes: List<ArcaneOutcome> = emptyList()
)

// ── Signature move model ───────────────────────────────────────────────────────

@Serializable
data class SigMoveEntry(
    val deal: String,               // "Null" | "0".."4"
    val isFollowUp: Boolean = false // true = amber circle in PDF table
)

@Serializable
data class SignatureMove(
    val name: String,
    val upgradeFor: String = "",
    val possibleDamageTypes: List<String> = emptyList(),
    val highGuard: SigMoveEntry    = SigMoveEntry("Null"),
    val fallingSwing: SigMoveEntry = SigMoveEntry("Null"),
    val thrust: SigMoveEntry       = SigMoveEntry("Null"),
    val sweepingCut: SigMoveEntry  = SigMoveEntry("Null"),
    val risingAttack: SigMoveEntry = SigMoveEntry("Null"),
    val lowGuard: SigMoveEntry     = SigMoveEntry("Null"),
    val extraText: String = "",
    val endStepEffect: String = ""
)

/** Returns the six melee positions as an ordered list for display iteration. */
fun SignatureMove.positionEntries(): List<Pair<String, SigMoveEntry>> = listOf(
    "High Guard"    to highGuard,
    "Falling Swing" to fallingSwing,
    "Thrust"        to thrust,
    "Sweeping Cut"  to sweepingCut,
    "Rising Attack" to risingAttack,
    "Low Guard"     to lowGuard
)

// ── Character entity ───────────────────────────────────────────────────────────

@Entity
@Serializable
data class Character(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val version: Int = 1,
    val name: String = "",
    val factions: List<Faction>,
    val keywords: List<String>,
    val melee: Int = 0,
    val meleeRange: Int = 0,
    val arcane: Int = 0,
    val evade: Int = 0,
    val health: Int = 0,
    val energyTrack: List<Int>,
    val signatureMove: SignatureMove? = null,
    val abilities: List<Ability> = emptyList(),
    val baseSize: String = "30mm",
    val imageName: String?,
    val shareCode: String = "AAA",

    // null = passive ability forces that damage type to {0}
    val slicingDamageBuff: Int? = 0,
    val piercingDamageBuff: Int? = 0,
    val impactDamageBuff: Int? = 0,
    val dealsMagicalDamage: Boolean = false,

    val allDamageMitigation: Int = 0,
    val piercingDamageMitigation: Int = 0,
    val impactDamageMitigation: Int = 0,
    val slicingDamageMitigation: Int = 0,
    val magicalDamageMitigation: Int = 0,

    val isUnselectableInTroupe: Boolean = false,
    val summonsCharacterIds: List<Int> = emptyList(),
    val transformsInto: Int? = null,
    val poolResources: List<PoolResourceDefinition> = emptyList()
)

@Serializable
data class PoolResourceDefinition(
    val name: String,
    val maxInPool: Int = 1,
    val maxPerCharacter: Int = 1
)

// ── Kept for UpgradeCard (which still uses a simple passive-only ability list) ──

@Serializable
data class PassiveAbility(
    val name: String,
    val description: String,
    val oncePerTurn: Boolean = false,
    val oncePerGame: Boolean = false
)

// ── Compendium wrapper ─────────────────────────────────────────────────────────

@Serializable
data class CompendiumData(
    val version: String,
    val characters: List<Character> = emptyList(),
    val upgrades: List<UpgradeCard> = emptyList(),
    val campaign: List<CampaignCard> = emptyList()
)

// ── Other game entities ────────────────────────────────────────────────────────

@Entity
@Serializable
data class UpgradeCard(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val factions: List<Faction>? = null,
    val allowedKeywords: List<String> = emptyList(),
    val restrictedKeywords: List<String> = emptyList(),
    val abilities: List<PassiveAbility>,
    val shareCode: String,
    val imageName: String? = null
)

@Entity
@Serializable
data class CampaignCard(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val timing: String,
    val factions: List<Faction>? = null,
    val description: String,
    val extraDescription: String? = null,
    val shareCode: String,
    val imageName: String? = null
)

@Serializable
data class TroupeCampaignCard(
    val cardId: Int,
    val used: Boolean = false
)

@Entity
@Serializable
data class Troupe(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val troupeName: String,
    val faction: Faction,
    val characterIds: List<Int>,
    val shareCode: String,
    val isTournamentList: Boolean = false,
    val isCampaignTroupe: Boolean = false,
    val victoryPoints: Int = 0,
    val equippedUpgrades: Map<Int, List<Int>> = emptyMap(),
    val campaignCards: List<TroupeCampaignCard> = emptyList()
)

@Entity
@Serializable
data class Campaign(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val description: String,
    val players: List<CampaignPlayer>,
    val currentRound: Int = 1,
    val rounds: List<CampaignRound> = emptyList(),
    val attacksEnabled: Boolean = false,
    val machinationPhaseActive: Boolean = false,
    val machinationDraft: Map<String, PlayerMachinationDraft>? = null
)

@Serializable
data class CampaignPlayer(
    val id: String,
    val name: String,
    val troupeId: Int,
    val machinationPoints: Int = 0,
    val attackPoints: Int = 2,
    val campaignCardDraw: Int = 2
)

@Serializable
data class CampaignRound(
    val roundNumber: Int,
    val games: List<CampaignGame>,
    val machinations: List<CampaignMachination> = emptyList(),
    val attacks: List<CampaignAttack> = emptyList(),
    val skipPlayerIds: List<String> = emptyList(),
    val mpDeltas: Map<String, Int> = emptyMap()
)

@Serializable
data class CampaignGame(
    val playerIds: List<String>,
    val winnerId: String? = null,
    val isPlayed: Boolean = false
)

@Serializable
enum class MachinationType {
    SUPPORT, SABOTAGE
}

@Serializable
data class CampaignMachination(
    val sourcePlayerId: String,
    val targetPlayerId: String,
    val type: MachinationType
)

@Serializable
enum class AttackType(val cost: Int) {
    ASSAULT(1), ABDUCTION(2)
}

@Serializable
data class CampaignAttack(
    val sourcePlayerId: String,
    val targetPlayerId: String,
    val targetCharacterId: Int,
    val type: AttackType
)

@Serializable
data class PlayerMachinationDraft(
    val machType1: MachinationType? = null,
    val machType2: MachinationType? = null,
    val target1: String = "",
    val target2: String = "",
    val isAttacking: Boolean = false,
    val attackType: AttackType = AttackType.ASSAULT,
    val attackTargetPlayerId: String = "",
    val attackTargetCharId: Int = -1
)

@Entity
@Serializable
data class GameResult(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,
    val playerStats: List<PlayerStat>,
    val winnerIndex: Int?
)

@Serializable
data class PlayerStat(
    val playerName: String?,
    val troupeName: String,
    val faction: Faction,
    val totalStones: Int,
    val characterStats: List<CharacterGameStat>
)

@Serializable
data class CharacterGameStat(
    val characterId: Int,
    val name: String,
    val stones: Int,
    val died: Boolean
)
