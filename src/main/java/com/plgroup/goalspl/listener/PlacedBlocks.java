package com.plgroup.goalspl.listener;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;

/**
 * Bloques colocados por jugadores, para que colocar y romper el mismo bloque no sume progreso. Se guardan en los
 * datos persistentes del propio chunk (sobreviven a reinicios y se van con el chunk), como posiciones empaquetadas
 * en un {@code long[]}. Solo se marcan los bloques que cuentan para alguna campaña de romper bloques activa.
 */
public final class PlacedBlocks {

    private static final int MAX_PER_CHUNK = 4096;
    private final NamespacedKey key;

    public PlacedBlocks(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "placed");
    }

    static long pack(Block b) {
        return ((long) (b.getX() & 15) << 40) | ((long) (b.getZ() & 15) << 32) | (b.getY() & 0xFFFFFFFFL);
    }

    public void mark(Block b) {
        PersistentDataContainer pdc = b.getChunk().getPersistentDataContainer();
        long[] list = pdc.getOrDefault(key, PersistentDataType.LONG_ARRAY, new long[0]);
        long v = pack(b);
        for (long l : list) {
            if (l == v) {
                return;
            }
        }
        long[] next;
        if (list.length >= MAX_PER_CHUNK) {
            next = Arrays.copyOfRange(list, 1, list.length + 1);
            next[next.length - 1] = v;
        } else {
            next = Arrays.copyOf(list, list.length + 1);
            next[list.length] = v;
        }
        pdc.set(key, PersistentDataType.LONG_ARRAY, next);
    }

    /** Quita la marca y devuelve si el bloque estaba marcado como colocado. */
    public boolean take(Block b) {
        PersistentDataContainer pdc = b.getChunk().getPersistentDataContainer();
        long[] list = pdc.get(key, PersistentDataType.LONG_ARRAY);
        if (list == null) {
            return false;
        }
        long v = pack(b);
        for (int i = 0; i < list.length; i++) {
            if (list[i] == v) {
                long[] next = new long[list.length - 1];
                System.arraycopy(list, 0, next, 0, i);
                System.arraycopy(list, i + 1, next, i, list.length - i - 1);
                if (next.length == 0) {
                    pdc.remove(key);
                } else {
                    pdc.set(key, PersistentDataType.LONG_ARRAY, next);
                }
                return true;
            }
        }
        return false;
    }
}
