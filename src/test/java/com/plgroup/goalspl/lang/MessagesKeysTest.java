package com.plgroup.goalspl.lang;

import com.plgroup.goalspl.command.GoalsCommand;
import com.plgroup.goalspl.domain.CampaignState;
import com.plgroup.goalspl.domain.EndPolicy;
import com.plgroup.goalspl.domain.HistoryAction;
import com.plgroup.goalspl.domain.ObjectiveType;
import com.plgroup.goalspl.domain.RewardPolicy;
import com.plgroup.goalspl.domain.RewardType;
import com.plgroup.goalspl.runtime.CampaignManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los dos idiomas tienen las mismas claves y existe toda clave que usa el código: las literales (mensajes y
 * botones), las que salen de enumerados, las de validación y las de ayuda de cada subcomando.
 */
class MessagesKeysTest {

    private static YamlConfiguration load(String lang) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                MessagesKeysTest.class.getResourceAsStream("/lang/messages_" + lang + ".yml"), StandardCharsets.UTF_8));
    }

    private static Set<String> leafKeys(YamlConfiguration y) {
        Set<String> out = new TreeSet<>();
        for (String k : y.getKeys(true)) {
            if (!y.isConfigurationSection(k)) {
                out.add(k);
            }
        }
        return out;
    }

    @Test
    void languagesHaveSameKeys() {
        Set<String> es = leafKeys(load("es"));
        Set<String> en = leafKeys(load("en"));
        Set<String> onlyEs = new TreeSet<>(es);
        onlyEs.removeAll(en);
        Set<String> onlyEn = new TreeSet<>(en);
        onlyEn.removeAll(es);
        assertTrue(onlyEs.isEmpty(), "Solo en es: " + onlyEs);
        assertTrue(onlyEn.isEmpty(), "Solo en en: " + onlyEn);
    }

    @Test
    void literalKeysInCodeExist() throws IOException {
        YamlConfiguration es = load("es");
        Pattern call = Pattern.compile("\\b(?:send|text|item|lore|line|msg|raw|rawList|ask)\\([^\"\\n]*?\"([a-z][a-z0-9\\-]*(?:\\.[a-zA-Z0-9_\\-]+)+)\"");
        Pattern button = Pattern.compile("Gui\\.button\\(plugin, [^,]+, \"([a-z0-9.\\-]+)\"");
        Pattern ternaryKey = Pattern.compile("\\? \"((?:gui|result|command|deliver|announce|rewards)\\.[a-z0-9.\\-]+)\"\\s*:\\s*\"((?:gui|result|command|deliver|announce|rewards)\\.[a-z0-9.\\-]+)\"");
        int checked = 0;
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(f);
                Matcher m = call.matcher(src);
                while (m.find()) {
                    String key = m.group(1);
                    if (key.endsWith(".yml") || key.endsWith(".java")) {
                        continue;
                    }
                    checked++;
                    if (!es.contains(key)) {
                        missing.add(f.getFileName() + ": " + key);
                    }
                }
                Matcher b = button.matcher(src);
                while (b.find()) {
                    for (String suffix : List.of(".name", ".lore")) {
                        checked++;
                        if (!es.contains(b.group(1) + suffix)) {
                            missing.add(f.getFileName() + ": " + b.group(1) + suffix);
                        }
                    }
                }
                Matcher t = ternaryKey.matcher(src);
                while (t.find()) {
                    for (int g = 1; g <= 2; g++) {
                        String key = t.group(g);
                        checked++;
                        if (!es.contains(key) && !es.contains(key + ".name")) {
                            missing.add(f.getFileName() + ": " + key);
                        }
                    }
                }
            }
        }
        assertTrue(checked > 200, "Solo se revisaron " + checked + " claves: la expresión no encuentra las llamadas");
        assertTrue(missing.isEmpty(), "Claves que faltan: " + missing);
    }

    @Test
    void enumKeysExist() {
        YamlConfiguration es = load("es");
        List<String> missing = new ArrayList<>();
        check(es, "state.", CampaignState.values(), missing);
        check(es, "objective.", ObjectiveType.values(), missing);
        check(es, "objective-help.", ObjectiveType.values(), missing);
        check(es, "policy.", RewardPolicy.values(), missing);
        check(es, "end-policy.", EndPolicy.values(), missing);
        check(es, "reward-type.", RewardType.values(), missing);
        check(es, "reward-lore.", RewardType.values(), missing);
        check(es, "reward-help.", RewardType.values(), missing);
        check(es, "history-action.", HistoryAction.values(), missing);
        check(es, "result.", CampaignManager.Result.values(), missing);
        assertTrue(missing.isEmpty(), "Claves que faltan: " + missing);
    }

    private static void check(YamlConfiguration y, String prefix, Enum<?>[] values, List<String> missing) {
        for (Enum<?> v : values) {
            if (!y.contains(prefix + v.name())) {
                missing.add(prefix + v.name());
            }
        }
    }

    @Test
    void validationKeysExist() throws IOException {
        YamlConfiguration es = load("es");
        List<String> missing = new ArrayList<>();
        int found = 0;
        for (String file : List.of("CampaignValidator.java", "CampaignCodec.java")) {
            String src = Files.readString(Path.of("src/main/java/com/plgroup/goalspl/domain/" + file));
            Matcher m = Pattern.compile("Problem\\.(?:error|warning)\\(\"([a-z\\-]+)\"|\"([a-z\\-]+-invalid)\"").matcher(src);
            while (m.find()) {
                found++;
                String key = m.group(1) != null ? m.group(1) : m.group(2);
                if (!es.contains("validation." + key)) {
                    missing.add(key);
                }
            }
        }
        assertTrue(found > 30, "No se encontraron las claves del validador (" + found + ")");
        assertTrue(missing.isEmpty(), "Faltan claves de validación: " + missing);
    }

    @Test
    void everySubcommandHasHelp() {
        YamlConfiguration es = load("es");
        for (String name : GoalsCommand.NAMES) {
            assertTrue(es.contains("help.usage." + name), "Falta help.usage." + name);
            assertTrue(es.contains("help.description." + name), "Falta help.description." + name);
        }
    }

    @Test
    void languageFieldMatchesFile() {
        for (String lang : Messages.LANGUAGES) {
            assertEquals(lang, load(lang).getString("language", "").toLowerCase(Locale.ROOT));
        }
    }
}
