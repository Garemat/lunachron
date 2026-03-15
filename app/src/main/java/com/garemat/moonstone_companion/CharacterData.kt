package com.garemat.moonstone_companion

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

object CharacterData {
    /** The highest schema major version this app build can parse.
     *  When the data repo bumps from 0.x.x → 1.0.0 this must be incremented
     *  alongside the app update that supports the new schema. */
    const val SUPPORTED_SCHEMA = 0

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun readCompendium(context: Context): CompendiumData =
        json.decodeFromString(readCompendiumText(context))

    /** Installed version read directly from the compendium file — prefs-independent. */
    fun getInstalledVersion(context: Context): String =
        try { readCompendium(context).version } catch (_: Exception) { "0.0.0" }

    /** True if [version]'s major component exceeds [SUPPORTED_SCHEMA]. */
    fun isSchemaCompatible(version: String): Boolean {
        val major = version.removePrefix("v").split(".").firstOrNull()?.toIntOrNull() ?: 0
        return major <= SUPPORTED_SCHEMA
    }

    /** True if [candidate] is strictly newer than [installed] by semver rules. */
    fun isNewer(candidate: String, installed: String): Boolean {
        val c = toTuple(candidate)
        val i = toTuple(installed)
        for (idx in c.indices) {
            if (c[idx] != i[idx]) return c[idx] > i[idx]
        }
        return false
    }

    private fun toTuple(version: String): List<Int> =
        version.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
            .let { parts -> List(3) { parts.getOrElse(it) { 0 } } }

    private fun readCompendiumText(context: Context): String {
        val internal = File(context.filesDir, "data/compendium.json")
        return if (internal.exists()) internal.readText()
               else context.assets.open("compendium.json").bufferedReader().readText()
    }
}
