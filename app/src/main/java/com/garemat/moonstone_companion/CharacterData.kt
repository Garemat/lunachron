package com.garemat.moonstone_companion

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

object CharacterData {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun getCharactersFromAssets(context: Context): List<Character> =
        json.decodeFromString(readDataFile(context, "characters.json"))

    fun getUpgradesFromAssets(context: Context): List<UpgradeCard> =
        json.decodeFromString(readDataFile(context, "upgrades.json"))

    fun getCampaignCardsFromAssets(context: Context): List<CampaignCard> =
        json.decodeFromString(readDataFile(context, "campaign.json"))

    /**
     * Reads from internal storage (downloaded update) first; falls back to bundled asset.
     */
    private fun readDataFile(context: Context, name: String): String {
        val internal = File(context.filesDir, "data/$name")
        return if (internal.exists()) internal.readText()
               else context.assets.open(name).bufferedReader().readText()
    }
}
