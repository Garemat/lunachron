# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.12.3] - 2026-04-21

### Added

- JSON-driven theme system with font and inheritance support (#76)

- Google Play prep — privacy policy, name fix, installer detection, theme gameplay prefs (#79)

- Troupe editor UX polish — header, cards, strip (#89)

- Campaign overhaul — machinations, VP tracking, MP distribution, round rankings (#91)

- Compendium UI overhaul — filter, fast-scroll, card polish, faction icons (#99)

- Settings UI improvements (#101)

- Local game setup flow, favourite troupes, and home Quick Start (#105)

- LunaChron online campaign API integration (#109)

- Health pip outline style and in-app APK updates for GitHub builds (#111)

- Add Obsidian dark theme with themeable health pip colour (#113)

- Pre-merge version bump embedded in PR branch (#121)


### Fixed

- Use findByName for signingConfig to survive F-Droid build (#69)

- Replace ML Kit barcode scanning with ZXing (#70)

- Disable tutorial and local tournament flows (#93)

- Privacy-first defaults, app icon, and startup network tests (#97)

- Update CI workflow ref from deleted branch to main (#100)

- CI, compile, and cold-start fixes for campaign API integration (#110)

- CI OOM and artifact paths after fdroid/github flavor split (#112)

- Remove stale Type of Change validation and bump patch for chore PRs (#116)

- Patch bump for chore PRs and serialise concurrent releases (#117)

- Correct version floor using absolute latest tag (#118)

- Use --unreleased for release notes before tag is created (#122)

- Move data.version pin to pre-merge, release workflow pushes tag only


## [0.0.1] - 2026-02-10

