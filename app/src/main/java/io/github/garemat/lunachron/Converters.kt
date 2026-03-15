package io.github.garemat.lunachron

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromFactionList(value: List<Faction>) = json.encodeToString(value)

    @TypeConverter
    fun toFactionList(value: String) = json.decodeFromString<List<Faction>>(value)

    @TypeConverter
    fun fromStringList(value: List<String>) = json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String) = json.decodeFromString<List<String>>(value)

    @TypeConverter
    fun fromAbilityList(value: List<Ability>) = json.encodeToString(value)

    @TypeConverter
    fun toAbilityList(value: String) = json.decodeFromString<List<Ability>>(value)

    @TypeConverter
    fun fromSignatureMove(value: SignatureMove?): String =
        if (value == null) "" else json.encodeToString(value)

    @TypeConverter
    fun toSignatureMove(value: String): SignatureMove? =
        if (value.isEmpty()) null else json.decodeFromString(value)

    @TypeConverter
    fun fromIntList(value: List<Int>) = json.encodeToString(value)

    @TypeConverter
    fun toIntList(value: String) = json.decodeFromString<List<Int>>(value)

    @TypeConverter
    fun fromFaction(value: Faction) = value.name

    @TypeConverter
    fun toFaction(value: String) = Faction.valueOf(value)

    @TypeConverter
    fun fromPassiveAbilityList(value: List<PassiveAbility>) = json.encodeToString(value)

    @TypeConverter
    fun toPassiveAbilityList(value: String) = json.decodeFromString<List<PassiveAbility>>(value)

    @TypeConverter
    fun fromPlayerStatList(value: List<PlayerStat>) = json.encodeToString(value)

    @TypeConverter
    fun toPlayerStatList(value: String) = json.decodeFromString<List<PlayerStat>>(value)

    @TypeConverter
    fun fromEquippedUpgrades(value: Map<Int, List<Int>>) = json.encodeToString(value)

    @TypeConverter
    fun toEquippedUpgrades(value: String) = json.decodeFromString<Map<Int, List<Int>>>(value)

    @TypeConverter
    fun fromTroupeCampaignCardList(value: List<TroupeCampaignCard>) = json.encodeToString(value)

    @TypeConverter
    fun toTroupeCampaignCardList(value: String) = json.decodeFromString<List<TroupeCampaignCard>>(value)

    @TypeConverter
    fun fromCampaignPlayerList(value: List<CampaignPlayer>) = json.encodeToString(value)

    @TypeConverter
    fun toCampaignPlayerList(value: String) = json.decodeFromString<List<CampaignPlayer>>(value)

    @TypeConverter
    fun fromCampaignRoundList(value: List<CampaignRound>) = json.encodeToString(value)

    @TypeConverter
    fun toCampaignRoundList(value: String) = json.decodeFromString<List<CampaignRound>>(value)

    @TypeConverter
    fun fromMachinationDraft(value: Map<String, PlayerMachinationDraft>?): String =
        if (value == null) "" else json.encodeToString(value)

    @TypeConverter
    fun toMachinationDraft(value: String): Map<String, PlayerMachinationDraft>? =
        if (value.isEmpty()) null else json.decodeFromString(value)

    @TypeConverter
    fun fromPoolResourceDefinitionList(value: List<PoolResourceDefinition>) = json.encodeToString(value)

    @TypeConverter
    fun toPoolResourceDefinitionList(value: String) = json.decodeFromString<List<PoolResourceDefinition>>(value)
}
