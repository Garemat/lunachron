package com.garemat.moonstone_companion

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
    fun fromActiveAbilityList(value: List<ActiveAbility>) = json.encodeToString(value)

    @TypeConverter
    fun toActiveAbilityList(value: String) = json.decodeFromString<List<ActiveAbility>>(value)

    @TypeConverter
    fun fromArcaneAbilityList(value: List<ArcaneAbility>) = json.encodeToString(value)

    @TypeConverter
    fun toArcaneAbilityList(value: String) = json.decodeFromString<List<ArcaneAbility>>(value)

    @TypeConverter
    fun fromSignatureMove(value: SignatureMove) = json.encodeToString(value)

    @TypeConverter
    fun toSignatureMove(value: String) = json.decodeFromString<SignatureMove>(value)

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
}
