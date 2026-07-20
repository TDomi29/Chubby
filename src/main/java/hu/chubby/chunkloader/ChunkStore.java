package hu.chubby.chunkloader;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class ChunkStore {
    public record ChunkSettings(UUID owner, String group, int priority) {
        public ChunkSettings {
            group = group == null ? "" : group;
            priority = Math.clamp(priority, 0, 2);
        }
    }

    private final File file;
    private final Map<ChunkKey, ChunkSettings> chunks = new HashMap<>();
    private final Set<String> pausedGroups = new HashSet<>();
    private final Map<ChunkKey, String> persistedKeys = new HashMap<>();
    private YamlConfiguration yaml;

    public ChunkStore(File dataFolder) { this.file = new File(dataFolder, "chunks.yml"); }

    public void load() {
        chunks.clear();
        pausedGroups.clear();
        persistedKeys.clear();
        yaml = YamlConfiguration.loadConfiguration(file);
        boolean migrated = false;
        ConfigurationSection section = yaml.getConfigurationSection("chunks");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    ChunkKey chunkKey = ChunkKey.parse(key);
                    Object raw = section.get(key);
                    // Earlier versions stored a chunk directly as its owner's UUID.
                    if (raw instanceof String owner) {
                        chunks.put(chunkKey, new ChunkSettings(UUID.fromString(owner), "", 1));
                        persistedKeys.put(chunkKey, key);
                        migrated = true;
                    } else if (raw instanceof ConfigurationSection details) {
                        String owner = details.getString("owner");
                        if (owner == null || owner.isBlank()) continue;
                        chunks.put(chunkKey, new ChunkSettings(UUID.fromString(owner), details.getString("group", ""), details.getInt("priority", 1)));
                        persistedKeys.put(chunkKey, key);
                        migrated |= !details.contains("group") || !details.contains("priority");
                    }
                } catch (IllegalArgumentException ignored) {
                    // Leave malformed or future-version entries untouched in the source file.
                }
            }
        }
        ConfigurationSection pausedSection = yaml.getConfigurationSection("paused-groups");
        if (pausedSection != null) for (String owner : pausedSection.getKeys(false)) {
            for (String group : pausedSection.getStringList(owner)) pausedGroups.add(owner + "\u0000" + group);
        }
        if (migrated || !yaml.contains("data-version")) save();
    }

    public void save() {
        if (yaml == null) yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection chunkSection = yaml.getConfigurationSection("chunks");
        if (chunkSection == null) chunkSection = yaml.createSection("chunks");
        for (Map.Entry<ChunkKey, String> entry : new HashMap<>(persistedKeys).entrySet()) {
            if (!chunks.containsKey(entry.getKey())) chunkSection.set(entry.getValue(), null);
        }
        for (Map.Entry<ChunkKey, ChunkSettings> entry : chunks.entrySet()) {
            String key = persistedKeys.getOrDefault(entry.getKey(), entry.getKey().configKey());
            // Rebuild only entries managed by this plugin; unrelated YAML data remains intact.
            chunkSection.set(key, null);
            ConfigurationSection details = chunkSection.createSection(key);
            ChunkSettings settings = entry.getValue();
            details.set("owner", settings.owner().toString());
            details.set("group", settings.group());
            details.set("priority", settings.priority());
            persistedKeys.put(entry.getKey(), key);
        }
        Map<String, List<String>> pausedByOwner = new HashMap<>();
        for (String value : pausedGroups) {
            int separator = value.indexOf('\u0000');
            pausedByOwner.computeIfAbsent(value.substring(0, separator), ignored -> new ArrayList<>()).add(value.substring(separator + 1));
        }
        yaml.set("paused-groups", null);
        pausedByOwner.forEach((owner, groups) -> yaml.set("paused-groups." + owner, groups));
        yaml.set("data-version", 2);
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) throw new IllegalStateException("Could not create Chubby data folder");
        try { yaml.save(file); } catch (IOException exception) { throw new IllegalStateException("Could not save chunks.yml", exception); }
    }

    public boolean contains(ChunkKey key) { return chunks.containsKey(key); }
    public UUID owner(ChunkKey key) { return chunks.get(key).owner(); }
    public String group(ChunkKey key) { return chunks.get(key).group(); }
    public int priority(ChunkKey key) { return chunks.get(key).priority(); }
    public void add(ChunkKey key, UUID owner) { chunks.put(key, new ChunkSettings(owner, "", 1)); }
    public void remove(ChunkKey key) { chunks.remove(key); }
    public void setGroup(ChunkKey key, String group) { update(key, new ChunkSettings(owner(key), group, priority(key))); }
    public void setPriority(ChunkKey key, int priority) { update(key, new ChunkSettings(owner(key), group(key), priority)); }
    private void update(ChunkKey key, ChunkSettings settings) { if (contains(key)) chunks.put(key, settings); }
    public Map<ChunkKey, ChunkSettings> all() { return Collections.unmodifiableMap(chunks); }
    public long count(UUID player) { return chunks.values().stream().filter(settings -> player.equals(settings.owner())).count(); }
    public List<ChunkKey> ownedBy(UUID player) {
        return chunks.entrySet().stream().filter(entry -> entry.getValue().owner().equals(player)).map(Map.Entry::getKey)
                .sorted(Comparator.comparing(ChunkKey::worldName).thenComparingInt(ChunkKey::x).thenComparingInt(ChunkKey::z)).toList();
    }
    public Set<String> groups(UUID owner) {
        return chunks.values().stream().filter(settings -> settings.owner().equals(owner)).map(ChunkSettings::group)
                .filter(group -> !group.isBlank()).collect(java.util.stream.Collectors.toCollection(TreeSet::new));
    }
    public boolean isGroupPaused(ChunkKey key) {
        return contains(key) && !group(key).isBlank() && pausedGroups.contains(groupKey(owner(key), group(key)));
    }
    public void setGroupPaused(UUID owner, String group, boolean paused) {
        if (group.isBlank()) return;
        if (paused) pausedGroups.add(groupKey(owner, group)); else pausedGroups.remove(groupKey(owner, group));
    }
    private String groupKey(UUID owner, String group) { return owner + "\u0000" + group; }
}
