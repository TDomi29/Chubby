package hu.chubby.chunkloader;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;

import java.util.*;

public final class ChubbyPlugin extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    private final ChunkStore store = new ChunkStore(getDataFolder());
    private final Lang lang = new Lang(this);
    private final Map<UUID, BukkitTask> visualTasks = new HashMap<>();
    private ChunkMenu menu;
    private BukkitTask performanceTask;
    private BukkitTask warningTask;
    private int suspendedPriority = -1;
    private final Map<ChunkKey, Long> warningCooldowns = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        saveResource("lang/en.yml", false);
        saveResource("lang/hu_HU.yml", false);
        lang.load();
        store.load();
        menu = new ChunkMenu(this);
        Objects.requireNonNull(getCommand("chubby")).setExecutor(this);
        Objects.requireNonNull(getCommand("chubby")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        restoreChunkTickets();
        printBanner();
        startPerformanceProtection();
        startWarningMonitor();
        lang.console("console.enabled", Map.of("count", String.valueOf(store.all().size())));
    }

    @Override public void onDisable() {
        visualTasks.values().forEach(BukkitTask::cancel);
        visualTasks.clear();
        if (performanceTask != null) performanceTask.cancel();
        if (warningTask != null) warningTask.cancel();
        releaseChunkTickets();
        store.save();
    }

    private void printBanner() {
        int owners = (int) store.all().values().stream().map(ChunkStore.ChunkSettings::owner).distinct().count();
        int groups = store.all().values().stream().map(ChunkStore.ChunkSettings::group).filter(group -> !group.isBlank()).collect(java.util.stream.Collectors.toSet()).size();
        lang.console("console.banner-selected", Map.of("count", String.valueOf(store.all().size())));
        lang.console("console.banner-active", Map.of("count", String.valueOf(activeTicketCount())));
        lang.console("console.banner-owners", Map.of("owners", String.valueOf(owners), "groups", String.valueOf(groups)));
        lang.console("console.banner-protection", Map.of("state", lang.text(isLoadProtected() ? "console.state-paused" : "console.state-active"), "tps", String.format(Locale.ROOT, "%.2f", currentTps())));
    }

    private long activeTicketCount() {
        return store.all().keySet().stream().filter(key -> {
            World world = Bukkit.getWorld(key.worldName());
            return world != null && world.isChunkForceLoaded(key.x(), key.z());
        }).count();
    }

    public ChunkStore store() { return store; }
    public Lang lang() { return lang; }
    public int maxChunks() { return getConfig().getInt("max-chunks-per-player", 10); }
    public int guiItemsPerPage() { return Math.clamp(getConfig().getInt("gui.items-per-page", 28), 1, 36); }
    public int guiGroupsPerMenu() { return Math.clamp(getConfig().getInt("gui.max-groups-per-menu", 12), 1, 12); }
    public boolean isLoadProtected() { return suspendedPriority >= 0; }
    public boolean isChunkSuspended(ChunkKey key) { return store.contains(key) && (store.isGroupPaused(key) || store.priority(key) <= suspendedPriority); }
    public double currentTps() { return Bukkit.getServer().getTPS()[0]; }
    public String format(ChunkKey key) { return key.worldName() + " (" + key.x() + ", " + key.z() + ")"; }

    private void restoreChunkTickets() {
        for (ChunkKey key : store.all().keySet()) setForced(key, !isChunkSuspended(key));
    }
    private void releaseChunkTickets() {
        for (ChunkKey key : store.all().keySet()) setForced(key, false);
    }
    private boolean setForced(ChunkKey key, boolean forced) {
        World world = Bukkit.getWorld(key.worldName());
        if (world == null) return false;
        world.setChunkForceLoaded(key.x(), key.z(), forced);
        return true;
    }

    private void startPerformanceProtection() {
        if (performanceTask != null) performanceTask.cancel();
        if (!getConfig().getBoolean("performance-protection.enabled", true)) return;
        long period = Math.max(5, getConfig().getLong("performance-protection.check-interval-seconds", 20)) * 20L;
        performanceTask = Bukkit.getScheduler().runTaskTimer(this, this::updatePerformanceState, period, period);
    }

    private void updatePerformanceState() {
        if (!getConfig().getBoolean("performance-protection.enabled", true)) {
            if (suspendedPriority >= 0) { suspendedPriority = -1; restoreChunkTickets(); }
            return;
        }
        double tps = currentTps();
        int nextSuspended = tps < getConfig().getDouble("performance-protection.suspend-high-priority-below-tps", 15.0D) ? 2
                : tps < getConfig().getDouble("performance-protection.suspend-normal-priority-below-tps", 17.0D) ? 1
                : tps < getConfig().getDouble("performance-protection.suspend-low-priority-below-tps", 18.0D) ? 0 : -1;
        if (nextSuspended != suspendedPriority) {
            suspendedPriority = nextSuspended;
            restoreChunkTickets();
            lang.console(nextSuspended < 0 ? "console.protection-restored" : "console.protection-suspended", Map.of("priority", priorityName(nextSuspended), "tps", String.format(Locale.ROOT, "%.2f", tps)));
        }
    }

    private void startWarningMonitor() {
        if (warningTask != null) warningTask.cancel();
        long minutes = Math.max(1, getConfig().getLong("load-warnings.check-interval-minutes", 5));
        warningTask = Bukkit.getScheduler().runTaskTimer(this, this::scanForWarnings, minutes * 1200L, minutes * 1200L);
    }

    private void scanForWarnings() {
        if (!getConfig().getBoolean("load-warnings.enabled", true)) return;
        int threshold = getConfig().getInt("load-warnings.score-threshold", 40);
        long now = System.currentTimeMillis();
        long consoleCooldown = Math.max(1, getConfig().getLong("load-warnings.console-cooldown-minutes", 60)) * 60_000L;
        long ownerCooldown = Math.max(1, getConfig().getLong("load-warnings.owner-chat-cooldown-minutes", 30)) * 60_000L;
        for (ChunkKey key : store.all().keySet()) {
            ChunkLoadEstimate estimate = estimate(key);
            if (!estimate.available() || estimate.score() < threshold) continue;
            long consoleKey = warningCooldowns.getOrDefault(key, 0L);
            if (now - consoleKey >= consoleCooldown) {
                warningCooldowns.put(key, now);
                lang.console("console.warning", Map.of("chunk", format(key), "score", String.valueOf(estimate.score()), "entities", String.valueOf(estimate.entities()), "hoppers", String.valueOf(estimate.hoppers())));
            }
            if (getConfig().getBoolean("load-warnings.notify-owner-in-chat", false)) {
                ChunkKey ownerWarningKey = new ChunkKey(key.worldName() + "#owner", key.x(), key.z());
                if (now - warningCooldowns.getOrDefault(ownerWarningKey, 0L) >= ownerCooldown) {
                    warningCooldowns.put(ownerWarningKey, now);
                    Player owner = Bukkit.getPlayer(store.owner(key));
                    if (owner != null) lang.send(owner, "console.warning", Map.of("chunk", format(key), "score", String.valueOf(estimate.score()), "entities", String.valueOf(estimate.entities()), "hoppers", String.valueOf(estimate.hoppers())));
                }
            }
        }
    }

    public ChunkLoadEstimate estimate(ChunkKey key) {
        World world = Bukkit.getWorld(key.worldName());
        if (world == null || !world.isChunkLoaded(key.x(), key.z())) return new ChunkLoadEstimate(false, 0, 0, 0, 0);
        Chunk chunk = world.getChunkAt(key.x(), key.z());
        BlockState[] tiles = chunk.getTileEntities();
        int hoppers = (int) Arrays.stream(tiles).filter(tile -> tile.getType() == Material.HOPPER).count();
        int score = (chunk.getEntities().length * getConfig().getInt("load-estimation.entity-weight", 1))
                + (hoppers * getConfig().getInt("load-estimation.hopper-weight", 4))
                + (tiles.length * getConfig().getInt("load-estimation.tile-entity-weight", 1));
        return new ChunkLoadEstimate(true, chunk.getEntities().length, hoppers, tiles.length, score);
    }

    public String priorityName(int priority) { return lang.text("priorities." + switch (priority) { case 0 -> "low"; case 2 -> "high"; default -> "normal"; }); }

    boolean toggle(Player player, ChunkKey key) {
        if (!player.hasPermission("chubby.manage")) { noPermission(player); return false; }
        if (store.contains(key)) {
            UUID owner = store.owner(key);
            if (!owner.equals(player.getUniqueId()) && !player.hasPermission("chubby.admin")) { noPermission(player); return false; }
            store.remove(key); setForced(key, false); store.save();
            player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.65F, 1.15F);
            lang.send(player, "messages.chunk-disabled", Map.of("chunk", format(key)));
            return true;
        }
        if (!player.hasPermission("chubby.bypass.limit") && store.count(player.getUniqueId()) >= maxChunks()) {
            lang.send(player, "messages.limit-reached", Map.of("limit", String.valueOf(maxChunks()))); return false;
        }
        store.add(key, player.getUniqueId());
        if (!isChunkSuspended(key)) setForced(key, true);
        store.save();
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.65F, 1.35F);
        lang.send(player, isChunkSuspended(key) ? "messages.chunk-waiting" : "messages.chunk-enabled", Map.of("chunk", format(key)));
        return true;
    }

    private void noPermission(Player player) { lang.send(player, "messages.no-permission"); }

    private void visualize(Player player) {
        if (!player.hasPermission("chubby.visualize")) { noPermission(player); return; }
        BukkitTask old = visualTasks.remove(player.getUniqueId()); if (old != null) old.cancel();
        Chunk chunk = player.getLocation().getChunk();
        int seconds = Math.max(1, getConfig().getInt("visualization-seconds", 12));
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> drawBorder(player, chunk), 0L, 10L);
        visualTasks.put(player.getUniqueId(), task);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            BukkitTask running = visualTasks.remove(player.getUniqueId()); if (running != null) running.cancel();
        }, seconds * 20L);
        boolean active = store.contains(ChunkKey.of(chunk));
        lang.send(player, "messages.visualization", Map.of("seconds", String.valueOf(seconds)));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7F, active ? 1.35F : 1.1F);
    }

    private void drawBorder(Player player, Chunk chunk) {
        if (!player.isOnline() || !player.getWorld().equals(chunk.getWorld())) return;
        ChunkKey key = ChunkKey.of(chunk);
        boolean active = store.contains(key);
        int height = Math.max(2, getConfig().getInt("visualization.vertical-height", 6));
        int spacing = Math.max(1, getConfig().getInt("visualization.particle-spacing", 2));
        double y = Math.floor(player.getLocation().getY()) + 0.15;
        double minX = chunk.getX() << 4;
        double minZ = chunk.getZ() << 4;
        Particle.DustOptions dust = new Particle.DustOptions(active ? Color.fromRGB(80, 255, 145) : Color.fromRGB(70, 210, 255), 1.35F);

        // The 16x16 ground border is sent only to the requesting player.
        for (int point = 0; point <= 16; point += spacing) {
            dust(player, minX + point + .5, y, minZ + .5, dust);
            dust(player, minX + point + .5, y, minZ + 15.5, dust);
            dust(player, minX + .5, y, minZ + point + .5, dust);
            dust(player, minX + 15.5, y, minZ + point + .5, dust);
        }
        // Vertical light corners make the border easy to recognize at a distance.
        for (int level = 0; level <= height; level += 2) {
            corner(player, minX + .5, y + level, minZ + .5, dust);
            corner(player, minX + 15.5, y + level, minZ + .5, dust);
            corner(player, minX + .5, y + level, minZ + 15.5, dust);
            corner(player, minX + 15.5, y + level, minZ + 15.5, dust);
        }
        player.sendActionBar(Component.text(lang.text(active ? "messages.actionbar-active" : "messages.actionbar-inactive", Map.of("world", key.worldName(), "x", String.valueOf(key.x()), "z", String.valueOf(key.z())))));
    }

    private void dust(Player player, double x, double y, double z, Particle.DustOptions dust) {
        player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
    }

    private void corner(Player player, double x, double y, double z, Particle.DustOptions dust) {
        dust(player, x, y, z, dust);
        player.spawnParticle(Particle.END_ROD, x, y, z, 1, 0.025, 0.025, 0.025, 0.005);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { lang.send(sender, "messages.player-only"); return true; }
        if (!player.hasPermission("chubby.use")) { noPermission(player); return true; }
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> { if (!player.hasPermission("chubby.menu")) noPermission(player); else menu.open(player); }
            case "toggle", "select" -> toggle(player, ChunkKey.of(player.getLocation().getChunk()));
            case "show", "visualize" -> visualize(player);
            case "help", "?" -> sendHelp(player);
            case "diagnostics", "diag" -> sendDiagnostics(player);
            case "search" -> {
                if (!player.hasPermission("chubby.menu")) noPermission(player);
                else menu.open(player, 0, args.length < 2 || args[1].equalsIgnoreCase("clear") ? "" : String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            }
            case "map" -> { if (!player.hasPermission("chubby.admin")) noPermission(player); else { Chunk chunk = player.getLocation().getChunk(); menu.map(player, chunk.getX(), chunk.getZ()); } }
            case "link" -> linkCommand(player, args);
            case "unlink" -> linkCommand(player, new String[] {"link"});
            case "groups" -> menu.groups(player);
            case "group" -> groupCommand(player, args);
            case "priority" -> priorityCommand(player, args);
            case "list" -> lang.send(player, "messages.list", Map.of("count", String.valueOf(store.count(player.getUniqueId())), "chunks", String.join(", ", store.ownedBy(player.getUniqueId()).stream().map(this::format).toList())));
            case "remove" -> removeCommand(player, args);
            case "reload" -> {
                if (!player.hasPermission("chubby.admin")) {
                    noPermission(player);
                } else {
                    releaseChunkTickets();
                    reloadConfig();
                    lang.load();
                    store.load();
                    startPerformanceProtection();
                    startWarningMonitor();
                    updatePerformanceState();
                    restoreChunkTickets();
                    lang.send(player, "messages.config-reloaded");
                }
            }
            default -> menu.help(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        lang.lore("help.lines", Map.of()).forEach(player::sendMessage);
    }

    private void sendDiagnostics(Player player) {
        if (!player.hasPermission("chubby.admin")) { noPermission(player); return; }
        long active = activeTicketCount();
        long suspended = store.all().keySet().stream().filter(this::isChunkSuspended).count();
        lang.send(player, "diagnostics.line");
        lang.send(player, "diagnostics.title");
        lang.send(player, "diagnostics.status", Map.of("tps_color", currentTps() >= 18 ? "&a" : "&c", "tps", String.format(Locale.ROOT, "%.2f", currentTps()), "protection", lang.text(isLoadProtected() ? "diagnostics.protection-active" : "diagnostics.protection-normal")));
        lang.send(player, "diagnostics.counts", Map.of("selected", String.valueOf(store.all().size()), "active", String.valueOf(active), "paused", String.valueOf(suspended)));
        List<Map.Entry<ChunkKey, ChunkLoadEstimate>> busiest = store.all().keySet().stream()
                .map(key -> Map.entry(key, estimate(key))).filter(entry -> entry.getValue().available())
                .sorted(Comparator.comparingInt((Map.Entry<ChunkKey, ChunkLoadEstimate> entry) -> entry.getValue().score()).reversed()).limit(5).toList();
        if (busiest.isEmpty()) lang.send(player, "diagnostics.no-chunks");
        else for (Map.Entry<ChunkKey, ChunkLoadEstimate> entry : busiest) lang.send(player, "diagnostics.chunk", Map.of("chunk", format(entry.getKey()), "grade", entry.getValue().grade(), "score", String.valueOf(entry.getValue().score())));
        lang.send(player, "diagnostics.line");
    }

    private void groupCommand(Player player, String[] args) {
        if (!player.hasPermission("chubby.manage")) { noPermission(player); return; }
        if (args.length < 3) { lang.send(player, "messages.usage-group"); return; }
        String group = args[1];
        List<ChunkKey> members = store.ownedBy(player.getUniqueId()).stream().filter(key -> store.group(key).equalsIgnoreCase(group)).toList();
        if (members.isEmpty()) { lang.send(player, "messages.group-empty"); return; }
        String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("pause") || action.equals("resume")) {
            store.setGroupPaused(player.getUniqueId(), group, action.equals("pause"));
            members.forEach(key -> setForced(key, !isChunkSuspended(key))); store.save();
            lang.send(player, action.equals("pause") ? "messages.group-paused" : "messages.group-resumed");
        } else if (action.equals("priority") && args.length == 4) {
            int priority = switch (args[3].toLowerCase(Locale.ROOT)) { case "low" -> 0; case "high" -> 2; case "normal" -> 1; default -> -1; };
            if (priority < 0) { lang.send(player, "messages.invalid-priority"); return; }
            members.forEach(key -> { store.setPriority(key, priority); setForced(key, !isChunkSuspended(key)); }); store.save();
            lang.send(player, "messages.group-priority", Map.of("priority", priorityName(priority)));
        } else lang.send(player, "messages.usage-group");
    }

    private void linkCommand(Player player, String[] args) {
        if (!player.hasPermission("chubby.manage")) { noPermission(player); return; }
        ChunkKey key = ChunkKey.of(player.getLocation().getChunk());
        if (!store.contains(key)) { lang.send(player, "messages.chunk-not-selected"); return; }
        if (!store.owner(key).equals(player.getUniqueId()) && !player.hasPermission("chubby.admin")) { noPermission(player); return; }
        String group = args.length < 2 ? "" : String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        int maxLength = Math.clamp(getConfig().getInt("groups.max-name-length", 24), 3, 48);
        if (group.length() > maxLength) { lang.send(player, "messages.group-too-long", Map.of("limit", String.valueOf(maxLength))); return; }
        store.setGroup(key, group); store.save();
        lang.send(player, group.isBlank() ? "messages.chunk-unlinked" : "messages.chunk-linked", Map.of("group", group));
    }

    private void priorityCommand(Player player, String[] args) {
        if (!player.hasPermission("chubby.manage")) { noPermission(player); return; }
        if (args.length != 2) { lang.send(player, "messages.usage-priority"); return; }
        ChunkKey key = ChunkKey.of(player.getLocation().getChunk());
        if (!store.contains(key)) { lang.send(player, "messages.chunk-not-selected"); return; }
        if (!store.owner(key).equals(player.getUniqueId()) && !player.hasPermission("chubby.admin")) { noPermission(player); return; }
        int priority = switch (args[1].toLowerCase(Locale.ROOT)) { case "low" -> 0; case "high" -> 2; case "normal" -> 1; default -> -1; };
        if (priority < 0) { lang.send(player, "messages.invalid-priority"); return; }
        store.setPriority(key, priority); setForced(key, !isChunkSuspended(key)); store.save();
        lang.send(player, "messages.priority-set", Map.of("priority", priorityName(priority)));
    }

    private void removeCommand(Player player, String[] args) {
        if (!player.hasPermission("chubby.admin")) { noPermission(player); return; }
        if (args.length != 4) { lang.send(player, "messages.usage-remove"); return; }
        try {
            ChunkKey key = new ChunkKey(args[1], Integer.parseInt(args[2]), Integer.parseInt(args[3]));
            if (!store.contains(key)) { lang.send(player, "messages.chunk-not-selected"); return; }
            store.remove(key); setForced(key, false); store.save(); lang.send(player, "messages.chunk-removed", Map.of("chunk", format(key)));
        } catch (NumberFormatException ex) { lang.send(player, "messages.invalid-coordinates"); }
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() >= event.getInventory().getSize()) return;
        int slot = event.getRawSlot();
        if (holder.view() == MenuHolder.View.DASHBOARD) handleDashboardClick(player, holder, slot);
        else if (holder.view() == MenuHolder.View.LIST) handleListClick(player, holder, slot);
        else if (holder.view() == MenuHolder.View.INFO) handleInfoClick(player, holder, slot);
        else if (holder.view() == MenuHolder.View.MAP) handleMapClick(player, holder, slot);
        else if (holder.view() == MenuHolder.View.MANAGE) handleManageClick(player, holder, slot);
        else if (holder.view() == MenuHolder.View.GROUP) handleGroupClick(player, holder, slot);
        else if (holder.view() == MenuHolder.View.GROUP_LIST) handleGroupListClick(player, holder, slot);
        else if (holder.view() == MenuHolder.View.DIAGNOSTICS) handleDiagnosticsClick(player, holder, slot);
        else if (holder.view() == MenuHolder.View.HELP) { if (slot == 49) menu.open(player); }
        else handleConfirmDeleteClick(player, holder, slot);
    }

    private void handleDashboardClick(Player player, MenuHolder holder, int slot) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35F, 1.2F);
        if (slot == 10) { toggle(player, ChunkKey.of(player.getLocation().getChunk())); menu.open(player); }
        else if (slot == 12) menu.manage(player);
        else if (slot == 14) visualize(player);
        else if (slot == 16) menu.open(player, 0, "");
        else if (slot == 28) menu.groups(player);
        else if (slot == 30) { player.closeInventory(); lang.send(player, "messages.search-hint"); }
        else if (slot == 32) { player.closeInventory(); sendHelp(player); }
        else if (slot == 34 && player.hasPermission("chubby.admin")) { Chunk chunk = player.getLocation().getChunk(); menu.map(player, chunk.getX(), chunk.getZ()); }
        else if (slot == 36 && player.hasPermission("chubby.admin")) { player.closeInventory(); sendDiagnostics(player); }
        else if (slot == 49) player.closeInventory();
    }

    private void handleListClick(Player player, MenuHolder holder, int slot) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35F, 1.2F);
        if (slot == 0) { toggle(player, ChunkKey.of(player.getLocation().getChunk())); menu.open(player, holder.page(), holder.filter()); }
        else if (slot == 1) visualize(player);
        else if (slot == 3) { player.closeInventory(); lang.send(player, "messages.search-hint"); }
        else if (slot == 7) menu.manage(player);
        else if (slot == 8 && player.hasPermission("chubby.admin")) { Chunk chunk = player.getLocation().getChunk(); menu.map(player, chunk.getX(), chunk.getZ()); }
        else if (slot == 45) menu.open(player, holder.page() - 1, holder.filter());
        else if (slot == 53) menu.open(player, holder.page() + 1, holder.filter());
        else if (slot == 49) player.closeInventory();
        else if (holder.chunkSlots().containsKey(slot)) menu.info(player, holder.chunkSlots().get(slot), holder.page(), holder.filter());
    }

    private void handleInfoClick(Player player, MenuHolder holder, int slot) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35F, 1.1F);
        if (slot == 18) menu.open(player, holder.page(), holder.filter());
        else if (slot == 22) player.closeInventory();
        else if (slot == 15 && holder.subject() != null) menu.confirmDelete(player, holder.subject(), holder.page(), holder.filter());
    }

    private void handleMapClick(Player player, MenuHolder holder, int slot) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35F, 1.1F);
        int x = holder.page(), z = Integer.parseInt(holder.filter());
        if (slot == 0) menu.map(player, x, z - 5);
        else if (slot == 1) menu.map(player, x - 7, z);
        else if (slot == 7) menu.map(player, x + 7, z);
        else if (slot == 8) menu.map(player, x, z + 5);
        else if (slot == 2) player.closeInventory();
        else if (holder.chunkSlots().containsKey(slot)) menu.info(player, holder.chunkSlots().get(slot), 0, "");
    }

    private void handleManageClick(Player player, MenuHolder holder, int slot) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35F, 1.1F);
        ChunkKey key = holder.subject();
        if (slot == 49) { menu.open(player); return; }
        if (key == null || !store.contains(key)) return;
        if (!store.owner(key).equals(player.getUniqueId()) && !player.hasPermission("chubby.admin")) { noPermission(player); return; }
        if (slot == 10 || slot == 13 || slot == 16) {
            int priority = slot == 10 ? 0 : slot == 13 ? 1 : 2;
            store.setPriority(key, priority); setForced(key, !isChunkSuspended(key)); store.save();
            lang.send(player, "messages.priority-set", Map.of("priority", priorityName(priority))); menu.manage(player);
        } else if (slot == 28) {
            store.setGroup(key, ""); store.save(); lang.send(player, "messages.chunk-unlinked"); menu.manage(player);
        } else if (slot == 29 && !store.group(key).isBlank()) {
            menu.groupActions(player, store.group(key));
        } else if (holder.groupSlots().containsKey(slot)) {
            String group = holder.groupSlots().get(slot); store.setGroup(key, group); store.save(); lang.send(player, "messages.chunk-linked", Map.of("group", group)); menu.manage(player);
        } else if (slot == 30) {
            player.closeInventory(); lang.send(player, "messages.new-group-hint");
        }
    }

    private void handleGroupClick(Player player, MenuHolder holder, int slot) {
        String group = holder.filter();
        if (slot == 18) { menu.manage(player); return; }
        if (group.isBlank()) return;
        List<ChunkKey> members = store.ownedBy(player.getUniqueId()).stream().filter(key -> store.group(key).equals(group)).toList();
        if (slot == 10 || slot == 13 || slot == 16) {
            int priority = slot == 10 ? 0 : slot == 13 ? 1 : 2;
            members.forEach(key -> { store.setPriority(key, priority); setForced(key, !isChunkSuspended(key)); }); store.save();
            lang.send(player, "messages.group-priority", Map.of("priority", priorityName(priority))); menu.groupActions(player, group);
        } else if (slot == 22) {
            boolean resume = members.stream().findFirst().map(store::isGroupPaused).orElse(false);
            store.setGroupPaused(player.getUniqueId(), group, !resume);
            members.forEach(key -> setForced(key, !isChunkSuspended(key))); store.save();
            lang.send(player, resume ? "messages.group-resumed" : "messages.group-paused"); menu.groupActions(player, group);
        }
    }

    private void handleGroupListClick(Player player, MenuHolder holder, int slot) {
        if (slot == 49) { menu.open(player); return; }
        if (holder.groupSlots().containsKey(slot)) menu.groupActions(player, holder.groupSlots().get(slot));
    }

    private void handleDiagnosticsClick(Player player, MenuHolder holder, int slot) {
        if (slot == 49) { menu.open(player); return; }
        if (holder.chunkSlots().containsKey(slot)) menu.info(player, holder.chunkSlots().get(slot), 0, "");
    }

    private void handleConfirmDeleteClick(Player player, MenuHolder holder, int slot) {
        if (holder.subject() == null) return;
        if (slot == 11) { toggle(player, holder.subject()); menu.open(player, holder.page(), holder.filter()); }
        else if (slot == 15) menu.info(player, holder.subject(), holder.page(), holder.filter());
    }

    @EventHandler public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("menu", "toggle", "show", "help", "diagnostics", "search", "map", "link", "unlink", "groups", "group", "priority", "list", "remove", "reload").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("priority")) return List.of("low", "normal", "high").stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("group")) return List.of("priority", "pause", "resume").stream().filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 4 && args[0].equalsIgnoreCase("group") && args[2].equalsIgnoreCase("priority")) return List.of("low", "normal", "high").stream().filter(s -> s.startsWith(args[3].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
