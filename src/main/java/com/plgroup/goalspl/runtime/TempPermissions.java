package com.plgroup.goalspl.runtime;

import com.plgroup.goalspl.GoalsPL;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Permisos temporales de las recompensas {@code PERMISSION}, sin depender de un plugin de permisos: se dan con un
 * {@link PermissionAttachment} mientras el jugador está conectado y se guardan en la base de datos con su caducidad,
 * así vuelven al entrar y se quitan solos al caducar. Si ya lo tenía, la duración se suma a lo que le quedaba.
 */
public final class TempPermissions {

    private final GoalsPL plugin;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private final Map<UUID, Map<String, Long>> active = new HashMap<>();

    public TempPermissions(GoalsPL plugin) {
        this.plugin = plugin;
    }

    public void grant(Player p, String permission, long durationMs) {
        long now = System.currentTimeMillis();
        Map<String, Long> perms = active.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        long expires = Math.max(perms.getOrDefault(permission, now), now) + durationMs;
        perms.put(permission, expires);
        attachment(p).setPermission(permission, true);
        UUID id = p.getUniqueId();
        plugin.database().write("guardar un permiso temporal", st -> st.saveTempPermission(id, permission, expires));
    }

    private PermissionAttachment attachment(Player p) {
        return attachments.computeIfAbsent(p.getUniqueId(), k -> p.addAttachment(plugin));
    }

    public void onJoin(Player p) {
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        plugin.database().query("leer permisos temporales de " + p.getName(), st -> st.tempPermissions(id, now), perms -> {
            if (perms == null || perms.isEmpty() || !p.isOnline()) {
                return;
            }
            active.put(id, new HashMap<>(perms));
            PermissionAttachment a = attachment(p);
            perms.keySet().forEach(perm -> a.setPermission(perm, true));
        });
    }

    public void onQuit(Player p) {
        PermissionAttachment a = attachments.remove(p.getUniqueId());
        if (a != null) {
            p.removeAttachment(a);
        }
        active.remove(p.getUniqueId());
    }

    /** Quita a los conectados los permisos caducados (cada 20 s). */
    public void expire() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Map<String, Long>>> it = active.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<String, Long>> e = it.next();
            PermissionAttachment a = attachments.get(e.getKey());
            e.getValue().entrySet().removeIf(perm -> {
                if (perm.getValue() <= now) {
                    if (a != null) {
                        a.unsetPermission(perm.getKey());
                    }
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p != null) {
                        plugin.messages().send(p, "rewards.permission-expired",
                                com.plgroup.goalspl.lang.Messages.ph("permission", perm.getKey()));
                    }
                    return true;
                }
                return false;
            });
            if (e.getValue().isEmpty()) {
                it.remove();
            }
        }
    }

    /** Al apagar: quita todos los adjuntos (los permisos siguen guardados y vuelven al arrancar). */
    public void removeAll() {
        attachments.forEach((id, a) -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.removeAttachment(a);
            }
        });
        attachments.clear();
        active.clear();
    }
}
