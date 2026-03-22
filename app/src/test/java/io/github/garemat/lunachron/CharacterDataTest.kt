package io.github.garemat.lunachron

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CharacterData] version-comparison utilities.
 *
 * These functions control whether the app prompts users to install data updates
 * and whether a downloaded data release is schema-compatible with this app build.
 * Getting them wrong silently breaks data updates or causes crashes on launch.
 */
class CharacterDataTest {

    // ─── isNewer ─────────────────────────────────────────────────────────────

    @Test fun isNewer_patchIncrement() =
        assertTrue(CharacterData.isNewer("0.1.1", "0.1.0"))

    @Test fun isNewer_minorIncrement() =
        assertTrue(CharacterData.isNewer("0.2.0", "0.1.9"))

    @Test fun isNewer_majorIncrement() =
        assertTrue(CharacterData.isNewer("1.0.0", "0.9.9"))

    @Test fun isNewer_equalVersions_returnsFalse() =
        assertFalse(CharacterData.isNewer("0.1.0", "0.1.0"))

    @Test fun isNewer_olderCandidate_returnsFalse() =
        assertFalse(CharacterData.isNewer("0.0.9", "0.1.0"))

    @Test fun isNewer_candidateHasVPrefix() =
        assertTrue(CharacterData.isNewer("v0.2.0", "0.1.0"))

    @Test fun isNewer_bothHaveVPrefix() =
        assertTrue(CharacterData.isNewer("v0.2.0", "v0.1.0"))

    @Test fun isNewer_equalVersionsBothVPrefix_returnsFalse() =
        assertFalse(CharacterData.isNewer("v0.1.0", "v0.1.0"))

    @Test fun isNewer_multiDigitComponents() =
        assertTrue(CharacterData.isNewer("0.10.0", "0.9.0"))

    @Test fun isNewer_onlyPatchDiffers_olderInstalled() =
        assertTrue(CharacterData.isNewer("0.1.2", "0.1.1"))

    // ─── isSchemaCompatible ───────────────────────────────────────────────────

    @Test fun isSchemaCompatible_majorZero_returnsTrue() =
        assertTrue(CharacterData.isSchemaCompatible("0.9.9"))

    @Test fun isSchemaCompatible_majorOne_returnsFalse() =
        assertFalse(CharacterData.isSchemaCompatible("1.0.0"))

    @Test fun isSchemaCompatible_majorZeroWithVPrefix_returnsTrue() =
        assertTrue(CharacterData.isSchemaCompatible("v0.5.0"))

    @Test fun isSchemaCompatible_majorOneWithVPrefix_returnsFalse() =
        assertFalse(CharacterData.isSchemaCompatible("v1.0.0"))

    @Test fun isSchemaCompatible_emptyString_treatedAsZero_returnsTrue() =
        assertTrue(CharacterData.isSchemaCompatible(""))

    @Test fun isSchemaCompatible_nonNumericMajor_treatedAsZero_returnsTrue() =
        assertTrue(CharacterData.isSchemaCompatible("not-a-version"))

    @Test fun isSchemaCompatible_highMinorStillCompatible() =
        assertTrue(CharacterData.isSchemaCompatible("0.99.0"))
}
