# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Bumped Ktor from 3.0.1 to 3.4.1
- Bumped Kotlin from 2.3.10 to 2.3.20

## [2.2.7] - 2026-03-19

### Added
- Fastlane `icon.png` for F-Droid store listing metadata

### Fixed
- Replaced ML Kit barcode scanning with ZXing (Apache-2.0) for F-Droid compatibility
- `signingConfig` now uses `findByName` so F-Droid builds survive the stripped signing block
- `versionCode`/`versionName` now derived from git tags for reproducible F-Droid builds
- PNG crunching disabled for reproducible builds
- Force-pinned vulnerable transitive dependency versions via resolution strategy
- Resolved all lint warnings

## [2.2.2] - 2026-03-16

### Fixed
- Release pipeline no longer fails when `data.version` is unchanged between releases

## [2.2.1] - 2026-03-16

### Changed
- Dependabot auto-updates enabled for Gradle and GitHub Actions dependencies
- Updated Compose BOM from 2024.10.01 to 2024.12.01
- Updated various GitHub Actions (upload-artifact, setup-java, checkout, cache)

## [2.2.0] - 2026-03-15

### Changed
- Split Room database into `GameDatabase` (destructive migration OK — game data is reseedable) and `UserDatabase` (explicit migrations required — preserves troupes, campaigns, results)

## [2.1.0] - 2026-03-15

### Fixed
- Bundled assets patch for compendium data loading

## [2.0.0] - 2026-03-09

### Added
- Data update system: app checks for new compendium releases from `garemat/lunachron-data` on GitHub and downloads updated JSON assets and portrait images
- `DataUpdateRepository` with `PROMPT`/`ENABLED`/`DISABLED` image download preference
- Consolidated asset files (`compendium.json`, `upgrades.json`, `campaign.json`) replace per-file subdirectories

### Changed
- `CharacterData` reads from `filesDir/data/` first, falling back to bundled assets (enables live updates without reinstall)

## [1.1.0] - 2026-03-08

### Added
- Active game screen redesign with two layout modes: `COMPACT_GRID` (2-column grid) and `DETAILED_LIST` (full card)
- `GameTrackingMode`: `LOW_DETAIL` (health pips only) and `FULL_TRACKING` (energy, moonstones, ability markers)
- `HealthPipsChunked` canonical health display (groups of 5, new row after 10)
- `SummonerIndicator` shared composable for both layout modes

## [1.0.0] - 2026-03-07

### Added
- Campaign management: `CampaignManagement`, `CampaignDetails`, `AddEditCampaign`, `EditCampaign` screens
- `CampaignCard` and `UpgradeCard` compendium entries
- Share codes for all compendium items (5-char format: `[type][faction][id0][id1][id2]`)
- Troupe share strings (base64-encoded faction + character/upgrade codes)

## [0.2.0] - 2026-02-25

### Added
- Usability improvements across compendium, troupe, and game setup flows

## [0.1.0] - 2026-02-10

### Added
- Initial public release
- Character compendium with four factions (Commonwealth, Dominion, Leshavult, Shades)
- Troupe builder with faction filtering
- Local multiplayer via Android NSD (mDNS) + TCP sockets; Wi-Fi Direct support
- Local tournament bracket management
- Two app themes: Default and Moonstone
- Three layout densities: Compact, Cozy, Spacious

[Unreleased]: https://github.com/Garemat/lunachron/compare/v2.2.7...HEAD
[2.2.7]: https://github.com/Garemat/lunachron/compare/v2.2.2...v2.2.7
[2.2.2]: https://github.com/Garemat/lunachron/compare/v2.2.1...v2.2.2
[2.2.1]: https://github.com/Garemat/lunachron/compare/v2.2.0...v2.2.1
[2.2.0]: https://github.com/Garemat/lunachron/compare/v2.1.0...v2.2.0
[2.1.0]: https://github.com/Garemat/lunachron/compare/v2.0.0...v2.1.0
[2.0.0]: https://github.com/Garemat/lunachron/compare/v1.1.0...v2.0.0
[1.1.0]: https://github.com/Garemat/lunachron/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/Garemat/lunachron/compare/v0.2.0...v1.0.0
[0.2.0]: https://github.com/Garemat/lunachron/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/Garemat/lunachron/releases/tag/v0.1.0
