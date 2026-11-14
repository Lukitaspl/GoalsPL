package com.plgroup.goalspl.storage;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Fachada asíncrona: todo el disco (SQLite y la escritura de goals.yml) va a un único hilo propio
 * ({@code GoalsPL-IO}), así el hilo principal nunca espera al disco y las escrituras salen en el mismo orden
 * en que se pidieron. Los resultados de las consultas vuelven al hilo principal. El historial se guarda por lotes.
 */
public final class Database {

    /** Consulta contra la base de datos. */
    public interface Call<T> {
        T run(SqliteStorage storage) throws StorageException;
    }

    /** Escritura contra la base de datos. */
    public interface Work {
        void run(SqliteStorage storage) throws StorageException;
    }

    private static final int MAX_PENDING = 20_000;

    private final Plugin plugin;
    private final SqliteStorage storage;
    private final ExecutorService executor;
    private final ConcurrentLinkedQueue<HistoryEntry> history = new ConcurrentLinkedQueue<>();
    private volatile boolean closed;

    public Database(Plugin plugin, SqliteStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "GoalsPL-IO");
            t.setDaemon(true);
            return t;
        });
    }

    /** Acceso directo y bloqueante: solo al arrancar, antes de iniciar las tareas. */
    public SqliteStorage blocking() {
        return storage;
    }

    public void write(String what, Work work) {
        if (closed) {
            return;
        }
        executor.execute(() -> {
            try {
                work.run(storage);
            } catch (StorageException e) {
                plugin.getLogger().log(Level.SEVERE, "[BD] No se pudo " + what + ": " + e.getMessage());
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "[BD] Error inesperado al " + what, e);
            }
        });
    }

    /** Consulta en segundo plano; {@code onMain} recibe el resultado en el hilo principal ({@code null} si falla). */
    public <T> void query(String what, Call<T> call, Consumer<T> onMain) {
        if (closed) {
            return;
        }
        executor.execute(() -> {
            T result = null;
            try {
                result = call.run(storage);
            } catch (StorageException e) {
                plugin.getLogger().log(Level.SEVERE, "[BD] No se pudo " + what + ": " + e.getMessage());
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "[BD] Error inesperado al " + what, e);
            }
            T r = result;
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> onMain.accept(r));
            }
        });
    }

    /**
     * Escribe un archivo de texto entero de forma atómica (archivo temporal y después se renombra): si el
     * servidor se cae a mitad, queda el archivo anterior completo. {@code onDone} se llama en el hilo principal
     * con {@code true} si se guardó.
     */
    public void writeFile(File target, String content, Consumer<Boolean> onDone) {
        if (closed) {
            onDone.accept(writeNow(target, content));
            return;
        }
        executor.execute(() -> {
            boolean ok = writeNow(target, content);
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> onDone.accept(ok));
            }
        });
    }

    private boolean writeNow(File target, String content) {
        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("no se pudo crear " + parent);
            }
            File tmp = new File(target.getPath() + ".tmp");
            Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar " + target.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public void history(HistoryEntry entry) {
        if (history.size() >= MAX_PENDING) {
            history.poll();
        }
        history.add(entry);
    }

    /** Manda a disco el historial pendiente. */
    public void flushHistory() {
        List<HistoryEntry> h = new ArrayList<>();
        HistoryEntry e;
        while ((e = history.poll()) != null) {
            h.add(e);
        }
        if (!h.isEmpty()) {
            write("guardar el historial (" + h.size() + " entradas)", s -> s.appendHistory(h));
        }
    }

    /** Ejecuta {@code last} (lo último por guardar), manda el historial, espera al hilo (hasta 15 s) y cierra. */
    public void shutdown(Work last) {
        write("guardar al apagar", last);
        flushHistory();
        closed = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("[BD] El disco no terminó en 15 s; puede faltar lo último guardado.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        storage.close();
    }
}
