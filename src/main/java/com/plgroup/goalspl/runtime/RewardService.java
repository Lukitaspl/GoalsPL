package com.plgroup.goalspl.runtime;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.domain.Campaign;
import com.plgroup.goalspl.domain.HistoryAction;
import com.plgroup.goalspl.domain.Milestone;
import com.plgroup.goalspl.domain.Placeholders;
import com.plgroup.goalspl.domain.ProgressMath;
import com.plgroup.goalspl.domain.Reward;
import com.plgroup.goalspl.domain.RewardPolicy;
import com.plgroup.goalspl.domain.RewardType;
import com.plgroup.goalspl.lang.Messages;
import com.plgroup.goalspl.storage.DeliveryRow;
import com.plgroup.goalspl.storage.RewardRow;
import com.plgroup.goalspl.util.Durations;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Entrega de recompensas. Cada recompensa de jugador se guarda en la base de datos con las variables ya
 * sustituidas: si el jugador está conectado se entrega en el momento y queda como {@code DELIVERED}; si no está, o
 * una parte falla (un comando que no existe, el inventario lleno), lo que falta queda {@code PENDING} y se reintenta
 * al entrar, cada {@code rewards.retry-minutes} o con {@code /goals claim}. Las recompensas globales (anuncio, comando
 * sin jugador, evento) se ejecutan una vez por hito.
 */
public final class RewardService {

    /** Resultado de entregar unas líneas: lo que falta y el último error. */
    private record Outcome(List<String> remaining, String error, int delivered) {
    }

    private final GoalsPL plugin;
    private final Set<UUID> claiming = new HashSet<>();

    public RewardService(GoalsPL plugin) {
        this.plugin = plugin;
    }

    // ---- concesión ----

    public void grantMilestone(Campaign c, RunState s, Milestone m) {
        Placeholders base = base(c, s);
        runGlobal(c, m.rewards, base, m.percent + "%");
        if (m.rewards.stream().noneMatch(r -> r.type().perPlayer())) {
            return;
        }
        Map<UUID, String> recipients = new LinkedHashMap<>();
        long min = Math.max(1, c.minContribution);
        for (RunState.Contribution con : s.contributions.values()) {
            if (m.policy == RewardPolicy.COMMUNITY ? con.amount > 0 : con.amount >= min) {
                recipients.put(con.player, con.name);
            }
        }
        if (m.policy == RewardPolicy.COMMUNITY) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                recipients.putIfAbsent(p.getUniqueId(), p.getName());
            }
        }
        recipients.forEach((uuid, name) -> grantPlayer(c, s, "m:" + m.percent + ":" + uuid, uuid, name, m.rewards, 0));
    }

    /** Recompensas de jugador para uno (hito personal, clasificación o hito comunitario). No se repite la clave. */
    public void grantPlayer(Campaign c, RunState s, String key, UUID player, String name, List<Reward> rewards, int rank) {
        if (!s.grants.add(key)) {
            return;
        }
        int run = s.run;
        long now = System.currentTimeMillis();
        plugin.database().write("guardar la clave " + key, st -> st.insertGrant(key, c.id, run, now));
        Placeholders ph = base(c, s).set("player", name).set("contribution", s.contribution(player))
                .set("rank", rank > 0 ? rank : s.rank(player));
        if (!key.startsWith("m:")) {
            runGlobal(c, rewards, ph, key);
        }
        List<String> lines = new ArrayList<>();
        for (Reward r : rewards) {
            if (r.type().perPlayer()) {
                lines.add(r.type().name() + ": " + (r.type().console() ? ph.applyForCommand(r.value()) : ph.apply(r.value())));
            }
        }
        if (!lines.isEmpty()) {
            deliverAndStore(c.id, run, key, player, name, lines);
        }
    }

    /** Devuelve los objetos entregados de una ejecución cancelada o vencida (como recompensas ITEM). */
    public void refund(Campaign c, int run) {
        long now = System.currentTimeMillis();
        plugin.database().query("preparar las devoluciones de " + c.id, st -> st.takeRefunds(c.id, run, now), rows -> {
            if (rows == null) {
                return;
            }
            for (DeliveryRow d : rows) {
                plugin.manager().history(c.id, CampaignManager.SYSTEM, HistoryAction.REFUND, d.name() + ": " + d.amount() + " " + d.material());
                deliverAndStore(c.id, run, "r:" + d.player() + ":" + d.material(), d.player(), d.name(),
                        List.of(RewardType.ITEM.name() + ": " + d.material() + " " + d.amount()));
            }
        });
    }

    private Placeholders base(Campaign c, RunState s) {
        String plainName = PlainTextComponentSerializer.plainText().serialize(Messages.mini(c.name));
        return new Placeholders().set("goal_id", c.id).set("goal_name", plainName).set("progress", s.progress)
                .set("goal", c.goal).set("percent", ProgressMath.percent(s.progress, c.goal)).set("player", "")
                .set("contribution", 0).set("rank", 0);
    }

    private void runGlobal(Campaign c, List<Reward> rewards, Placeholders ph, String where) {
        for (Reward r : rewards) {
            if (r.type().perPlayer()) {
                continue;
            }
            String error = null;
            try {
                switch (r.type()) {
                    case BROADCAST -> Bukkit.getServer().sendMessage(Messages.mini(ph.apply(r.value())));
                    case GLOBAL_COMMAND -> error = dispatch(ph.applyForCommand(r.value()));
                    case EVENT -> error = plugin.eventsPl().start(r.value().strip()) ? null : "EventsPL";
                    default -> {
                    }
                }
            } catch (RuntimeException e) {
                error = e.getClass().getSimpleName() + ": " + e.getMessage();
                plugin.getLogger().log(Level.WARNING, "Recompensa global de " + c.id + " (" + where + ") falló", e);
            }
            if (error != null) {
                plugin.manager().history(c.id, CampaignManager.SYSTEM, HistoryAction.REWARD_FAILED, where + ": " + r.serialize() + " - " + error);
                plugin.getLogger().warning("Recompensa global de " + c.id + " (" + where + ") no entregada: " + r.serialize() + " - " + error);
            }
        }
    }

    // ---- entrega ----

    private void deliverAndStore(String campaignId, int run, String key, UUID player, String name, List<String> lines) {
        long now = System.currentTimeMillis();
        Player p = Bukkit.getPlayer(player);
        String status = RewardRow.PENDING;
        String payload = String.join("\n", lines);
        String error = "";
        int attempts = 0;
        if (p != null && p.isOnline()) {
            Outcome o = deliver(p, lines);
            attempts = 1;
            if (o.remaining().isEmpty()) {
                status = RewardRow.DELIVERED;
            } else {
                payload = String.join("\n", o.remaining());
                error = o.error() == null ? "" : o.error();
                plugin.messages().send(p, "rewards.pending-added");
            }
        }
        RewardRow row = new RewardRow(0, key, campaignId, run, player, name, payload, status, attempts, error, now,
                RewardRow.DELIVERED.equals(status) ? now : 0);
        plugin.database().write("guardar una recompensa de " + name, st -> st.insertReward(row));
        plugin.manager().history(campaignId, CampaignManager.SYSTEM,
                RewardRow.DELIVERED.equals(status) ? HistoryAction.REWARD : HistoryAction.REWARD_FAILED,
                name + " [" + key + "] " + (RewardRow.DELIVERED.equals(status) ? "" : "PENDING " + error));
    }

    private Outcome deliver(Player p, List<String> lines) {
        List<String> remaining = new ArrayList<>();
        String error = null;
        int delivered = 0;
        for (String line : lines) {
            Reward r = Reward.parse(line);
            if (r == null || line.isBlank()) {
                continue;
            }
            try {
                String fail = switch (r.type()) {
                    case COMMAND -> dispatch(r.value());
                    case MESSAGE -> {
                        p.sendMessage(Messages.mini(r.value()));
                        yield null;
                    }
                    case ITEM -> {
                        String left = giveItems(p, r.value());
                        if (left != null) {
                            remaining.add(RewardType.ITEM.name() + ": " + left);
                            yield "inventory-full";
                        }
                        yield null;
                    }
                    case PERMISSION -> {
                        String[] parts = r.value().split("\\s+");
                        long secs = parts.length == 2 ? Durations.parse(parts[1]) : -1;
                        if (secs <= 0) {
                            plugin.getLogger().warning("Permiso temporal mal escrito, se descarta: " + r.value());
                            yield null;
                        }
                        plugin.permissions().grant(p, parts[0], secs * 1000);
                        yield null;
                    }
                    default -> null;
                };
                if (fail != null) {
                    error = fail;
                    if (r.type() == RewardType.COMMAND) {
                        remaining.add(line);
                    }
                } else {
                    delivered++;
                }
            } catch (RuntimeException e) {
                error = e.getClass().getSimpleName() + ": " + e.getMessage();
                remaining.add(line);
                plugin.getLogger().log(Level.WARNING, "Error al entregar '" + line + "' a " + p.getName(), e);
            }
        }
        return new Outcome(remaining, error, delivered);
    }

    /** Ejecuta un comando como consola; devuelve el error o {@code null}. Los comandos bloqueados nunca se ejecutan. */
    private String dispatch(String command) {
        String cmd = command.strip();
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        if (cmd.isEmpty()) {
            return "empty";
        }
        if (plugin.settings().blocked(cmd)) {
            plugin.getLogger().severe("Comando bloqueado en una recompensa (console-commands.blocked), no se ejecuta: " + cmd);
            return "blocked";
        }
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd) ? null : "command-failed";
    }

    /** Da {@code MATERIAL cantidad}; devuelve lo que no cupo ({@code MATERIAL resto}) o {@code null}. */
    private String giveItems(Player p, String value) {
        String[] parts = value.strip().split("\\s+");
        Material m = parts.length == 2 ? Material.matchMaterial(parts[0].toUpperCase(Locale.ROOT)) : null;
        long amount;
        try {
            amount = parts.length == 2 ? Long.parseLong(parts[1]) : 0;
        } catch (NumberFormatException e) {
            amount = 0;
        }
        if (m == null || !m.isItem() || amount <= 0) {
            plugin.getLogger().warning("Recompensa de objetos mal escrita, se descarta: " + value);
            return null;
        }
        long left = amount;
        int max = Math.max(1, m.getMaxStackSize());
        while (left > 0) {
            int n = (int) Math.min(max, left);
            Map<Integer, ItemStack> over = p.getInventory().addItem(new ItemStack(m, n));
            int notGiven = over.values().stream().mapToInt(ItemStack::getAmount).sum();
            left -= (n - notGiven);
            if (notGiven > 0) {
                break;
            }
        }
        return left > 0 ? m.name() + " " + left : null;
    }

    // ---- reclamar ----

    /** Entrega lo pendiente de un jugador conectado. {@code quiet}: sin mensaje si no había nada. */
    public void claim(Player p, boolean quiet, Runnable after) {
        UUID id = p.getUniqueId();
        if (!claiming.add(id)) {
            return;
        }
        plugin.database().query("leer recompensas pendientes de " + p.getName(), st -> st.pendingRewards(id), rows -> {
            claiming.remove(id);
            if (rows == null || !p.isOnline()) {
                return;
            }
            int done = 0;
            int left = 0;
            long now = System.currentTimeMillis();
            for (RewardRow row : rows) {
                Outcome o = deliver(p, Arrays.asList(row.payload().split("\n")));
                RewardRow updated = o.remaining().isEmpty()
                        ? row.withResult(RewardRow.DELIVERED, row.payload(), null, now)
                        : row.withResult(RewardRow.PENDING, String.join("\n", o.remaining()), o.error(), now);
                plugin.database().write("actualizar una recompensa de " + p.getName(), st -> st.updateReward(updated));
                if (o.remaining().isEmpty()) {
                    done++;
                    plugin.manager().history(row.campaignId(), p.getName(), HistoryAction.REWARD, "[" + row.grantKey() + "] claim");
                } else {
                    left++;
                }
            }
            if (done > 0 || !quiet) {
                plugin.messages().send(p, done > 0 ? "rewards.claimed" : "rewards.nothing", Messages.ph("count", done));
            }
            if (left > 0) {
                plugin.messages().send(p, "rewards.still-pending", Messages.ph("count", left));
            }
            if (after != null) {
                after.run();
            }
        });
    }

    public void retryOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            claim(p, true, null);
        }
    }

    public void pending(UUID player, Consumer<List<RewardRow>> onMain) {
        plugin.database().query("leer recompensas pendientes", st -> st.pendingRewards(player), onMain);
    }

    public void forCampaign(String campaignId, int limit, int offset, Consumer<List<RewardRow>> onMain) {
        plugin.database().query("leer recompensas de " + campaignId, st -> st.campaignRewards(campaignId, limit, offset), onMain);
    }
}
