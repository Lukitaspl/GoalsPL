package com.plgroup.goalspl.storage;

import com.plgroup.goalspl.domain.Campaign;
import com.plgroup.goalspl.domain.CampaignCodec;
import com.plgroup.goalspl.domain.Problem;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * goals.yml: las definiciones de las campañas, legibles y editables a mano. Si una campaña tiene un valor que no
 * se entiende, no se carga, pero su sección se conserva tal cual al volver a guardar. Si el archivo entero no se
 * puede leer, se bloquea el guardado desde el juego para no pisarlo.
 */
public final class GoalFiles {

    /** Campañas leídas y problemas por campaña. */
    public record LoadResult(Map<String, Campaign> campaigns, Map<String, List<Problem>> problems) {
    }

    private final File file;
    private final Logger log;
    private final Map<String, ConfigurationSection> broken = new LinkedHashMap<>();
    private boolean readOnly;

    public GoalFiles(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public File file() {
        return file;
    }

    public boolean readOnly() {
        return readOnly;
    }

    public LoadResult load(ZoneId zone) {
        Map<String, Campaign> out = new LinkedHashMap<>();
        Map<String, List<Problem>> problems = new LinkedHashMap<>();
        broken.clear();
        readOnly = false;
        if (!file.exists()) {
            return new LoadResult(out, problems);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            readOnly = true;
            log.severe("goals.yml no se puede leer (" + e.getMessage().lines().findFirst().orElse("") + "). No se carga"
                    + " ninguna campaña y no se guardarán cambios desde el juego hasta que se corrija y se use /goals reload.");
            return new LoadResult(out, problems);
        }
        ConfigurationSection root = yaml.getConfigurationSection("goals");
        if (root == null) {
            return new LoadResult(out, problems);
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            CampaignCodec.Read read = CampaignCodec.read(id, s, zone);
            if (!read.problems().isEmpty()) {
                problems.put(id, read.problems());
            }
            if (read.campaign() == null) {
                broken.put(id, s);
            } else {
                out.put(id, read.campaign());
            }
        }
        return new LoadResult(out, problems);
    }

    public void logProblems(Map<String, List<Problem>> problems) {
        problems.forEach((id, list) -> {
            for (Problem p : list) {
                String text = "goals.yml, campaña '" + id + "': " + p.key() + (p.detail().isEmpty() ? "" : " (" + p.detail() + ")");
                if (p.error()) {
                    log.severe(text + " - la campaña no se carga hasta corregirlo.");
                } else {
                    log.warning(text);
                }
            }
        });
    }

    /** Texto completo de goals.yml con estas campañas y las que no se pudieron leer. */
    public String render(Collection<Campaign> campaigns, ZoneId zone) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Campañas de GoalsPL. Lo normal es crearlas y editarlas con /goals admin.",
                "Si editas a mano, usa /goals reload. No cambies el ID (la clave) de una campaña: el progreso va unido a él.",
                "Formato de cada campo: ver docs/es/configuracion.md en el repositorio."));
        List<Campaign> sorted = new ArrayList<>(campaigns);
        for (Campaign c : sorted) {
            CampaignCodec.write(c, yaml.createSection("goals." + c.id), zone);
        }
        broken.forEach((id, section) -> {
            if (!yaml.contains("goals." + id)) {
                yaml.set("goals." + id, section);
            }
        });
        return yaml.saveToString();
    }
}
