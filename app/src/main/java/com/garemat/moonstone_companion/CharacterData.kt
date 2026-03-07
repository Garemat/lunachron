package com.garemat.moonstone_companion

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json

object CharacterData {
    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true 
    }

    fun getCharactersFromAssets(context: Context): List<Character> {
        val characters = mutableListOf<Character>()
        try {
            val files = context.assets.list("characters") ?: return emptyList()
            for (fileName in files) {
                if (fileName.endsWith(".json")) {
                    try {
                        val jsonString = context.assets.open("characters/$fileName").bufferedReader().use { it.readText() }
                        characters.add(json.decodeFromString<Character>(jsonString))
                    } catch (e: Exception) {
                        Log.e("CharacterData", "Failed to parse character file: $fileName", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CharacterData", "Error listing character assets", e)
        }
        return characters
    }

    fun getUpgradesFromAssets(context: Context): List<UpgradeCard> {
        val upgrades = mutableListOf<UpgradeCard>()
        try {
            val files = context.assets.list("upgrades") ?: return emptyList()
            for (fileName in files) {
                if (fileName.endsWith(".json")) {
                    try {
                        val jsonString = context.assets.open("upgrades/$fileName").bufferedReader().use { it.readText() }
                        upgrades.add(json.decodeFromString<UpgradeCard>(jsonString))
                    } catch (e: Exception) {
                        Log.e("CharacterData", "Failed to parse upgrade file: $fileName", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CharacterData", "Error listing upgrade assets", e)
        }
        return upgrades
    }

    fun getCampaignCardsFromAssets(context: Context): List<CampaignCard> {
        val cards = mutableListOf<CampaignCard>()
        try {
            val files = context.assets.list("campaign") ?: return emptyList()
            for (fileName in files) {
                if (fileName.endsWith(".json")) {
                    try {
                        val jsonString = context.assets.open("campaign/$fileName").bufferedReader().use { it.readText() }
                        cards.add(json.decodeFromString<CampaignCard>(jsonString))
                    } catch (e: Exception) {
                        Log.e("CharacterData", "Failed to parse campaign card file: $fileName", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CharacterData", "Error listing campaign card assets", e)
        }
        return cards
    }
}
