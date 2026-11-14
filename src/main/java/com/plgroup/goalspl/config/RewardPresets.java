package com.plgroup.goalspl.config;

import com.plgroup.goalspl.domain.Reward;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * rewards.yml: recompensas preparadas con nombre (diamantes, dinero, vuelo una hora…) que se añaden con un clic
 * desde el editor de recompensas, en lugar de escribirlas cada vez.
 */
public final class RewardPresets {

    /** Una plantilla: nombre visible, icono y recompensas. */
    public record Preset(String id, String name, String icon, List<Reward> rewards) {
    }

    private final Map<String, Preset> presets = new LinkedHashMap<>();

    /** Lee rewards.yml (lo crea la primera vez); devuelve los avisos. */
    public List<String> load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "rewards.yml");
        if (!file.exists()) {
            plugin.saveResource("rewards.yml", false);
        }
        List<String> warnings = new ArrayList<>();
        presets.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("presets");
        if (root == null) {
            return warnings;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            List<Reward> rewards = new ArrayList<>();
            for (String line : s.getStringList("rewards")) {
                Reward r = Reward.parse(line);
                if (r == null) {
                    warnings.add("rewards.yml, plantilla '" + id + "': recompensa no válida '" + line + "' (formato TIPO: valor).");
                } else {
                    rewards.add(r);
                }
            }
            presets.put(id, new Preset(id, s.getString("name", id), s.getString("icon", "CHEST"), rewards));
        }
        return warnings;
    }

    public List<Preset> all() {
        return new ArrayList<>(presets.values());
    }
}
