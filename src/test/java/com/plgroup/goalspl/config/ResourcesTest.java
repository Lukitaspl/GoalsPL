package com.plgroup.goalspl.config;

import com.plgroup.goalspl.domain.Campaign;
import com.plgroup.goalspl.domain.CampaignCodec;
import com.plgroup.goalspl.domain.CampaignValidator;
import com.plgroup.goalspl.domain.CampaignValidatorTest;
import com.plgroup.goalspl.domain.Problem;
import com.plgroup.goalspl.domain.Reward;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los archivos que van en el JAR (config.yml, goals.yml y rewards.yml) se leen sin errores. */
class ResourcesTest {

    private static YamlConfiguration load(String path) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                ResourcesTest.class.getResourceAsStream("/" + path), StandardCharsets.UTF_8));
    }

    @Test
    void configIsCurrentAndReadable() {
        YamlConfiguration c = load("config.yml");
        assertEquals(PluginConfig.VERSION, c.getInt("config-version"));
        PluginConfig p = PluginConfig.load(c, Logger.getLogger("test"));
        assertEquals("es", p.language);
        assertTrue(p.blocked("op Steve"));
        assertTrue(p.blocked("luckperms user x"));
        assertFalse(p.blocked("give Steve diamond"));
    }

    @Test
    void exampleGoalsAreValid() {
        ConfigurationSection goals = load("goals.yml").getConfigurationSection("goals");
        assertNotNull(goals);
        assertEquals(3, goals.getKeys(false).size());
        for (String id : goals.getKeys(false)) {
            CampaignCodec.Read read = CampaignCodec.read(id, goals.getConfigurationSection(id), ZoneId.of("UTC"));
            assertTrue(read.problems().isEmpty(), id + ": " + read.problems());
            Campaign c = read.campaign();
            assertNotNull(c);
            List<Problem> errors = CampaignValidator.validate(c, CampaignValidatorTest.ENV).stream().filter(Problem::error).toList();
            assertTrue(errors.isEmpty(), id + ": " + errors);
        }
    }

    @Test
    void rewardPresetsParse() {
        ConfigurationSection presets = load("rewards.yml").getConfigurationSection("presets");
        assertNotNull(presets);
        int count = 0;
        for (String id : presets.getKeys(false)) {
            for (String line : presets.getStringList(id + ".rewards")) {
                assertNotNull(Reward.parse(line), id + ": " + line);
                count++;
            }
        }
        assertTrue(count >= 5);
    }
}
