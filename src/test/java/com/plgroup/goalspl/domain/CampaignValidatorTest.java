package com.plgroup.goalspl.domain;

import com.plgroup.goalspl.config.PluginConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Errores y avisos del validador con un servidor simulado. */
public class CampaignValidatorTest {

    /** Servidor de prueba: unas pocas entidades, bloques, objetos y mundos. */
    public static final CampaignValidator.Env ENV = new CampaignValidator.Env() {
        @Override
        public boolean entity(String name) {
            return Set.of("ZOMBIE", "HUSK", "DROWNED", "ZOMBIE_VILLAGER", "SKELETON").contains(name);
        }

        @Override
        public boolean block(String name) {
            return name.endsWith("_ORE") || name.equals("WHEAT");
        }

        @Override
        public boolean item(String name) {
            return Set.of("DIAMOND", "WHEAT", "CARROT", "PAPER", "NETHER_STAR", "ZOMBIE_HEAD", "IRON_PICKAXE", "IRON_INGOT",
                    "GOLD_INGOT", "BREAD", "CAKE", "GOLDEN_APPLE").contains(name);
        }

        @Override
        public boolean world(String name) {
            return name.equals("world");
        }

        @Override
        public boolean campaign(String id) {
            return Set.of("zombie_hunt", "mining_week", "harvest_donation").contains(id);
        }

        @Override
        public boolean eventsPl() {
            return false;
        }

        @Override
        public boolean blockedCommand(String command) {
            return PluginConfig.blocked(command, List.of("op", "stop"));
        }

        @Override
        public long now() {
            return 1_000_000;
        }
    };

    private static List<String> keys(List<Problem> problems, boolean errors) {
        return problems.stream().filter(p -> p.error() == errors).map(Problem::key).toList();
    }

    private static Campaign valid() {
        Campaign c = new Campaign("valid_one");
        c.type = ObjectiveType.KILL_ENTITY;
        c.targets.add("ZOMBIE");
        c.goal = 100;
        Milestone m = new Milestone(100);
        m.rewards.add(new Reward(RewardType.ITEM, "DIAMOND 1"));
        c.milestones.add(m);
        return c;
    }

    @Test
    void validCampaignHasNoProblems() {
        assertTrue(CampaignValidator.validate(valid(), ENV).isEmpty());
    }

    @Test
    void errorsAreReported() {
        Campaign c = valid();
        c.name = " ";
        c.goal = 0;
        c.targets.add("DRAGON_OF_DOOM");
        c.minContribution = 50;
        c.maxContribution = 10;
        c.next = c.id;
        c.bossbarColor = "ORANGE";
        c.milestones.add(new Milestone(100));
        c.milestones.get(0).rewards.add(new Reward(RewardType.COMMAND, "/op {player}"));
        c.milestones.get(0).rewards.add(new Reward(RewardType.ITEM, "DIAMOND lots"));
        c.milestones.get(0).rewards.add(new Reward(RewardType.PERMISSION, "fly"));
        List<String> errors = keys(CampaignValidator.validate(c, ENV), true);
        assertTrue(errors.containsAll(List.of("name-empty", "goal-invalid", "target-unknown", "min-above-max", "next-self",
                "bossbar-color-invalid", "milestone-duplicate", "command-blocked", "item-invalid", "permission-invalid")),
                "Errores: " + errors);
    }

    @Test
    void deliveryNeedsTargets() {
        Campaign c = valid();
        c.type = ObjectiveType.DELIVER_ITEM;
        c.targets.clear();
        assertTrue(keys(CampaignValidator.validate(c, ENV), true).contains("targets-empty"));
    }

    @Test
    void warningsDoNotBlock() {
        Campaign c = valid();
        c.targets.clear();
        c.worlds.add("world_missing");
        c.deadline = 10;
        c.maxContribution = 10;
        c.next = "later";
        c.startMessage = "{jugador} empieza";
        c.milestones.get(0).rewards.add(new Reward(RewardType.EVENT, "doble_xp"));
        c.milestones.get(0).rewards.add(new Reward(RewardType.GLOBAL_COMMAND, "say {player}"));
        PersonalMilestone p = new PersonalMilestone(50);
        p.rewards.add(new Reward(RewardType.BROADCAST, "hola"));
        c.personal.add(p);
        List<Problem> problems = CampaignValidator.validate(c, ENV);
        assertFalse(problems.stream().anyMatch(Problem::error), "Errores: " + problems);
        assertTrue(keys(problems, false).containsAll(List.of("targets-any", "world-unknown", "deadline-past", "max-needs-players",
                "next-unknown", "placeholder-unknown", "events-missing", "global-player", "personal-global",
                "personal-unreachable")), "Avisos: " + keys(problems, false));
    }

    @Test
    void blockedCommandsMatchNamespacesAndSlash() {
        List<String> blocked = List.of("op", "stop");
        assertTrue(PluginConfig.blocked("/op Steve", blocked));
        assertTrue(PluginConfig.blocked("minecraft:stop", blocked));
        assertTrue(PluginConfig.blocked("  OP x", blocked));
        assertFalse(PluginConfig.blocked("opme", blocked));
        assertFalse(PluginConfig.blocked("give Steve diamond", blocked));
    }
}
