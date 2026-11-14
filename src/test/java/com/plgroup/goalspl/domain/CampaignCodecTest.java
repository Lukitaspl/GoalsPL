package com.plgroup.goalspl.domain;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** goals.yml: escribir y volver a leer da lo mismo; los valores no válidos impiden cargar la campaña. */
class CampaignCodecTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Test
    void roundTripKeepsEverything() throws Exception {
        Campaign c = new Campaign("mining");
        c.name = "<gray>Mina";
        c.description = "Picad";
        c.icon = "IRON_PICKAXE";
        c.type = ObjectiveType.BREAK_BLOCK;
        c.targets = new ArrayList<>(List.of("IRON_ORE", "GOLD_ORE"));
        c.goal = 2000;
        c.worlds = new ArrayList<>(List.of("world"));
        c.durationSeconds = 7 * 86400 + 3600;
        c.deadline = CampaignCodec.parseDate("2026-12-31 23:59", MADRID);
        c.minContribution = 10;
        c.maxContribution = 300;
        c.endPolicy = EndPolicy.REFUND;
        c.next = "harvest";
        c.announceStart = false;
        c.startMessage = "<green>Empieza {goal_name}";
        c.bossbarColor = "BLUE";
        c.bossbarOverlay = "NOTCHED_10";
        Milestone m = new Milestone(50);
        m.policy = RewardPolicy.COMMUNITY;
        m.announce = false;
        m.rewards.add(new Reward(RewardType.ITEM, "DIAMOND 2"));
        m.rewards.add(new Reward(RewardType.BROADCAST, "Mitad: {percent}%"));
        c.milestones.add(m);
        PersonalMilestone p = new PersonalMilestone(100);
        p.rewards.add(new Reward(RewardType.COMMAND, "eco give {player} 10"));
        c.personal.add(p);
        c.topRewards.put(1, new ArrayList<>(List.of(new Reward(RewardType.PERMISSION, "essentials.fly 1h"))));

        YamlConfiguration yaml = new YamlConfiguration();
        CampaignCodec.write(c, yaml.createSection("goals.mining"), MADRID);
        YamlConfiguration back = new YamlConfiguration();
        back.loadFromString(yaml.saveToString());
        CampaignCodec.Read read = CampaignCodec.read("mining", back.getConfigurationSection("goals.mining"), MADRID);

        assertTrue(read.problems().isEmpty(), "Problemas: " + read.problems());
        Campaign r = read.campaign();
        assertNotNull(r);
        assertEquals(c.name, r.name);
        assertEquals(c.type, r.type);
        assertEquals(c.targets, r.targets);
        assertEquals(c.goal, r.goal);
        assertEquals(c.worlds, r.worlds);
        assertEquals(c.durationSeconds, r.durationSeconds);
        assertEquals(c.deadline, r.deadline);
        assertEquals(c.maxContribution, r.maxContribution);
        assertEquals(EndPolicy.REFUND, r.endPolicy);
        assertEquals("harvest", r.next);
        assertEquals(false, r.announceStart);
        assertEquals(c.startMessage, r.startMessage);
        assertEquals("NOTCHED_10", r.bossbarOverlay);
        assertEquals(1, r.milestones.size());
        assertEquals(RewardPolicy.COMMUNITY, r.milestones.get(0).policy);
        assertEquals(false, r.milestones.get(0).announce);
        assertEquals(m.rewards, r.milestones.get(0).rewards);
        assertEquals(p.rewards, r.personal(100).rewards);
        assertEquals(c.topRewards.get(1), r.topRewards.get(1));
    }

    @Test
    void invalidValuesBlockTheCampaign() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                g:
                  type: CRAFT
                  duration: 7 weeks
                  deadline: mañana
                  milestones:
                    '150': {rewards: []}
                  personal:
                    '10': ['NOPE: x']
                """);
        CampaignCodec.Read read = CampaignCodec.read("bad_one", y.getConfigurationSection("g"), MADRID);
        assertNull(read.campaign());
        List<String> keys = read.problems().stream().map(Problem::key).toList();
        assertTrue(keys.containsAll(List.of("type-invalid", "duration-invalid", "deadline-invalid", "milestone-invalid",
                "reward-invalid")), "Claves: " + keys);
    }

    @Test
    void invalidIdIsRejected() {
        CampaignCodec.Read read = CampaignCodec.read("Con Espacios", new YamlConfiguration(), MADRID);
        assertNull(read.campaign());
        assertEquals("id-invalid", read.problems().get(0).key());
    }

    @Test
    void rewardParsing() {
        assertEquals(new Reward(RewardType.ITEM, "DIAMOND 3"), Reward.parse("item:  DIAMOND 3 "));
        assertEquals(new Reward(RewardType.MESSAGE, "Hola: qué tal"), Reward.parse("MESSAGE: Hola: qué tal"));
        assertNull(Reward.parse("DIAMOND 3"));
        assertNull(Reward.parse("FOO: x"));
    }
}
