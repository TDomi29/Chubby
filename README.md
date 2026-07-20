# Chubby

Chubby is a Paper plugin for selecting and force-loading Minecraft chunks through a polished command center and inventory GUI. It helps players keep important builds, farms, and transport systems loaded while giving server operators practical limits and performance controls.

## Features

- Force-load owned chunks with configurable per-player limits.
- Inventory-based command center, chunk browser, management screens, group controls, diagnostics, and administrator map.
- Chunk groups, priorities, pausing, and automatic performance protection based on TPS.
- Particle chunk-border visualizer and load-estimate warnings.
- Persistent chunk ownership and settings.
- Complete language-key based player messaging and GUI text.

## Requirements and compatibility

- Java 25
- Paper 26.2 (build 60-beta API) or a compatible Paper server
- Minecraft version matching your Paper 26.2 server

## Installation

1. Download or build `Chubby-1.0.0.jar`.
2. Place it in the server `plugins` directory.
3. Start the server once to create `plugins/Chubby/config.yml` and the language files.
4. Review the configuration, then run `/chubby reload` after edits.

## Commands

| Command | Description |
| --- | --- |
| `/chubby` | Open the command center. |
| `/chubby toggle` | Select or remove the current chunk. |
| `/chubby show` | Display the current chunk border. |
| `/chubby search <text>` | Filter your selected chunks. |
| `/chubby groups` | Open chunk groups. |
| `/chubby link <name>` | Link the current selected chunk to a group. |
| `/chubby unlink` | Remove the current chunk from its group. |
| `/chubby priority <low|normal|high>` | Set the current chunk priority. |
| `/chubby group <name> <priority|pause|resume>` | Manage a group. |
| `/chubby diagnostics` | Show administrator diagnostics. |
| `/chubby map` | Open the administrator map. |
| `/chubby remove <world> <x> <z>` | Remove a selected chunk as an administrator. |
| `/chubby reload` | Reload configuration, language, and `chunks.yml` data as an administrator. |

## Permissions

| Permission | Description |
| --- | --- |
| `chubby.use` | Use core commands. |
| `chubby.menu` | Open the GUI. |
| `chubby.manage` | Select and manage owned chunks. |
| `chubby.visualize` | Display chunk borders. |
| `chubby.admin` | Use administrative tools and reload configuration. |
| `chubby.bypass.limit` | Bypass the ownership limit. |

## Configuration

`config.yml` controls ownership limits, GUI paging, visualizer timing, group-name limits, load estimation, warning thresholds, and TPS protection. Chubby stores selected chunks in `chunks.yml`; do not edit it while the server is running. Replacing this file while the plugin is stopped and then starting the server (or running `/chubby reload`) restores that file's chunk data.

## Localization

All player-visible messages and GUI labels are resolved through `plugins/Chubby/lang/<language>.yml`. Built-in locales are English (`en`) and Hungarian (`hu_HU`; `hu` is accepted as an alias). Set `language` in `config.yml` and use `/chubby reload`. Custom language files can omit keys: Chubby safely fills missing entries from English after reload.

## GUI overview

The command center exposes current-chunk controls, visualizer, browser, groups, search, help, and administrator tools. The browser offers paging and detail screens; management screens set priority, create/link groups, pause a group, and safely confirm removals.

## Examples

```text
/chubby toggle
/chubby link iron-farm
/chubby group iron-farm priority high
/chubby show
```

## FAQ

**Why is my chunk paused?** TPS protection can temporarily release low-priority tickets. It restores tickets automatically when server health recovers.

**Can players remove another player's chunk?** Only users with `chubby.admin` can do that.

**Does Chubby place blocks?** No. It uses Paper force-load tickets and optional client-side particles.

## License

No license file is currently included. Add an explicit license before redistributing or accepting contributions.

## Credits

Built for PaperMC and the Minecraft server community. Chubby uses the Paper API and Kyori Adventure components provided by Paper.
