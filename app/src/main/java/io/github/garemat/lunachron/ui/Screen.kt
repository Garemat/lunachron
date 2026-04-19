package io.github.garemat.lunachron.ui

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Compendium : Screen("compendium")
    data object Characters : Screen("characters")
    data object Upgrades : Screen("upgrades")
    data object CampaignCards : Screen("campaign_cards")
    data object Troupes : Screen("troupes")
    data object SelectTroupe : Screen("select_troupe")
    data object SoloTroupeSelect : Screen("solo_troupe_select")
    data object AddEditTroupe : Screen("add_edit_troupe")
    data object GameSetup : Screen("game_setup")
    data object ActiveGame : Screen("active_game")
    data object Settings : Screen("settings")
    data object Rules : Screen("rules")
    data object Stats : Screen("stats")
    data object TournamentSetup : Screen("tournament_setup")
    data object TournamentWaitingRoom : Screen("tournament_waiting_room")
    data object TournamentRound : Screen("tournament_round")
    data object CampaignHub : Screen("campaign_hub")
    data object CampaignManagement : Screen("campaign_management")
    data object AddEditCampaign : Screen("add_edit_campaign")
    data object HostOnlineCampaign : Screen("host_online_campaign")
    data object JoinOnlineCampaign : Screen("join_online_campaign")
    data object ActiveOnlineCampaigns : Screen("active_online_campaigns")
    data object OnlineCampaignDetail : Screen("online_campaign_detail/{campaignId}") {
        fun createRoute(campaignId: String) = "online_campaign_detail/$campaignId"
    }
    data object EditCampaign : Screen("edit_campaign/{campaignId}") {
        fun createRoute(campaignId: Int) = "edit_campaign/$campaignId"
    }
    data object CampaignDetails : Screen("campaign_details/{campaignId}") {
        fun createRoute(campaignId: Int) = "campaign_details/$campaignId"
    }
}
