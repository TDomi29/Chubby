package hu.chubby.chunkloader;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Loads user-editable language files and non-destructively upgrades missing keys. */
public final class Lang {
    private final ChubbyPlugin plugin;
    private YamlConfiguration messages;
    private YamlConfiguration englishDefaults;
    private String activeLanguage;

    public Lang(ChubbyPlugin plugin) { this.plugin = plugin; }

    public void load() {
        englishDefaults = bundled("en");
        String configuredLanguage = Optional.ofNullable(plugin.getConfig().getString("language", "en"))
                .orElse("en").trim();
        activeLanguage = configuredLanguage.equalsIgnoreCase("hu") || configuredLanguage.equalsIgnoreCase("hu_HU")
                ? "hu_HU" : configuredLanguage.toLowerCase(Locale.ROOT);
        File directory = new File(plugin.getDataFolder(), "lang");
        if (!directory.exists() && !directory.mkdirs()) plugin.getLogger().warning("Could not create the lang folder.");
        File file = new File(directory, activeLanguage + ".yml");
        if (!file.isFile()) {
            plugin.getLogger().warning("Language file lang/" + activeLanguage + ".yml is missing; falling back to en.yml.");
            activeLanguage = "en";
            file = new File(directory, "en.yml");
        }
        messages = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration defaults = bundled(activeLanguage);
        if ((activeLanguage.equals("en") || activeLanguage.equals("hu_HU"))
                && messages.getInt("lang-version", 0) < defaults.getInt("lang-version", 1)) {
            File backup = new File(directory, activeLanguage + ".legacy-" + System.currentTimeMillis() + ".yml");
            if (file.renameTo(backup)) {
                plugin.getLogger().warning("Outdated lang/" + activeLanguage + ".yml was backed up as " + backup.getName() + " and regenerated.");
                plugin.saveResource("lang/" + activeLanguage + ".yml", true);
                messages = YamlConfiguration.loadConfiguration(file);
            }
        }
        if (defaults.getKeys(true).isEmpty()) defaults = englishDefaults;
        boolean changed = mergeMissing(messages, defaults);
        // Custom languages inherit every missing key from English, so they never show a raw key.
        changed |= mergeMissing(messages, englishDefaults);
        if (changed) {
            try { messages.save(file); }
            catch (IOException exception) { plugin.getLogger().warning("Could not update lang/" + activeLanguage + ".yml: " + exception.getMessage()); }
        }
    }

    private YamlConfiguration bundled(String language) {
        try (InputStream stream = plugin.getResource("lang/" + language + ".yml")) {
            if (stream == null) return new YamlConfiguration();
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not read bundled lang/" + language + ".yml: " + exception.getMessage());
            return new YamlConfiguration();
        }
    }

    private boolean mergeMissing(YamlConfiguration target, YamlConfiguration source) {
        boolean changed = false;
        for (String key : source.getKeys(true)) {
            if (source.isConfigurationSection(key) || target.contains(key)) continue;
            target.set(key, source.get(key));
            changed = true;
        }
        return changed;
    }

    public String text(String key, Map<String, String> replacements) {
        String value = messages.getString(key);
        if (value == null) value = englishDefaults.getString(key, key);
        for (Map.Entry<String, String> entry : replacements.entrySet()) value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        return ChatColor.translateAlternateColorCodes('&', value);
    }
    public String text(String key) { return text(key, Map.of()); }
    public List<String> lore(String key, Map<String, String> replacements) {
        List<String> source = messages.getStringList(key);
        if (source.isEmpty()) source = englishDefaults.getStringList(key);
        return source.stream().map(line -> {
            for (Map.Entry<String, String> entry : replacements.entrySet()) line = line.replace("{" + entry.getKey() + "}", entry.getValue());
            return ChatColor.translateAlternateColorCodes('&', line);
        }).toList();
    }
    public void send(CommandSender target, String key, Map<String, String> replacements) { target.sendMessage(text(key, replacements)); }
    public void send(CommandSender target, String key) { send(target, key, Map.of()); }
    public void console(String key, Map<String, String> replacements) { Bukkit.getConsoleSender().sendMessage(text(key, replacements)); }
    public String activeLanguage() { return activeLanguage; }
}
