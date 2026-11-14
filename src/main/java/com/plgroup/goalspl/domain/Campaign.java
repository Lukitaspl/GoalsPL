package com.plgroup.goalspl.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Definición de una campaña (lo que se guarda en goals.yml). El estado y el progreso no van aquí sino en la base
 * de datos, unidos por el {@link #id}, que no cambia nunca para no perder el progreso al editar. Los menús editan
 * una copia ({@link #copy()}) y solo la guardan al confirmar.
 */
public final class Campaign {

    /** IDs válidos: minúsculas, números, {@code _} y {@code -}, de 2 a 32 caracteres. */
    public static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{1,31}");

    public final String id;
    public String name;
    public String description = "";
    public String icon = "NETHER_STAR";
    public ObjectiveType type = ObjectiveType.KILL_ENTITY;
    /** Entidades, bloques u objetos que cuentan (nombres de Bukkit en mayúsculas). Vacío = cualquiera. */
    public List<String> targets = new ArrayList<>();
    /** Cantidad total necesaria. */
    public long goal = 100;
    /** Mundos donde cuenta el progreso. Vacío = todos. */
    public List<String> worlds = new ArrayList<>();
    /** Duración de cada ejecución en segundos (0 = sin límite). */
    public long durationSeconds;
    /** Fecha límite absoluta en milisegundos (0 = ninguna). */
    public long deadline;
    /** Aportación mínima para recibir las recompensas de los hitos {@link RewardPolicy#PARTICIPANTS}. */
    public long minContribution = 1;
    /** Aportación máxima que cuenta por jugador (0 = sin límite). */
    public long maxContribution;
    public List<Milestone> milestones = new ArrayList<>();
    public List<PersonalMilestone> personal = new ArrayList<>();
    /** Recompensas por puesto en la clasificación al completar (1 = primero). */
    public TreeMap<Integer, List<Reward>> topRewards = new TreeMap<>();
    public EndPolicy endPolicy = EndPolicy.KEEP;
    /** Campaña que se inicia sola al completar esta ({@code ""} = ninguna). */
    public String next = "";
    public boolean announceStart = true;
    public boolean announceEnd = true;
    /** Mensajes propios (MiniMessage); vacío = el de messages.yml. */
    public String startMessage = "";
    public String completeMessage = "";
    public boolean bossbar = true;
    public String bossbarColor = "GREEN";
    public String bossbarOverlay = "PROGRESS";

    public Campaign(String id) {
        this.id = id;
        this.name = id;
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    public static String normalizeId(String text) {
        return text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
    }

    /** Copia profunda con otro ID (para clonar) o el mismo (para editar). */
    public Campaign copy(String newId) {
        Campaign c = new Campaign(newId);
        c.name = name;
        c.description = description;
        c.icon = icon;
        c.type = type;
        c.targets = new ArrayList<>(targets);
        c.goal = goal;
        c.worlds = new ArrayList<>(worlds);
        c.durationSeconds = durationSeconds;
        c.deadline = deadline;
        c.minContribution = minContribution;
        c.maxContribution = maxContribution;
        c.milestones = new ArrayList<>(milestones.stream().map(Milestone::copy).toList());
        c.personal = new ArrayList<>(personal.stream().map(PersonalMilestone::copy).toList());
        c.topRewards = new TreeMap<>();
        topRewards.forEach((k, v) -> c.topRewards.put(k, new ArrayList<>(v)));
        c.endPolicy = endPolicy;
        c.next = next;
        c.announceStart = announceStart;
        c.announceEnd = announceEnd;
        c.startMessage = startMessage;
        c.completeMessage = completeMessage;
        c.bossbar = bossbar;
        c.bossbarColor = bossbarColor;
        c.bossbarOverlay = bossbarOverlay;
        return c;
    }

    public Campaign copy() {
        return copy(id);
    }

    /** Ordena los hitos por porcentaje y los personales por cantidad. */
    public void sort() {
        milestones.sort(Comparator.comparingInt(m -> m.percent));
        personal.sort(Comparator.comparingLong(p -> p.amount));
    }

    public Milestone milestone(int percent) {
        for (Milestone m : milestones) {
            if (m.percent == percent) {
                return m;
            }
        }
        return null;
    }

    public PersonalMilestone personal(long amount) {
        for (PersonalMilestone p : personal) {
            if (p.amount == amount) {
                return p;
            }
        }
        return null;
    }

    /** Si el objetivo cuenta para esta campaña (lista vacía = cualquiera). */
    public boolean counts(String target) {
        return targets.isEmpty() || targets.contains(target);
    }

    public boolean countsInWorld(String world) {
        return worlds.isEmpty() || worlds.contains(world);
    }

    /** Momento de fin de una ejecución que empieza en {@code start} (0 = sin fin). */
    public long endFor(long start) {
        long byDuration = durationSeconds > 0 ? start + durationSeconds * 1000 : 0;
        if (byDuration == 0) {
            return deadline;
        }
        return deadline == 0 ? byDuration : Math.min(byDuration, deadline);
    }
}
