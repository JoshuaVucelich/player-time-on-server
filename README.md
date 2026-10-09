# Player Time on Server

A Fabric mod for checking player stats on a Minecraft server. It reads the stats Minecraft already stores, so it covers people who are online and people who have logged off.

VWS Digital. The license is CC0.

## Commands

You need the gamemaster command permission.

- `/pstats list` shows every player who has stats, whether they are online or offline, and their play time. Online players are listed first.
- `/pstats player <name>` shows one player: play time, deaths, mob kills, player kills, damage dealt, damage taken, distance walked and sprinted, jumps, blocks mined, items used, items crafted, and tools broken. It also lists their top 5 mined blocks and top 5 killed mobs.
- `/pstats player <name> full` adds every non-zero stat. That includes custom stats, each block mined, items used, broken, crafted, picked up, and dropped, plus mobs they killed and mobs that killed them.
- `/pstats top <category>` is a leaderboard. It shows 10 players unless you add a limit from 1 to 100. Example: `/pstats top playtime 25`.
- `/pstats export` writes a TSV file in the world save folder. The name looks like `player-time-on-server-export-yyyyMMdd-HHmmss.tsv`. Columns are name, uuid, online (`yes` or `no`), then one raw number for each category below.
- `/pstats` and `/pstats help` print the command list.

Player names and categories tab-complete. Name matching ignores case.

### Leaderboard categories

`playtime`, `deaths`, `mob_kills`, `player_kills`, `jumps`, `damage_dealt`, `damage_taken`, `walked`, `sprinted`, `crouched`, `blocks_mined`, `items_used`, `items_broken`, `items_crafted`, `items_picked`, `items_dropped`

Play time is stored in ticks. 20 ticks is one second, and the command prints days, hours, minutes, and seconds. Walked, sprinted, and crouched are stored in centimeters and printed as cm, meters, or kilometers. Damage dealt and damage taken are stored in tenths of a heart and printed with one decimal. The export file keeps the raw integers.

Offline players are loaded from the world stats folder (`<uuid>.json`). If the server does not have their name cached, you see the UUID instead.

Tools broken on the player page is the total of the items-broken stat.

## Requirements

This branch is for Minecraft 26.3 on Fabric (mod version `1.0.2+26.3-fabric`).

- Java 25 or newer
- Fabric Loader 0.19.5 or newer
- Fabric API 0.162.0+26.3
- Loom 1.18-SNAPSHOT and Gradle 9.7.1

Mod id: `player-time-on-server`

Other version branches:

- Fabric 26.2: `mc/26.2` (`1.0.2+26.2-fabric`)
- Fabric 26.1.2: `mc/26.1.2` (`1.0.1+26.1.2-fabric`)
- Fabric 1.21.11: `mc/1.21.11` (`1.0.2+1.21.11-fabric`)
- NeoForge 26.2: `neoforge/26.2` (`1.0.2+26.2-neoforge`). NeoForge 26.3 is beta-only, so the port tracks the newest stable NeoForge release.

## Build

```bash
./gradlew build
```

The jar ends up in `build/libs/`.

## License

[CC0 1.0 Universal](LICENSE). Use it, change it, and ship it.
