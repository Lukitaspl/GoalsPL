package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Escribir un texto desde un menú (ID, nombre, cantidad, una recompensa…): el menú se cierra, se pide el texto con un
 * botón [Cancelar], y lo siguiente que escribe el jugador va al menú, no al chat. Caduca a los 120 s; escribir
 * {@code cancelar} o {@code cancel} vuelve al menú sin cambios. El texto llega al hilo principal.
 */
public final class ChatInput implements Listener {

    private static final long TIMEOUT_MS = 120_000;
    private static final Set<String> CANCEL = Set.of("cancelar", "cancel");

    private record Pending(Consumer<String> onText, Runnable onCancel, long expiresAt) {
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private final GoalsPL plugin;

    public ChatInput(GoalsPL plugin) {
        this.plugin = plugin;
    }

    public static boolean waiting(Player p) {
        Pending pd = PENDING.get(p.getUniqueId());
        return pd != null && pd.expiresAt() > System.currentTimeMillis();
    }

    public static void ask(GoalsPL plugin, Player p, String promptKey, Consumer<String> onText, Runnable onCancel,
                           TagResolver... ph) {
        Pending pending = new Pending(onText, onCancel, System.currentTimeMillis() + TIMEOUT_MS);
        PENDING.put(p.getUniqueId(), pending);
        p.closeInventory();
        plugin.messages().send(p, promptKey, ph);
        p.sendMessage(plugin.messages().text("gui.input.hint").append(plugin.messages().text("gui.input.cancel-button")
                .clickEvent(ClickEvent.callback(a -> {
                    if (PENDING.remove(p.getUniqueId(), pending)) {
                        Bukkit.getScheduler().runTask(plugin, pending.onCancel());
                    }
                }))));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (PENDING.remove(p.getUniqueId(), pending) && p.isOnline()) {
                plugin.messages().send(p, "gui.input.expired");
                pending.onCancel().run();
            }
        }, TIMEOUT_MS / 50 + 1);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Pending pd = PENDING.remove(e.getPlayer().getUniqueId());
        if (pd == null) {
            return;
        }
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (CANCEL.contains(text.toLowerCase(Locale.ROOT))) {
                pd.onCancel().run();
            } else {
                pd.onText().accept(text);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        PENDING.remove(e.getPlayer().getUniqueId());
    }

    public static void clear() {
        PENDING.clear();
    }
}
