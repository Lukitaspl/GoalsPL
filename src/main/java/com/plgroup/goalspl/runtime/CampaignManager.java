package com.plgroup.goalspl.runtime;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.api.CampaignInfo;
import com.plgroup.goalspl.api.event.CampaignContributionEvent;
import com.plgroup.goalspl.api.event.CampaignEndedEvent;
import com.plgroup.goalspl.api.event.CampaignMilestoneEvent;
import com.plgroup.goalspl.api.event.CampaignStartedEvent;
import com.plgroup.goalspl.domain.Campaign;
import com.plgroup.goalspl.domain.CampaignState;
import com.plgroup.goalspl.domain.CampaignValidator;
import com.plgroup.goalspl.domain.EndPolicy;
import com.plgroup.goalspl.domain.HistoryAction;
import com.plgroup.goalspl.domain.Milestone;
import com.plgroup.goalspl.domain.ObjectiveType;
import com.plgroup.goalspl.domain.PersonalMilestone;
import com.plgroup.goalspl.domain.Problem;
import com.plgroup.goalspl.domain.ProgressMath;
import com.plgroup.goalspl.domain.Reward;
import com.plgroup.goalspl.domain.Source;
import com.plgroup.goalspl.lang.Messages;
import com.plgroup.goalspl.storage.GoalFiles;
import com.plgroup.goalspl.storage.HistoryEntry;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * El corazón del plugin: definiciones (goals.yml) y estados (SQLite) de las campañas, y todas las operaciones:
 * iniciar, pausar, reanudar, completar, cancelar, vencer, aportar y ajustar progreso. Al sumar progreso comprueba
 * los hitos comunitarios y personales; cada entrega lleva una clave guardada ({@code m:50}, {@code p:<uuid>:100},
 * {@code t:1}…) para que nunca se repita, ni tras un reinicio. Solo se usa desde el hilo principal.
 */
public final class CampaignManager {

    /** Resultado de una operación (las claves {@code result.<NOMBRE>} de messages.yml explican cada uno). */
    public enum Result {
        OK,
        UNKNOWN,
        ALREADY_RUNNING,
        NOT_RUNNING,
        NOT_ACTIVE,
        NOT_PAUSED,
        INVALID,
        DEADLINE_PAST,
        READ_ONLY,
        IN_USE,
        EXISTS
    }

    public static final String SYSTEM = "SYSTEM";

    private final GoalsPL plugin;
    private final GoalFiles files;
    private final Map<String, Campaign> definitions = new LinkedHashMap<>();
    private final Map<String, RunState> states = new LinkedHashMap<>();
    private BukkitTask clock;
    private int ticks;

    public CampaignManager(GoalsPL plugin, GoalFiles files) {
        this.plugin = plugin;
        this.files = files;
    }

    // ---- carga ----

    public void load(Map<String, Campaign> defs, List<RunState> loaded) {
        definitions.clear();
        definitions.putAll(defs);
        states.clear();
        for (RunState s : loaded) {
            states.put(s.campaignId, s);
            if (!definitions.containsKey(s.campaignId) && s.state.running()) {
                plugin.getLogger().warning("La campaña '" + s.campaignId + "' está en curso en la base de datos pero no"
                        + " está en goals.yml (o no se pudo leer): queda en espera, sin contar, hasta que vuelva.");
            }
        }
    }

    public void startClock() {
        clock = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stopClock() {
        if (clock != null) {
            clock.cancel();
            clock = null;
        }
    }

    // ---- consultas ----

    public Map<String, Campaign> definitions() {
        return definitions;
    }

    public Campaign definition(String id) {
        return definitions.get(id);
    }

    /** Estado de una campaña (si no tiene, uno en borrador que no se guarda hasta que cambie). */
    public RunState state(String id) {
        return states.computeIfAbsent(id, RunState::new);
    }

    public Collection<RunState> states() {
        return states.values();
    }

    public List<Campaign> running() {
        List<Campaign> out = new ArrayList<>();
        for (Campaign c : definitions.values()) {
            if (state(c.id).state.running()) {
                out.add(c);
            }
        }
        return out;
    }

    public List<Campaign> active(ObjectiveType type) {
        List<Campaign> out = new ArrayList<>();
        for (Campaign c : definitions.values()) {
            if (c.type == type && state(c.id).state == CampaignState.ACTIVE) {
                out.add(c);
            }
        }
        return out;
    }

    public CampaignInfo info(Campaign c) {
        RunState s = state(c.id);
        return new CampaignInfo(c.id, c.name, c.type, s.state, s.run, s.progress, c.goal,
                ProgressMath.percent(s.progress, c.goal), s.startedAt, s.endsAt, s.ranking().size());
    }

    /** Segundos que le quedan a la ejecución (-1 = sin límite). */
    public long secondsLeft(Campaign c) {
        RunState s = state(c.id);
        if (s.state == CampaignState.PAUSED) {
            return s.pausedRemaining < 0 ? -1 : s.pausedRemaining / 1000;
        }
        if (s.endsAt <= 0) {
            return -1;
        }
        return Math.max(0, (s.endsAt - System.currentTimeMillis()) / 1000);
    }

    /** Tiempo restante para mostrar: duración, «sin límite» o, si no está en curso, un guion. */
    public String timeText(Campaign c) {
        if (!state(c.id).state.running()) {
            return plugin.messages().raw("gui.common.not-running");
        }
        long left = secondsLeft(c);
        return left < 0 ? plugin.messages().raw("gui.common.no-limit") : com.plgroup.goalspl.util.Durations.format(left);
    }

    // ---- operaciones ----

    public Result start(String id, String actor, String previous) {
        Campaign c = definitions.get(id);
        if (c == null) {
            return Result.UNKNOWN;
        }
        RunState s = state(id);
        if (!s.state.canStart()) {
            return Result.ALREADY_RUNNING;
        }
        List<Problem> problems = CampaignValidator.validate(c, plugin.env());
        if (problems.stream().anyMatch(Problem::error)) {
            return Result.INVALID;
        }
        long now = System.currentTimeMillis();
        long ends = c.endFor(now);
        if (ends > 0 && ends <= now) {
            return Result.DEADLINE_PAST;
        }
        s.run++;
        s.state = CampaignState.ACTIVE;
        s.progress = 0;
        s.contributions.clear();
        s.grants.clear();
        s.startedAt = now;
        s.endsAt = ends;
        s.pausedRemaining = -1;
        s.updatedAt = now;
        s.dirty = true;
        int run = s.run;
        long goal = c.goal;
        plugin.database().write("guardar el inicio de " + id, st -> st.insertRun(id, run, now, goal));
        saveNow(s);
        history(id, actor, HistoryAction.STARTED, "#" + run + (previous == null ? "" : " ← " + previous));
        if (c.announceStart) {
            announce(c, c.startMessage, "announce.started");
        }
        Bukkit.getPluginManager().callEvent(new CampaignStartedEvent(info(c), actor, previous, false));
        plugin.bossbars().update(c, s);
        return Result.OK;
    }

    public Result pause(String id, String actor) {
        Campaign c = definitions.get(id);
        if (c == null) {
            return Result.UNKNOWN;
        }
        RunState s = state(id);
        if (s.state != CampaignState.ACTIVE) {
            return Result.NOT_ACTIVE;
        }
        long now = System.currentTimeMillis();
        s.state = CampaignState.PAUSED;
        s.pausedRemaining = s.endsAt > 0 ? Math.max(0, s.endsAt - now) : -1;
        s.updatedAt = now;
        s.dirty = true;
        saveNow(s);
        history(id, actor, HistoryAction.PAUSED, "");
        if (c.announceEnd) {
            announce(c, "", "announce.paused");
        }
        Bukkit.getPluginManager().callEvent(new CampaignEndedEvent(info(c), CampaignState.PAUSED, actor));
        plugin.bossbars().update(c, s);
        return Result.OK;
    }

    public Result resume(String id, String actor) {
        Campaign c = definitions.get(id);
        if (c == null) {
            return Result.UNKNOWN;
        }
        RunState s = state(id);
        if (s.state != CampaignState.PAUSED) {
            return Result.NOT_PAUSED;
        }
        long now = System.currentTimeMillis();
        long ends = s.pausedRemaining >= 0 ? now + s.pausedRemaining : 0;
        if (c.deadline > 0) {
            ends = ends == 0 ? c.deadline : Math.min(ends, c.deadline);
        }
        s.state = CampaignState.ACTIVE;
        s.endsAt = ends;
        s.pausedRemaining = -1;
        s.updatedAt = now;
        s.dirty = true;
        saveNow(s);
        history(id, actor, HistoryAction.RESUMED, "");
        if (c.announceStart) {
            announce(c, "", "announce.resumed");
        }
        Bukkit.getPluginManager().callEvent(new CampaignStartedEvent(info(c), actor, null, true));
        plugin.bossbars().update(c, s);
        return Result.OK;
    }

    /**
     * Completa a mano: el progreso sube a la meta (sin contar para nadie) y se entregan los hitos que falten, las
     * recompensas de la clasificación y se inicia la siguiente etapa, igual que si se hubiera llegado jugando.
     */
    public Result complete(String id, String actor) {
        Campaign c = definitions.get(id);
        if (c == null) {
            return Result.UNKNOWN;
        }
        RunState s = state(id);
        if (!s.state.running()) {
            return Result.NOT_RUNNING;
        }
        long before = s.progress;
        s.progress = c.goal;
        s.dirty = true;
        history(id, actor, HistoryAction.PROGRESS, before + " → " + c.goal + " (complete)");
        checkMilestones(c, s);
        if (s.state.running()) {
            finish(c, s, CampaignState.COMPLETED, actor);
        }
        return Result.OK;
    }

    public Result cancel(String id, String actor) {
        Campaign c = definitions.get(id);
        if (c == null) {
            return Result.UNKNOWN;
        }
        RunState s = state(id);
        if (!s.state.running()) {
            return Result.NOT_RUNNING;
        }
        finish(c, s, CampaignState.CANCELLED, actor);
        return Result.OK;
    }

    /**
     * Aporte de un jugador ({@code player} nulo = de nadie). Devuelve lo aceptado: recortado a lo que falta y al
     * máximo del jugador, y 0 si la campaña no está activa o el evento se cancela.
     */
    public long contribute(Campaign c, UUID player, String name, long amount, Source source) {
        RunState s = state(c.id);
        if (s.state != CampaignState.ACTIVE || amount <= 0) {
            return 0;
        }
        long accepted = ProgressMath.accepted(s.progress, c.goal, player == null ? 0 : s.contribution(player),
                player == null ? 0 : c.maxContribution, amount);
        if (accepted <= 0) {
            return 0;
        }
        CampaignContributionEvent event = new CampaignContributionEvent(info(c), player, name, accepted, source);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return 0;
        }
        s.progress += accepted;
        s.updatedAt = System.currentTimeMillis();
        s.dirty = true;
        if (player != null) {
            RunState.Contribution con = s.contributions.computeIfAbsent(player, p -> new RunState.Contribution(p, name, 0));
            con.name = name;
            con.amount += accepted;
            con.dirty = true;
            checkPersonal(c, s, con);
        }
        checkMilestones(c, s);
        return accepted;
    }

    /**
     * Ajuste de la administración: suma o resta ({@code set} = fija el valor) al progreso total, entre 0 y la meta,
     * y si se indica un jugador también a su aportación. Los hitos ya entregados no se quitan.
     */
    public Result adjust(String id, long value, boolean set, UUID player, String name, String actor) {
        Campaign c = definitions.get(id);
        if (c == null) {
            return Result.UNKNOWN;
        }
        RunState s = state(id);
        if (!s.state.running()) {
            return Result.NOT_RUNNING;
        }
        long before = s.progress;
        long target = set ? value : s.progress + value;
        s.progress = Math.max(0, Math.min(c.goal, target));
        long delta = s.progress - before;
        if (player != null && delta != 0) {
            RunState.Contribution con = s.contributions.computeIfAbsent(player, p -> new RunState.Contribution(p, name, 0));
            con.amount = Math.max(0, con.amount + delta);
            con.dirty = true;
            if (delta > 0) {
                checkPersonal(c, s, con);
            }
        }
        s.updatedAt = System.currentTimeMillis();
        s.dirty = true;
        history(id, actor, HistoryAction.PROGRESS, before + " → " + s.progress + (player == null ? "" : " (" + name + ")"));
        if (s.state == CampaignState.ACTIVE) {
            checkMilestones(c, s);
        }
        saveNow(s);
        return Result.OK;
    }

    // ---- hitos ----

    private void checkPersonal(Campaign c, RunState s, RunState.Contribution con) {
        for (PersonalMilestone p : c.personal) {
            String key = "p:" + con.player + ":" + p.amount;
            if (con.amount >= p.amount && !s.grants.contains(key)) {
                plugin.rewards().grantPlayer(c, s, key, con.player, con.name, p.rewards, 0);
                history(c.id, SYSTEM, HistoryAction.MILESTONE, con.name + " #" + p.amount);
            }
        }
    }

    private void checkMilestones(Campaign c, RunState s) {
        for (Milestone m : c.milestones) {
            String key = "m:" + m.percent;
            if (s.grants.contains(key) || !ProgressMath.reached(s.progress, c.goal, m.percent)) {
                continue;
            }
            s.grants.add(key);
            int run = s.run;
            long now = System.currentTimeMillis();
            plugin.database().write("guardar el hito " + c.id + " " + key, st -> st.insertGrant(key, c.id, run, now));
            history(c.id, SYSTEM, HistoryAction.MILESTONE, m.percent + "%");
            if (m.announce) {
                Bukkit.getServer().sendMessage(plugin.messages().msg("announce.milestone", Messages.parsed("name", c.name),
                        Messages.ph("percent", m.percent), Messages.ph("progress", s.progress), Messages.ph("goal", c.goal)));
            }
            Bukkit.getPluginManager().callEvent(new CampaignMilestoneEvent(info(c), m.percent));
            plugin.rewards().grantMilestone(c, s, m);
        }
        if (s.progress >= c.goal && s.state.running()) {
            finish(c, s, CampaignState.COMPLETED, SYSTEM);
        }
    }

    // ---- fin ----

    private void finish(Campaign c, RunState s, CampaignState outcome, String actor) {
        long now = System.currentTimeMillis();
        s.state = outcome;
        s.pausedRemaining = -1;
        s.updatedAt = now;
        s.dirty = true;
        saveNow(s);
        int run = s.run;
        long progress = s.progress;
        plugin.database().write("cerrar la ejecución de " + c.id, st -> st.finishRun(c.id, run, now, outcome.name(), progress));
        history(c.id, actor, HistoryAction.valueOf(outcome.name()), progress + "/" + c.goal);
        plugin.bossbars().update(c, s);
        if (outcome == CampaignState.COMPLETED) {
            List<RunState.Contribution> ranking = s.ranking();
            for (Map.Entry<Integer, List<Reward>> e : c.topRewards.entrySet()) {
                int rank = e.getKey();
                if (rank <= ranking.size()) {
                    RunState.Contribution con = ranking.get(rank - 1);
                    plugin.rewards().grantPlayer(c, s, "t:" + rank, con.player, con.name, e.getValue(), rank);
                }
            }
            if (c.announceEnd) {
                announce(c, c.completeMessage, "announce.completed");
                announceTop(c, ranking);
            }
        } else {
            if (c.announceEnd) {
                announce(c, "", outcome == CampaignState.CANCELLED ? "announce.cancelled" : "announce.expired");
            }
            if (c.endPolicy == EndPolicy.REFUND && c.type == ObjectiveType.DELIVER_ITEM) {
                plugin.rewards().refund(c, run);
            }
        }
        Bukkit.getPluginManager().callEvent(new CampaignEndedEvent(info(c), outcome, actor));
        if (outcome == CampaignState.COMPLETED && !c.next.isEmpty()) {
            Result r = start(c.next, SYSTEM, c.id);
            history(c.id, SYSTEM, HistoryAction.NEXT, c.next + " → " + r.name());
            if (r != Result.OK) {
                plugin.getLogger().warning("No se pudo iniciar la etapa siguiente '" + c.next + "' de '" + c.id + "': " + r.name());
            }
        }
    }

    private void announceTop(Campaign c, List<RunState.Contribution> ranking) {
        if (ranking.isEmpty()) {
            return;
        }
        Bukkit.getServer().sendMessage(plugin.messages().text("announce.top-header", Messages.parsed("name", c.name)));
        for (int i = 0; i < Math.min(3, ranking.size()); i++) {
            RunState.Contribution con = ranking.get(i);
            Bukkit.getServer().sendMessage(plugin.messages().text("announce.top-line", Messages.ph("rank", i + 1),
                    Messages.ph("player", con.name), Messages.ph("amount", con.amount)));
        }
    }

    private void announce(Campaign c, String custom, String key) {
        RunState s = state(c.id);
        if (custom != null && !custom.isBlank()) {
            String text = new com.plgroup.goalspl.domain.Placeholders().set("goal_id", c.id).set("goal_name", c.name)
                    .set("progress", s.progress).set("goal", c.goal).set("percent", ProgressMath.percent(s.progress, c.goal))
                    .apply(custom);
            Bukkit.getServer().sendMessage(Messages.mini(text));
            return;
        }
        long left = secondsLeft(c);
        Bukkit.getServer().sendMessage(plugin.messages().msg(key, Messages.parsed("name", c.name), Messages.ph("goal", c.goal),
                Messages.ph("progress", s.progress), Messages.ph("percent", ProgressMath.percent(s.progress, c.goal)),
                Messages.ph("time", left < 0 ? plugin.messages().raw("gui.common.no-limit")
                        : com.plgroup.goalspl.util.Durations.format(left))));
    }

    // ---- reloj y guardado ----

    private void tick() {
        long now = System.currentTimeMillis();
        for (Campaign c : new ArrayList<>(definitions.values())) {
            RunState s = state(c.id);
            if (s.state == CampaignState.ACTIVE && s.endsAt > 0 && now >= s.endsAt) {
                finish(c, s, CampaignState.EXPIRED, SYSTEM);
            }
            plugin.bossbars().update(c, s);
        }
        ticks++;
        if (ticks % plugin.settings().saveIntervalSeconds == 0) {
            saveDirty();
            plugin.database().flushHistory();
        }
        if (ticks % 20 == 0) {
            plugin.permissions().expire();
        }
        int retry = plugin.settings().retryMinutes;
        if (retry > 0 && ticks % (retry * 60) == 0) {
            plugin.rewards().retryOnline();
        }
        if (ticks % 86_400 == 0) {
            plugin.purgeOld();
        }
    }

    /** Guarda en segundo plano los estados y aportaciones con cambios. */
    public void saveDirty() {
        List<RunState.Row> rows = new ArrayList<>();
        List<RunState.ContributionRow> cons = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (RunState s : states.values()) {
            List<RunState.ContributionRow> dirtyCons = s.drainDirty(now);
            if (s.dirty || !dirtyCons.isEmpty()) {
                s.dirty = false;
                rows.add(s.row());
                cons.addAll(dirtyCons);
            }
        }
        if (!rows.isEmpty()) {
            plugin.database().write("guardar el progreso", st -> st.save(rows, cons));
        }
    }

    /** Guarda ya (en el orden de la cola) el estado y las aportaciones de una campaña. */
    public void saveNow(RunState s) {
        s.dirty = false;
        RunState.Row row = s.row();
        List<RunState.ContributionRow> cons = s.drainDirty(System.currentTimeMillis());
        plugin.database().write("guardar el estado de " + s.campaignId, st -> st.save(List.of(row), cons));
    }

    /** Filas para guardar al apagar (todas las que tengan cambios). */
    public List<RunState.Row> dirtyRows(List<RunState.ContributionRow> consOut) {
        List<RunState.Row> rows = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (RunState s : states.values()) {
            List<RunState.ContributionRow> cons = s.drainDirty(now);
            if (s.dirty || !cons.isEmpty()) {
                s.dirty = false;
                rows.add(s.row());
                consOut.addAll(cons);
            }
        }
        return rows;
    }

    // ---- definiciones ----

    /** Crea o reemplaza una definición y guarda goals.yml. {@code onDone} recibe si se guardó. */
    public Result save(Campaign c, boolean creating, String actor, Consumer<Boolean> onDone) {
        if (files.readOnly()) {
            return Result.READ_ONLY;
        }
        if (creating && definitions.containsKey(c.id)) {
            return Result.EXISTS;
        }
        c.sort();
        definitions.put(c.id, c);
        history(c.id, actor, creating ? HistoryAction.CREATED : HistoryAction.EDITED, "");
        writeFile(onDone);
        RunState s = states.get(c.id);
        if (s != null) {
            plugin.bossbars().update(c, s);
            if (s.state == CampaignState.ACTIVE) {
                checkMilestones(c, s);
            }
        }
        return Result.OK;
    }

    public Result delete(String id, String actor, Consumer<Boolean> onDone) {
        Campaign c = definitions.get(id);
        if (c == null) {
            return Result.UNKNOWN;
        }
        if (files.readOnly()) {
            return Result.READ_ONLY;
        }
        if (state(id).state.running()) {
            return Result.IN_USE;
        }
        definitions.remove(id);
        states.remove(id);
        plugin.bossbars().remove(id);
        plugin.database().write("borrar el estado de " + id, st -> st.deleteState(id));
        history(id, actor, HistoryAction.DELETED, "");
        writeFile(onDone);
        return Result.OK;
    }

    private void writeFile(Consumer<Boolean> onDone) {
        String content = files.render(definitions.values(), plugin.settings().zone);
        plugin.database().writeFile(files.file(), content, ok -> {
            if (!ok) {
                plugin.getLogger().severe("goals.yml no se ha podido guardar; los cambios siguen en memoria hasta reiniciar.");
            }
            if (onDone != null) {
                onDone.accept(ok);
            }
        });
    }

    /** Vuelve a leer goals.yml en el hilo de disco y aplica el resultado en el principal. */
    public void reloadDefinitions(Runnable onDone) {
        java.time.ZoneId zone = plugin.settings().zone;
        plugin.database().query("leer goals.yml", st -> files.load(zone), result -> {
            if (result != null) {
                files.logProblems(result.problems());
                definitions.clear();
                definitions.putAll(result.campaigns());
                for (String id : new ArrayList<>(plugin.bossbars().ids())) {
                    if (!definitions.containsKey(id)) {
                        plugin.bossbars().remove(id);
                    }
                }
            }
            onDone.run();
        });
    }

    public void history(String campaignId, String actor, HistoryAction action, String details) {
        plugin.database().history(new HistoryEntry(System.currentTimeMillis(), campaignId, actor, action, details));
    }
}
