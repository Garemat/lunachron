package com.garemat.moonstone_companion

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

object IntOrStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("IntOrString", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    override fun deserialize(decoder: Decoder): String {
        val input = decoder as? JsonDecoder ?: return decoder.decodeString()
        val element = input.decodeJsonElement()
        return if (element is JsonPrimitive) {
            element.content
        } else {
            ""
        }
    }
}

@Serializable
enum class Faction {
    COMMONWEALTH, DOMINION, LESHAVULT, SHADES
}

@Serializable
data class ActiveAbility(
    val name: String,
    val cost: Int,
    val range: String = "",
    val description: String = "",
    val oncePerTurn: Boolean = false,
    val oncePerGame: Boolean = false
)

@Serializable
data class ArcaneAbility(
    val name: String,
    val cost: Int,
    val range: String = "",
    val description: String = "",
    val oncePerTurn: Boolean = false,
    val oncePerGame: Boolean = false,
    val reloadable: Boolean = false
)

@Serializable
data class SignatureMove(
    val name: String,
    val upgradeFrom: String = "",
    val results: List<SignatureResultEntry> = emptyList(),
    val damageType: String? = null, // Slicing, Piercing, Impact, Magical
    val passiveEffect: String? = null,
    val endStepEffect: String? = null
)

@Serializable
data class SignatureResultEntry(
    val opponentPlay: String,
    val deal: String,
    val isFollowUp: Boolean = false
)

@Entity
@Serializable
data class Character(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val version: String = "",
    val name: String = "",
    val factions: List<Faction>,
    val tags: List<String>,
    val melee: Int = 0,
    val meleeRange: Int = 0,
    val arcane: Int = 0,
    @Serializable(with = IntOrStringSerializer::class)
    val evade: String = "0",
    val health: Int = 0,
    val energyTrack: List<Int>,
    val passiveAbilities: List<PassiveAbility> = emptyList(),
    val activeAbilities: List<ActiveAbility> = emptyList(),
    val arcaneAbilities: List<ArcaneAbility> = emptyList(),
    val signatureMove: SignatureMove = SignatureMove(""),
    val baseSize: String = "30mm",
    val imageName: String?,
    val shareCode: String = "AAA",

    @Serializable(with = IntOrStringSerializer::class)
    val impactDamageBuff: String = "0",
    @Serializable(with = IntOrStringSerializer::class)
    val slicingDamageBuff: String = "0",
    @Serializable(with = IntOrStringSerializer::class)
    val piercingDamageBuff: String = "0",
    val dealsMagicalDamage: Boolean = false,
    
    @Serializable(with = IntOrStringSerializer::class)
    val impactDamageMitigation: String = "0",
    @Serializable(with = IntOrStringSerializer::class)
    val slicingDamageMitigation: String = "0",
    @Serializable(with = IntOrStringSerializer::class)
    val piercingDamageMitigation: String = "0",
    @Serializable(with = IntOrStringSerializer::class)
    val allDamageMitigation: String = "0",
    @Serializable(with = IntOrStringSerializer::class)
    val magicalDamageMitigation: String = "0",

    // Future-proofing for character-specific interactions
    val isUnselectableInTroupe: Boolean = false,
    val summonsCharacterIds: List<Int> = emptyList()
)

@Serializable
data class PassiveAbility(
    val name: String,
    val description: String,
    val oncePerTurn: Boolean = false,
    val oncePerGame: Boolean = false
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
    val isTournamentList: Boolean = false
)

@Entity
@Serializable
data class GameResult(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,
    val playerStats: List<PlayerStat>,
    val winnerIndex: Int? // null if tie
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
