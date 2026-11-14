package com.plgroup.goalspl;

import com.plgroup.goalspl.api.GoalsAPI;
import com.plgroup.goalspl.command.GoalsCommand;
import com.plgroup.goalspl.config.PluginConfig;
import com.plgroup.goalspl.config.RewardPresets;
import com.plgroup.goalspl.domain.CampaignValidator;
import com.plgroup.goalspl.gui.ChatInput;
import com.plgroup.goalspl.gui.Menu;
import com.plgroup.goalspl.gui.MenuListener;
import com.plgroup.goalspl.integration.EventsBridge;
import com.plgroup.goalspl.lang.Messages;
import com.plgroup.goalspl.listener.PlayerListener;
import com.plgroup.goalspl.listener.ProgressListener;
import com.plgroup.goalspl.runtime.ApiImpl;
import com.plgroup.goalspl.runtime.BossbarService;
import com.plgroup.goalspl.runtime.CampaignManager;
import com.plgroup.goalspl.runtime.DeliveryService;
import com.plgroup.goalspl.runtime.RewardService;
import com.plgroup.goalspl.runtime.RunState;
import com.plgroup.goalspl.runtime.ServerEnv;
import com.plgroup.goalspl.runtime.TempPermissions;
import com.plgroup.goalspl.storage.Database;
import com.plgroup.goalspl.storage.GoalFiles;
import com.plgroup.goalspl.storage.SqliteStorage;
import com.plgroup.goalspl.storage.StorageException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Clase principal. Arranca en orden (config.yml, messages.yml, rewards.yml, base de datos, goals.yml), registra
 * escuchadores, {@code /goals} y la API, y al apagar quita bossbars y permisos temporales, guarda el progreso y
 * cierra la base de datos. Si la base de datos no se puede abrir, el plugin se desactiva sin tocar nada.
 */
public final class GoalsPL extends JavaPlugin {

    private PluginConfig settings;
    private Messages messages;
    private RewardPresets presets;
    private Database database;
    private GoalFiles files;
    private CampaignManager manager;
    private RewardService rewards;
    private BossbarService bossbars;
    private DeliveryService deliveries;
    private TempPermissions permissions;
    private EventsBridge eventsPl;
    private CampaignValidator.Env env;
    private boolean ready;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        if (PluginConfig.migrate(getConfig(), getLogger())) {
            saveConfig();
        }
        settings = PluginConfig.load(getConfig(), getLogger());
        messages = Messages.load(this, settings.language);
        presets = new RewardPresets();
        presets.load(this).forEach(getLogger()::warning);
        if (!new File(getDataFolder(), "goals.yml").exists()) {
            saveResource("goals.yml", false);
        }

        eventsPl = new EventsBridge();
        env = new ServerEnv(this);
        bossbars = new BossbarService(this);
        rewards = new RewardService(this);
        deliveries = new DeliveryService(this);
        permissions = new TempPermissions(this);

        SqliteStorage storage = new SqliteStorage(new File(getDataFolder(), settings.databaseFile),
                new File(getDataFolder(), "data/backups"), getLogger());
        database = new Database(this, storage);
        files = new GoalFiles(new File(getDataFolder(), "goals.yml"), getLogger());
        manager = new CampaignManager(this, files);
        try {
            storage.open();
            GoalFiles.LoadResult loaded = files.load(settings.zone);
            files.logProblems(loaded.problems());
            manager.load(loaded.campaigns(), storage.loadStates());
        } catch (StorageException e) {
            getLogger().log(Level.SEVERE, "No se pudo abrir la base de datos: " + e.getMessage()
                    + ". GoalsPL se desactiva para no perder ni corromper datos; el servidor sigue funcionando.");
            storage.close();
            database = null;
            manager = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        var pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(new ChatInput(this), this);
        pm.registerEvents(new ProgressListener(this), this);
        pm.registerEvents(new PlayerListener(this), this);

        registerCommand("goalspl", "Metas comunitarias: campañas, progreso, aportes y recompensas.",
                List.of("goals"), new GoalsCommand(this));
        getServer().getServicesManager().register(GoalsAPI.class, new ApiImpl(this), this, ServicePriority.Normal);

        for (Player p : Bukkit.getOnlinePlayers()) {
            permissions.onJoin(p);
        }
        manager.startClock();
        purgeOld();
        ready = true;
        long running = manager.running().size();
        getLogger().info("GoalsPL " + getPluginMeta().getVersion() + " activado: " + manager.definitions().size()
                + " campañas (" + running + " en curso).");
    }

    /**
     * {@code /goals reload}: vuelve a leer config.yml, messages.yml, rewards.yml y goals.yml. Las campañas en curso
     * siguen con su progreso; el archivo de la base de datos ({@code storage.file}) solo cambia al reiniciar.
     */
    public void reloadAll(Runnable onDone) {
        String oldDb = settings.databaseFile;
        reloadConfig();
        getConfig().options().copyDefaults(true);
        if (PluginConfig.migrate(getConfig(), getLogger())) {
            saveConfig();
        }
        settings = PluginConfig.load(getConfig(), getLogger());
        messages = Messages.load(this, settings.language);
        presets.load(this).forEach(getLogger()::warning);
        if (!oldDb.equals(settings.databaseFile)) {
            getLogger().warning("storage.file ha cambiado: se aplicará al reiniciar el servidor.");
        }
        manager.reloadDefinitions(onDone);
    }

    /** Retención: borra en segundo plano lo anterior a {@code history.retention-days}. */
    public void purgeOld() {
        long now = System.currentTimeMillis();
        long before = now - settings.historyRetentionDays * 86_400_000L;
        database.query("limpiar datos antiguos", st -> st.purge(before, now), n -> {
            if (n != null && n > 0) {
                getLogger().info("Retención: " + n + " registros antiguos borrados.");
            }
        });
    }

    @Override
    public void onDisable() {
        if (!ready) {
            return;
        }
        ready = false;
        manager.stopClock();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) {
                p.closeInventory();
            }
        }
        bossbars.removeAll();
        permissions.removeAll();
        ChatInput.clear();
        HandlerList.unregisterAll(this);
        getServer().getServicesManager().unregisterAll(this);
        List<RunState.ContributionRow> cons = new ArrayList<>();
        List<RunState.Row> rows = manager.dirtyRows(cons);
        database.shutdown(st -> {
            if (!rows.isEmpty()) {
                st.save(rows, cons);
            }
        });
        getLogger().info("GoalsPL desactivado; progreso guardado.");
    }

    public PluginConfig settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public RewardPresets presets() {
        return presets;
    }

    public Database database() {
        return database;
    }

    public CampaignManager manager() {
        return manager;
    }

    public RewardService rewards() {
        return rewards;
    }

    public BossbarService bossbars() {
        return bossbars;
    }

    public DeliveryService deliveries() {
        return deliveries;
    }

    public TempPermissions permissions() {
        return permissions;
    }

    public EventsBridge eventsPl() {
        return eventsPl;
    }

    public CampaignValidator.Env env() {
        return env;
    }
}
