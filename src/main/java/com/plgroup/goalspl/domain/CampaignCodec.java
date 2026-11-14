package com.plgroup.goalspl.domain;

import com.plgroup.goalspl.util.Durations;
import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lee y escribe una campaña en goals.yml. Lo que no se entiende (un tipo que no existe, una duración mal escrita…)
 * se devuelve como problema de lectura y la campaña no se carga, pero su sección se conserva en el archivo.
 * La fecha límite se escribe en hora local de la zona de config.yml: {@code 2026-12-31 23:59}.
 */
public final class CampaignCodec {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    /** Resultado de leer una sección: la campaña ({@code null} si hay errores) y los problemas. */
    public record Read(Campaign campaign, List<Problem> problems) {
    }

    private CampaignCodec() {
    }

    public static Read read(String id, ConfigurationSection s, ZoneId zone) {
        List<Problem> problems = new ArrayList<>();
        if (!Campaign.validId(id)) {
            problems.add(Problem.error("id-invalid", id));
            return new Read(null, problems);
        }
        Campaign c = new Campaign(id);
        c.name = s.getString("name", id);
        c.description = s.getString("description", "");
        c.icon = s.getString("icon", c.icon).toUpperCase(Locale.ROOT);
        c.type = parseEnum(ObjectiveType.class, s.getString("type", "KILL_ENTITY"), "type-invalid", problems, c.type);
        c.targets = new ArrayList<>(s.getStringList("targets").stream().map(t -> t.strip().toUpperCase(Locale.ROOT)).toList());
        c.goal = s.getLong("goal", c.goal);
        c.worlds = new ArrayList<>(s.getStringList("worlds"));
        String duration = s.getString("duration", "0");
        long secs = Durations.parse(duration);
        if (secs < 0) {
            problems.add(Problem.error("duration-invalid", duration));
        } else {
            c.durationSeconds = secs;
        }
        String deadline = s.getString("deadline", "");
        if (!deadline.isBlank()) {
            long at = parseDate(deadline, zone);
            if (at < 0) {
                problems.add(Problem.error("deadline-invalid", deadline));
            } else {
                c.deadline = at;
            }
        }
        c.minContribution = s.getLong("min-contribution", c.minContribution);
        c.maxContribution = s.getLong("max-contribution", c.maxContribution);
        c.endPolicy = parseEnum(EndPolicy.class, s.getString("end-policy", "KEEP"), "end-policy-invalid", problems, c.endPolicy);
        c.next = Campaign.normalizeId(s.getString("next", ""));
        c.announceStart = s.getBoolean("announce.start", true);
        c.announceEnd = s.getBoolean("announce.end", true);
        c.startMessage = s.getString("messages.start", "");
        c.completeMessage = s.getString("messages.complete", "");
        c.bossbar = s.getBoolean("bossbar.enabled", true);
        c.bossbarColor = s.getString("bossbar.color", "GREEN").toUpperCase(Locale.ROOT);
        c.bossbarOverlay = s.getString("bossbar.overlay", "PROGRESS").toUpperCase(Locale.ROOT);

        ConfigurationSection ms = s.getConfigurationSection("milestones");
        if (ms != null) {
            for (String key : ms.getKeys(false)) {
                int percent = parseInt(key);
                ConfigurationSection m = ms.getConfigurationSection(key);
                if (percent < 1 || percent > 100 || m == null) {
                    problems.add(Problem.error("milestone-invalid", key));
                    continue;
                }
                Milestone milestone = new Milestone(percent);
                milestone.policy = parseEnum(RewardPolicy.class, m.getString("policy", "PARTICIPANTS"), "policy-invalid",
                        problems, milestone.policy);
                milestone.announce = m.getBoolean("announce", true);
                milestone.rewards = rewards(m.getStringList("rewards"), problems);
                c.milestones.add(milestone);
            }
        }
        ConfigurationSection ps = s.getConfigurationSection("personal");
        if (ps != null) {
            for (String key : ps.getKeys(false)) {
                long amount = parseInt(key);
                if (amount < 1) {
                    problems.add(Problem.error("personal-invalid", key));
                    continue;
                }
                PersonalMilestone p = new PersonalMilestone(amount);
                p.rewards = rewards(ps.getStringList(key), problems);
                c.personal.add(p);
            }
        }
        ConfigurationSection ts = s.getConfigurationSection("top");
        if (ts != null) {
            for (String key : ts.getKeys(false)) {
                int rank = parseInt(key);
                if (rank < 1 || rank > 54) {
                    problems.add(Problem.error("top-invalid", key));
                    continue;
                }
                c.topRewards.put(rank, rewards(ts.getStringList(key), problems));
            }
        }
        c.sort();
        boolean errors = problems.stream().anyMatch(Problem::error);
        return new Read(errors ? null : c, problems);
    }

    private static List<Reward> rewards(List<String> lines, List<Problem> problems) {
        List<Reward> out = new ArrayList<>();
        for (String line : lines) {
            Reward r = Reward.parse(line);
            if (r == null) {
                problems.add(Problem.error("reward-invalid", line));
            } else {
                out.add(r);
            }
        }
        return out;
    }

    public static void write(Campaign c, ConfigurationSection s, ZoneId zone) {
        s.set("name", c.name);
        s.set("description", c.description);
        s.set("icon", c.icon);
        s.set("type", c.type.name());
        s.set("targets", new ArrayList<>(c.targets));
        s.set("goal", c.goal);
        s.set("worlds", new ArrayList<>(c.worlds));
        s.set("duration", c.durationSeconds == 0 ? "0" : Durations.format(c.durationSeconds).replace(" ", ""));
        s.set("deadline", c.deadline == 0 ? "" : formatDate(c.deadline, zone));
        s.set("min-contribution", c.minContribution);
        s.set("max-contribution", c.maxContribution);
        s.set("end-policy", c.endPolicy.name());
        s.set("next", c.next);
        s.set("announce.start", c.announceStart);
        s.set("announce.end", c.announceEnd);
        s.set("messages.start", c.startMessage);
        s.set("messages.complete", c.completeMessage);
        s.set("bossbar.enabled", c.bossbar);
        s.set("bossbar.color", c.bossbarColor);
        s.set("bossbar.overlay", c.bossbarOverlay);
        s.set("milestones", null);
        for (Milestone m : c.milestones) {
            String base = "milestones." + m.percent;
            s.set(base + ".policy", m.policy.name());
            s.set(base + ".announce", m.announce);
            s.set(base + ".rewards", m.rewards.stream().map(Reward::serialize).toList());
        }
        s.set("personal", null);
        for (PersonalMilestone p : c.personal) {
            s.set("personal." + p.amount, p.rewards.stream().map(Reward::serialize).toList());
        }
        s.set("top", null);
        c.topRewards.forEach((rank, list) -> s.set("top." + rank, list.stream().map(Reward::serialize).toList()));
    }

    /** Milisegundos de una fecha {@code yyyy-MM-dd HH:mm} en la zona dada, o -1. */
    public static long parseDate(String text, ZoneId zone) {
        try {
            return LocalDateTime.parse(text.strip(), DATE).atZone(zone).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return -1;
        }
    }

    public static String formatDate(long millis, ZoneId zone) {
        return DATE.format(java.time.Instant.ofEpochMilli(millis).atZone(zone));
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String problem, List<Problem> problems, E def) {
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            problems.add(Problem.error(problem, value));
            return def;
        }
    }
}
