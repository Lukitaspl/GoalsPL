package com.plgroup.goalspl.command;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.domain.Campaign;
import com.plgroup.goalspl.domain.Milestone;
import com.plgroup.goalspl.gui.AdminMenu;
import com.plgroup.goalspl.gui.CampaignMenu;
import com.plgroup.goalspl.gui.DeliverMenu;
import com.plgroup.goalspl.gui.GoalsMenu;
import com.plgroup.goalspl.gui.MainMenu;
import com.plgroup.goalspl.lang.Messages;
import com.plgroup.goalspl.runtime.CampaignManager;
import com.plgroup.goalspl.runtime.RunState;
import com.plgroup.goalspl.domain.ObjectiveType;
import com.plgroup.goalspl.domain.ProgressMath;
import com.plgroup.goalspl.storage.HistoryEntry;
import com.plgroup.goalspl.util.Durations;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * {@code /goalspl} (alias {@code /goals}). Sin argumentos abre el menú principal (o la ayuda en consola).
 * Cada subcomando comprueba su permiso; los que no necesitan un jugador funcionan desde la consola.
 */
public final class GoalsCommand implements BasicCommand {

    private record Sub(String name, String permission, boolean playerOnly, BiConsumer<CommandSender, String[]> run) {
    }

    /** Subcomandos en el orden de la ayuda; las pruebas comprueban que cada uno tiene su línea de ayuda. */
    public static final List<String> NAMES = List.of("help", "goals", "info", "top", "contribute", "claim", "admin", "start",
            "pause", "resume", "complete", "cancel", "progress", "history", "reload");

    private final GoalsPL plugin;
    private final List<Sub> subs;

    public GoalsCommand(GoalsPL plugin) {
        this.plugin = plugin;
        this.subs = List.of(
                new Sub("help", null, false, (s, a) -> help(s)),
                new Sub("goals", "goalspl.use", false, this::goals),
                new Sub("info", "goalspl.use", false, this::info),
                new Sub("top", "goalspl.use", false, this::top),
                new Sub("contribute", "goalspl.contribute", true, this::contribute),
                new Sub("claim", "goalspl.use", true, (s, a) -> plugin.rewards().claim((Player) s, false, null)),
                new Sub("admin", "goalspl.view", true, (s, a) -> new AdminMenu(plugin, (Player) s, 0).open()),
                new Sub("start", "goalspl.manage", false, (s, a) -> op(s, a, id -> plugin.manager().start(id, actor(s), null))),
                new Sub("pause", "goalspl.manage", false, (s, a) -> op(s, a, id -> plugin.manager().pause(id, actor(s)))),
                new Sub("resume", "goalspl.manage", false, (s, a) -> op(s, a, id -> plugin.manager().resume(id, actor(s)))),
                new Sub("complete", "goalspl.manage", false, (s, a) -> op(s, a, id -> plugin.manager().complete(id, actor(s)))),
                new Sub("cancel", "goalspl.cancel", false, (s, a) -> op(s, a, id -> plugin.manager().cancel(id, actor(s)))),
                new Sub("progress", "goalspl.progress", false, this::progress),
                new Sub("history", "goalspl.view", false, this::history),
                new Sub("reload", "goalspl.reload", false, (s, a) -> plugin.reloadAll(() -> plugin.messages().send(s, "command.reloaded")))
        );
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            if (sender instanceof Player p && p.hasPermission("goalspl.use")) {
                new MainMenu(plugin, p).open();
            } else {
                help(sender);
            }
            return;
        }
        String name = args[0].toLowerCase(Locale.ROOT);
        Sub sub = subs.stream().filter(s -> s.name().equals(name)).findFirst().orElse(null);
        if (sub == null) {
            plugin.messages().send(sender, "command.unknown", Messages.ph("value", args[0]));
            return;
        }
        if (sub.permission() != null && !sender.hasPermission(sub.permission())) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (sub.playerOnly() && !(sender instanceof Player)) {
            plugin.messages().send(sender, "command.player-only");
            return;
        }
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        sub.run().accept(sender, rest);
    }

    private static String actor(CommandSender s) {
        return s instanceof Player p ? p.getName() : "CONSOLE";
    }

    private void help(CommandSender s) {
        plugin.messages().line(s, "help.header", Messages.ph("version", plugin.getPluginMeta().getVersion()));
        for (Sub sub : subs) {
            if (sub.permission() == null || s.hasPermission(sub.permission())) {
                plugin.messages().line(s, "help.line", Messages.ph("usage", plugin.messages().raw("help.usage." + sub.name())),
                        Messages.ph("description", plugin.messages().raw("help.description." + sub.name())));
            }
        }
    }

    private Campaign campaign(CommandSender s, String[] a) {
        if (a.length < 1) {
            plugin.messages().send(s, "command.missing-id");
            return null;
        }
        Campaign c = plugin.manager().definition(Campaign.normalizeId(a[0]));
        if (c == null) {
            plugin.messages().send(s, "result.UNKNOWN", Messages.ph("id", a[0]));
        }
        return c;
    }

    private void op(CommandSender s, String[] a, java.util.function.Function<String, CampaignManager.Result> action) {
        Campaign c = campaign(s, a);
        if (c == null) {
            return;
        }
        CampaignManager.Result r = action.apply(c.id);
        plugin.messages().send(s, r == CampaignManager.Result.OK ? "result.done" : "result." + r.name(), Messages.ph("id", c.id));
    }

    private void goals(CommandSender s, String[] a) {
        if (s instanceof Player p) {
            new GoalsMenu(plugin, p, 0).open();
            return;
        }
        List<Campaign> running = plugin.manager().running();
        plugin.messages().line(s, "command.goals-header", Messages.ph("count", running.size()));
        for (Campaign c : running) {
            RunState st = plugin.manager().state(c.id);
            plugin.messages().line(s, "command.goals-line", Messages.ph("id", c.id), Messages.parsed("name", c.name),
                    Messages.ph("progress", st.progress), Messages.ph("goal", c.goal),
                    Messages.ph("percent", ProgressMath.percent(st.progress, c.goal)),
                    Messages.ph("state", plugin.messages().raw("state." + st.state.name())));
        }
    }

    private void info(CommandSender s, String[] a) {
        Campaign c = campaign(s, a);
        if (c == null) {
            return;
        }
        if (s instanceof Player p && a.length == 1) {
            new CampaignMenu(plugin, p, c.id).open();
            return;
        }
        RunState st = plugin.manager().state(c.id);
        plugin.messages().line(s, "command.info", Messages.ph("id", c.id), Messages.parsed("name", c.name),
                Messages.ph("state", plugin.messages().raw("state." + st.state.name())),
                Messages.ph("type", plugin.messages().raw("objective." + c.type.name())),
                Messages.ph("progress", st.progress), Messages.ph("goal", c.goal),
                Messages.ph("percent", ProgressMath.percent(st.progress, c.goal)), Messages.ph("run", st.run),
                Messages.ph("participants", st.ranking().size()),
                Messages.ph("time", plugin.manager().timeText(c)));
        for (Milestone m : c.milestones) {
            plugin.messages().line(s, st.grants.contains("m:" + m.percent) ? "command.milestone-done" : "command.milestone-todo",
                    Messages.ph("percent", m.percent));
        }
    }

    private void top(CommandSender s, String[] a) {
        Campaign c = campaign(s, a);
        if (c == null) {
            return;
        }
        List<RunState.Contribution> ranking = plugin.manager().state(c.id).ranking();
        plugin.messages().line(s, "command.top-header", Messages.parsed("name", c.name));
        int size = Math.min(plugin.settings().topSize, ranking.size());
        if (size == 0) {
            plugin.messages().line(s, "command.top-empty");
        }
        for (int i = 0; i < size; i++) {
            RunState.Contribution con = ranking.get(i);
            plugin.messages().line(s, "announce.top-line", Messages.ph("rank", i + 1), Messages.ph("player", con.name),
                    Messages.ph("amount", con.amount));
        }
    }

    private void contribute(CommandSender s, String[] a) {
        Campaign c = campaign(s, a);
        if (c == null) {
            return;
        }
        if (c.type != ObjectiveType.DELIVER_ITEM) {
            plugin.messages().send(s, "deliver.not-delivery");
            return;
        }
        new DeliverMenu(plugin, (Player) s, c.id).open();
    }

    /** {@code /goals progress <id> <+n|-n|n|=n> [jugador]}. */
    private void progress(CommandSender s, String[] a) {
        Campaign c = campaign(s, a);
        if (c == null) {
            return;
        }
        if (a.length < 2) {
            plugin.messages().send(s, "command.usage", Messages.ph("usage", plugin.messages().raw("help.usage.progress")));
            return;
        }
        String v = a[1].strip();
        boolean set = v.startsWith("=");
        long value;
        try {
            value = Long.parseLong(set ? v.substring(1) : v.replace("+", ""));
        } catch (NumberFormatException e) {
            plugin.messages().send(s, "gui.input.bad-number");
            return;
        }
        UUID player = null;
        String name = null;
        if (a.length >= 3) {
            if (set) {
                plugin.messages().send(s, "command.set-no-player");
                return;
            }
            Player online = Bukkit.getPlayerExact(a[2]);
            OfflinePlayer op = online != null ? online : Bukkit.getOfflinePlayerIfCached(a[2]);
            if (op == null) {
                plugin.messages().send(s, "command.unknown-player", Messages.ph("value", a[2]));
                return;
            }
            player = op.getUniqueId();
            name = op.getName() == null ? a[2] : op.getName();
        }
        CampaignManager.Result r = plugin.manager().adjust(c.id, value, set, player, name, actor(s));
        if (r == CampaignManager.Result.OK) {
            RunState st = plugin.manager().state(c.id);
            plugin.messages().send(s, "command.progress-done", Messages.ph("id", c.id), Messages.ph("progress", st.progress),
                    Messages.ph("goal", c.goal));
        } else {
            plugin.messages().send(s, "result." + r.name(), Messages.ph("id", c.id));
        }
    }

    private void history(CommandSender s, String[] a) {
        String id = a.length >= 1 ? Campaign.normalizeId(a[0]) : null;
        int page = 0;
        if (a.length >= 2) {
            try {
                page = Math.max(0, Integer.parseInt(a[1]) - 1);
            } catch (NumberFormatException ignored) {
                page = 0;
            }
        }
        int offset = page * 10;
        int shown = page + 1;
        plugin.database().flushHistory();
        plugin.database().query("leer historial", st -> st.history(id, 10, offset), rows -> {
            plugin.messages().line(s, "command.history-header", Messages.ph("id", id == null ? "*" : id), Messages.ph("page", shown));
            if (rows == null || rows.isEmpty()) {
                plugin.messages().line(s, "command.history-empty");
                return;
            }
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            for (HistoryEntry h : rows) {
                plugin.messages().line(s, "command.history-line", Messages.ph("date", f.format(new Date(h.at()))),
                        Messages.ph("campaign", h.campaignId()), Messages.ph("actor", h.actor()),
                        Messages.ph("action", plugin.messages().raw("history-action." + h.action().name())),
                        Messages.ph("details", h.details()));
            }
        });
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender s = source.getSender();
        List<String> out = new ArrayList<>();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            for (Sub sub : subs) {
                if ((sub.permission() == null || s.hasPermission(sub.permission())) && sub.name().startsWith(prefix)) {
                    out.add(sub.name());
                }
            }
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && List.of("info", "top", "contribute", "start", "pause", "resume", "complete", "cancel", "progress", "history")
                .contains(sub)) {
            for (String id : plugin.manager().definitions().keySet()) {
                if (id.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(id);
                }
            }
        } else if (args.length == 3 && sub.equals("progress")) {
            out.addAll(List.of("+10", "-10", "=0"));
        } else if (args.length == 4 && sub.equals("progress")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
        }
        return out;
    }

    @Override
    public String permission() {
        return null;
    }
}
