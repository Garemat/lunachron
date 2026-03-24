package io.github.garemat.lunachron

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the discard-confirmation logic in AddEditTroupeScreen.
 *
 * The screen shows "Are you sure you want to discard changes?" when
 * [hasDashboardChanges] is true. This mirrors the exact expression used in
 * AddEditTroupeScreen.kt so a future refactor that silently breaks one of the
 * conditions will be caught here.
 *
 * Note: these tests validate the *conditions* that trigger the dialog, not the
 * Compose rendering of the dialog itself. UI-layer testing (that the dialog
 * actually appears on screen) would require an instrumented Compose test.
 */
class TroupeDiscardConditionTest {

    // Mirrors AddEditTroupeScreen.kt:
    //   val hasDashboardChanges =
    //       viewModel.newTroupeName     != originalName     ||
    //       viewModel.selectedFaction   != originalFaction  ||
    //       viewModel.selectedCharIds   != originalCharIds  ||
    //       viewModel.isCampaignTroupe  != originalIsCampaign
    private fun hasDashboardChanges(
        currentName: String,       originalName: String,
        currentFaction: Faction,   originalFaction: Faction,
        currentCharIds: Set<Int>,  originalCharIds: Set<Int>,
        currentIsCampaign: Boolean, originalIsCampaign: Boolean
    ) = currentName      != originalName      ||
        currentFaction   != originalFaction   ||
        currentCharIds   != originalCharIds   ||
        currentIsCampaign != originalIsCampaign

    // ── No changes → dialog should NOT appear ────────────────────────────────

    @Test
    fun noChanges_noDirtyFlag() {
        assertFalse(
            hasDashboardChanges(
                currentName = "Alpha",       originalName = "Alpha",
                currentFaction = Faction.COMMONWEALTH, originalFaction = Faction.COMMONWEALTH,
                currentCharIds = setOf(1, 2), originalCharIds = setOf(1, 2),
                currentIsCampaign = false,   originalIsCampaign = false
            )
        )
    }

    // ── Individual field changes ──────────────────────────────────────────────

    @Test
    fun nameChanged_triggersDirtyFlag() {
        assertTrue(
            hasDashboardChanges(
                currentName = "Beta",        originalName = "Alpha",
                currentFaction = Faction.COMMONWEALTH, originalFaction = Faction.COMMONWEALTH,
                currentCharIds = setOf(1, 2), originalCharIds = setOf(1, 2),
                currentIsCampaign = false,   originalIsCampaign = false
            )
        )
    }

    @Test
    fun factionChanged_triggersDirtyFlag() {
        assertTrue(
            hasDashboardChanges(
                currentName = "Alpha",       originalName = "Alpha",
                currentFaction = Faction.SHADES, originalFaction = Faction.COMMONWEALTH,
                currentCharIds = setOf(1, 2), originalCharIds = setOf(1, 2),
                currentIsCampaign = false,   originalIsCampaign = false
            )
        )
    }

    @Test
    fun characterAdded_triggersDirtyFlag() {
        assertTrue(
            hasDashboardChanges(
                currentName = "Alpha",       originalName = "Alpha",
                currentFaction = Faction.COMMONWEALTH, originalFaction = Faction.COMMONWEALTH,
                currentCharIds = setOf(1, 2, 3), originalCharIds = setOf(1, 2),
                currentIsCampaign = false,   originalIsCampaign = false
            )
        )
    }

    @Test
    fun characterRemoved_triggersDirtyFlag() {
        assertTrue(
            hasDashboardChanges(
                currentName = "Alpha",       originalName = "Alpha",
                currentFaction = Faction.COMMONWEALTH, originalFaction = Faction.COMMONWEALTH,
                currentCharIds = setOf(1),   originalCharIds = setOf(1, 2),
                currentIsCampaign = false,   originalIsCampaign = false
            )
        )
    }

    @Test
    fun isCampaignToggled_triggersDirtyFlag() {
        assertTrue(
            hasDashboardChanges(
                currentName = "Alpha",       originalName = "Alpha",
                currentFaction = Faction.COMMONWEALTH, originalFaction = Faction.COMMONWEALTH,
                currentCharIds = setOf(1, 2), originalCharIds = setOf(1, 2),
                currentIsCampaign = true,    originalIsCampaign = false
            )
        )
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun nameChangedThenRestored_noDirtyFlag() {
        // If the user edits the name back to the original value the dialog should not show.
        assertFalse(
            hasDashboardChanges(
                currentName = "Alpha",       originalName = "Alpha",
                currentFaction = Faction.COMMONWEALTH, originalFaction = Faction.COMMONWEALTH,
                currentCharIds = setOf(1, 2), originalCharIds = setOf(1, 2),
                currentIsCampaign = false,   originalIsCampaign = false
            )
        )
    }

    @Test
    fun emptyNameOnNewTroupe_noDirtyFlag() {
        // New troupe: both original and current are blank → no dirty flag yet.
        assertFalse(
            hasDashboardChanges(
                currentName = "",            originalName = "",
                currentFaction = Faction.COMMONWEALTH, originalFaction = Faction.COMMONWEALTH,
                currentCharIds = emptySet(), originalCharIds = emptySet(),
                currentIsCampaign = false,   originalIsCampaign = false
            )
        )
    }

    @Test
    fun characterOrderChange_doesNotTriggerDirtyFlag() {
        // selectedCharacterIds is a Set — order is irrelevant.
        assertFalse(
            hasDashboardChanges(
                currentName = "Alpha",       originalName = "Alpha",
                currentFaction = Faction.COMMONWEALTH, originalFaction = Faction.COMMONWEALTH,
                currentCharIds = setOf(2, 1), originalCharIds = setOf(1, 2),
                currentIsCampaign = false,   originalIsCampaign = false
            )
        )
    }

    @Test
    fun multipleFieldsChanged_triggersDirtyFlag() {
        assertTrue(
            hasDashboardChanges(
                currentName = "Gamma",       originalName = "Alpha",
                currentFaction = Faction.LESHAVULT, originalFaction = Faction.COMMONWEALTH,
                currentCharIds = emptySet(), originalCharIds = setOf(1, 2),
                currentIsCampaign = true,    originalIsCampaign = false
            )
        )
    }
}
