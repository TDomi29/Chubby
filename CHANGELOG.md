# Changelog

All notable changes to Chubby are documented here.

## [1.0.0] - 2026-07-20

### Official initial release

This is the complete feature set shipped in the official `Chubby-1.0.0.jar` release.

### Chunk force-loading and persistence

- Adds owner-scoped chunk selection and Paper force-load tickets.
- Adds configurable per-player chunk limits and the `chubby.bypass.limit` override.
- Restores force-load tickets automatically when the plugin starts.
- Releases all tickets safely when the plugin disables.
- Persists each selected chunk's owner, group, and priority in `chunks.yml`.
- Treats `chunks.yml` as the source of truth at startup and on `/chubby reload`.
- Supports legacy UUID-only chunk records and migrates them to the current owner/group/priority structure without deleting valid data.
- Appends required data fields during migration and preserves unrelated YAML entries.
- Reloads chunk state by releasing previous tickets, loading the replacement file, and restoring tickets from the newly loaded state.

### Commands

- Adds `/chubby` to open the command center.
- Adds `/chubby toggle` to select or remove the current chunk.
- Adds `/chubby show` to display the current chunk boundary.
- Adds `/chubby search <text>` to filter the owned-chunk browser; `clear` removes the filter.
- Adds `/chubby list` to list owned chunks in chat.
- Adds `/chubby groups` to open the group browser.
- Adds `/chubby link <name>` and `/chubby unlink` for current-chunk group membership.
- Adds `/chubby priority <low|normal|high>` for current-chunk priority.
- Adds `/chubby group <name> <priority|pause|resume>` for bulk group management.
- Adds `/chubby diagnostics`, `/chubby map`, and `/chubby remove <world> <chunkX> <chunkZ>` for administrators.
- Adds `/chubby reload` to reload configuration, language, and persisted chunk data.
- Provides command tab completion for supported subcommands, priorities, and group actions.

### Command-center GUI

- Adds an inventory command center with current-chunk enable/disable controls, status, search, help, groups, chunk browser, management, and visualizer access.
- Adds paged owned-chunk browsing with filters, active/paused state, priority, group, navigation, and detailed chunk information.
- Adds a detailed information screen with ownership, availability, group, priority, state, chunk/block coordinates, and load-estimate details.
- Adds a management screen for changing a selected chunk's priority, linking/unlinking groups, creating groups, and opening group actions.
- Adds a group browser and group-action screen for bulk priority changes and pausing/resuming tickets.
- Adds confirmation screens before GUI-initiated chunk removal.
- Adds a diagnostics screen highlighting system status and the highest estimated-load chunks.
- Adds an administrator area map showing nearby selected chunks, ownership colours, map navigation, and chunk detail access.
- Prevents item movement through plugin inventories and applies consistent inventory framing, labels, lore, and glint state.

### Groups and priorities

- Adds named chunk groups for a player's selected chunks.
- Adds configurable group-name length limits.
- Adds low, normal, and high per-chunk priorities.
- Adds bulk priority changes for all chunks in a group.
- Adds persistent group pausing and resuming, with immediate force-load ticket updates.

### Performance protection and load monitoring

- Adds configurable TPS-based protection that suspends tickets in low-to-high priority order as server performance declines.
- Restores tickets automatically when TPS recovers.
- Adds configurable check intervals and separate low, normal, and high suspension thresholds.
- Adds chunk load estimates based on entity, hopper, and tile-entity counts with configurable weights.
- Adds periodic load warnings with a configurable score threshold.
- Adds configurable console-warning and optional owner-chat notification cooldowns.
- Displays TPS, protection status, active tickets, paused tickets, and load estimates in diagnostics.

### Visualization

- Adds a particle-based 16×16 chunk-border visualizer.
- Shows border particles only to the player who requested the visualization.
- Adds configurable display duration, marker height, and particle spacing.

### Configuration and permissions

- Adds configurable ownership limits, GUI page size, group menu capacity, visualizer settings, protection thresholds, estimation weights, and warning behaviour.
- Adds `chubby.use`, `chubby.menu`, `chubby.manage`, `chubby.visualize`, `chubby.admin`, and `chubby.bypass.limit` permissions.
- Restricts administrative map, diagnostics, removal, and reload actions to `chubby.admin`.

### Localization

- Moves player-facing command messages, action-bar text, diagnostics, help, console output, GUI titles, item names, and lore into language files.
- Ships complete English (`en.yml`) and Hungarian (`hu_HU.yml`) language files; `hu` is accepted as a Hungarian alias.
- Falls back safely to English when a configured custom locale is missing.
- Automatically fills missing language keys from bundled defaults without replacing existing translations.

### Compatibility and packaging

- Targets Java 25 and Paper 26.2 build 60-beta API.
- Packages the official release artifact as `Chubby-1.0.0.jar`.
- Uses plugin version `1.0.0` in Maven metadata and `plugin.yml`.
