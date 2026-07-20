# Changelog

All notable changes to Chubby are documented here.

## [1.0.0] - 2026-07-20

### First stable release

- Added persistent, owner-scoped chunk force-loading with configurable limits.
- Added the command-center GUI, chunk browser, information screens, management controls, confirmation prompts, group browser, diagnostics, and administrator map.
- Added chunk groups, per-chunk priorities, group pausing, and automatic ticket restoration.
- Added TPS-based performance protection, load estimation, configurable load warnings, and optional owner notifications.
- Added a particle chunk-border visualizer that is visible only to the requesting player.
- Added configurable settings for limits, GUI paging, visualization, warnings, estimation weights, and performance thresholds.
- Added a permission model for standard use, GUI access, chunk management, visualization, administrator access, and limit bypass.
- Renamed the public command to `/chubby` throughout the plugin and documentation.
- Moved player-visible messages, help text, action-bar text, and every GUI title/name/lore entry to language keys.
- Added safe language-file fallback and missing-key upgrades from the bundled English language file.
- Improved compatibility with Paper 26.2 and Java 25, and retained legacy chunk-data loading compatibility.
- Fixed stale legacy GUI text, inconsistent command usage output, and force-load state refreshes after group or priority changes.
