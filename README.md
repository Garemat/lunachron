# Lunachron

[![F-Droid](https://img.shields.io/f-droid/v/io.github.garemat.lunachron.svg?logo=F-Droid)](https://f-droid.org/packages/io.github.garemat.lunachron/)
[![GitHub Release](https://img.shields.io/github/release/garemat/lunachron.svg?logo=github)](https://github.com/Garemat/lunachron/releases)

An unofficial companion app for the [Moonstone](https://goblinkinggames.com) tabletop miniatures game by Goblin King Games.

## Features

- **Compendium** — browse all characters, upgrade cards, and campaign cards with full stats
- **Troupe builder** — create and manage troupes for each faction
- **Active game tracker** — track health, energy, moonstones, and ability usage during a game
- **Campaign management** — run multi-round campaigns with rankings, machinations, and attack phases
- **Local multiplayer** — peer-to-peer game setup over Wi-Fi (no internet required)
- **Local tournament** — bracket-style tournament management for in-person events
- **Game data updates** — opt-in in-app updates when new characters or rules are released

## Installation

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/io.github.garemat.lunachron/)

Or download the latest APK directly from [GitHub Releases](https://github.com/garemat/lunachron/releases).

## Building from source

Requires Android SDK (API 36), JDK 17, and Gradle 9+.

```bash
./gradlew assembleDebug
```

Game data is bundled at build time. The `data.version` file in the repo root pins the
[lunachron-data](https://github.com/garemat/lunachron-data) release used for the seed
`compendium.json` asset, ensuring reproducible builds.

## License

The application source code is licensed under the **Apache License 2.0** — see the
[LICENSE](LICENSE) file for details.

## Attribution

Game data, character names, stats, ability descriptions, and portrait images are sourced
from [Goblin King Games'](https://goblinkinggames.com) publicly available store and website.
These assets are the intellectual property of Goblin King Games and are **not** covered by
the Apache 2.0 license.

- Assets must not be used for commercial purposes.
- Please credit Goblin King Games when using or referencing these assets.

Lunachron is an unofficial fan-created app and is not affiliated with or endorsed by
Goblin King Games.

