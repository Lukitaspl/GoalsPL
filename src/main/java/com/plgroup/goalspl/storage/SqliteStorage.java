package com.plgroup.goalspl.storage;

import com.plgroup.goalspl.domain.CampaignState;
import com.plgroup.goalspl.domain.HistoryAction;
import com.plgroup.goalspl.runtime.RunState;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Base de datos SQLite ({@code data/goalspl.db}): estado y progreso de cada campaña, ejecuciones,
 * aportaciones por jugador, claves de lo ya entregado, recompensas (entregadas y pendientes), entregas de objetos,
 * permisos temporales e historial. Una sola conexión, usada solo desde el hilo de {@link Database} (o al arrancar,
 * antes de las tareas). Modo WAL con {@code synchronous=FULL}.
 */
public final class SqliteStorage {

    private final File file;
    private final File backupDir;
    private final Logger log;
    private Connection connection;

    public SqliteStorage(File file, File backupDir, Logger log) {
        this.file = file;
        this.backupDir = backupDir;
        this.log = log;
    }

    public void open() throws StorageException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new StorageException("No se pudo crear la carpeta " + parent);
        }
        boolean existed = file.exists() && file.length() > 0;
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=FULL");
                st.execute("PRAGMA busy_timeout=5000");
            }
            migrate(existed);
        } catch (ClassNotFoundException e) {
            throw new StorageException("No está el driver de SQLite (org.xerial:sqlite-jdbc). El servidor lo descarga"
                    + " al arrancar por la sección libraries de plugin.yml: revisa la conexión a Maven Central.", e);
        } catch (SQLException e) {
            throw new StorageException("No se pudo abrir la base de datos " + file.getName() + ": " + e.getMessage(), e);
        }
    }

    private void migrate(boolean existed) throws SQLException, StorageException {
        int current = readSchemaVersion();
        if (current > Migrations.latest()) {
            throw new StorageException("La base de datos es de una versión más nueva del plugin (esquema " + current
                    + ", este plugin conoce hasta " + Migrations.latest() + "). Instala la versión más reciente.");
        }
        List<Migrations.Migration> pending = Migrations.ALL.stream().filter(m -> m.version() > current).toList();
        if (pending.isEmpty()) {
            return;
        }
        if (existed) {
            backup("v" + current + "-a-v" + Migrations.latest());
        }
        for (Migrations.Migration m : pending) {
            boolean auto = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement st = connection.createStatement()) {
                for (String sql : m.statements()) {
                    st.execute(sql);
                }
                st.execute("INSERT OR REPLACE INTO meta(key, value) VALUES ('schema_version', '" + m.version() + "')");
                connection.commit();
                log.info("Base de datos: migración " + m.version() + " aplicada (" + m.description() + ").");
            } catch (SQLException e) {
                connection.rollback();
                throw new StorageException("Falló la migración " + m.version() + " (" + m.description() + "): "
                        + e.getMessage() + ". La base de datos se ha dejado como estaba.", e);
            } finally {
                connection.setAutoCommit(auto);
            }
        }
    }

    private int readSchemaVersion() throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='meta'")) {
            if (!rs.next()) {
                return 0;
            }
        }
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT value FROM meta WHERE key='schema_version'")) {
            return rs.next() ? Integer.parseInt(rs.getString(1)) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Copia coherente de la base de datos con {@code VACUUM INTO}; si no se puede, no se migra. */
    private void backup(String reason) throws StorageException {
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw new StorageException("No se pudo crear la carpeta de copias " + backupDir);
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File target = new File(backupDir, "goalspl-" + stamp + "-" + reason + ".db");
        try (Statement st = connection.createStatement()) {
            st.execute("VACUUM INTO '" + target.getAbsolutePath().replace("'", "''") + "'");
            log.info("Copia de seguridad antes de migrar: " + target.getName());
        } catch (SQLException e) {
            throw new StorageException("No se pudo hacer la copia de seguridad antes de migrar (" + e.getMessage()
                    + "). No se ha migrado nada.", e);
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                log.warning("Error al cerrar la base de datos: " + e.getMessage());
            }
            connection = null;
        }
    }

    // ---- estados y aportaciones ----

    /** Estados con sus aportaciones y claves entregadas de la ejecución actual. */
    public List<RunState> loadStates() throws StorageException {
        Map<String, RunState> out = new HashMap<>();
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT * FROM campaign_state")) {
                while (rs.next()) {
                    RunState s = new RunState(rs.getString("campaign_id"));
                    try {
                        s.state = CampaignState.valueOf(rs.getString("state"));
                    } catch (IllegalArgumentException e) {
                        log.warning("Estado desconocido '" + rs.getString("state") + "' para " + s.campaignId + "; queda en borrador.");
                        s.state = CampaignState.DRAFT;
                    }
                    s.run = rs.getInt("run");
                    s.progress = rs.getLong("progress");
                    s.startedAt = rs.getLong("started_at");
                    s.endsAt = rs.getLong("ends_at");
                    s.pausedRemaining = rs.getLong("paused_remaining");
                    s.updatedAt = rs.getLong("updated_at");
                    out.put(s.campaignId, s);
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT c.campaign_id, c.uuid, c.name, c.amount FROM contributions c"
                    + " JOIN campaign_state s ON s.campaign_id = c.campaign_id AND s.run = c.run")) {
                while (rs.next()) {
                    RunState s = out.get(rs.getString(1));
                    UUID id = parseUuid(rs.getString(2));
                    if (s != null && id != null) {
                        s.contributions.put(id, new RunState.Contribution(id, rs.getString(3), rs.getLong(4)));
                    }
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT g.campaign_id, g.grant_key FROM grants g"
                    + " JOIN campaign_state s ON s.campaign_id = g.campaign_id AND s.run = g.run")) {
                while (rs.next()) {
                    RunState s = out.get(rs.getString(1));
                    if (s != null) {
                        s.grants.add(rs.getString(2));
                    }
                }
            }
        } catch (SQLException e) {
            throw new StorageException("leer estados: " + e.getMessage(), e);
        }
        return new ArrayList<>(out.values());
    }

    /** Guarda estados y aportaciones en una sola transacción. */
    public void save(List<RunState.Row> states, List<RunState.ContributionRow> contributions) throws StorageException {
        transaction(() -> {
            try (PreparedStatement ps = connection.prepareStatement("INSERT OR REPLACE INTO campaign_state(campaign_id,"
                    + " state, run, progress, started_at, ends_at, paused_remaining, updated_at) VALUES (?,?,?,?,?,?,?,?)")) {
                for (RunState.Row s : states) {
                    ps.setString(1, s.campaignId());
                    ps.setString(2, s.state().name());
                    ps.setInt(3, s.run());
                    ps.setLong(4, s.progress());
                    ps.setLong(5, s.startedAt());
                    ps.setLong(6, s.endsAt());
                    ps.setLong(7, s.pausedRemaining());
                    ps.setLong(8, s.updatedAt());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = connection.prepareStatement("INSERT OR REPLACE INTO contributions(campaign_id,"
                    + " run, uuid, name, amount, updated_at) VALUES (?,?,?,?,?,?)")) {
                for (RunState.ContributionRow c : contributions) {
                    ps.setString(1, c.campaignId());
                    ps.setInt(2, c.run());
                    ps.setString(3, c.player().toString());
                    ps.setString(4, c.name());
                    ps.setLong(5, c.amount());
                    ps.setLong(6, c.at());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }, "guardar progreso");
    }

    public void deleteState(String campaignId) throws StorageException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM campaign_state WHERE campaign_id=?")) {
            ps.setString(1, campaignId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("borrar el estado de " + campaignId + ": " + e.getMessage(), e);
        }
    }

    // ---- ejecuciones ----

    public void insertRun(String campaignId, int run, long startedAt, long goal) throws StorageException {
        try (PreparedStatement ps = connection.prepareStatement("INSERT OR REPLACE INTO runs(campaign_id, run, started_at,"
                + " ended_at, outcome, progress, goal) VALUES (?,?,?,0,'RUNNING',0,?)")) {
            ps.setString(1, campaignId);
            ps.setInt(2, run);
            ps.setLong(3, startedAt);
            ps.setLong(4, goal);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("guardar la ejecución " + campaignId + "#" + run + ": " + e.getMessage(), e);
        }
    }

    public void finishRun(String campaignId, int run, long endedAt, String outcome, long progress) throws StorageException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE runs SET ended_at=?, outcome=?, progress=? WHERE campaign_id=? AND run=?")) {
            ps.setLong(1, endedAt);
            ps.setString(2, outcome);
            ps.setLong(3, progress);
            ps.setString(4, campaignId);
            ps.setInt(5, run);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("cerrar la ejecución " + campaignId + "#" + run + ": " + e.getMessage(), e);
        }
    }

    // ---- claves entregadas y recompensas ----

    public void insertGrant(String key, String campaignId, int run, long at) throws StorageException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO grants(grant_key, campaign_id, run, at) VALUES (?,?,?,?)")) {
            ps.setString(1, key);
            ps.setString(2, campaignId);
            ps.setInt(3, run);
            ps.setLong(4, at);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("guardar la clave " + key + ": " + e.getMessage(), e);
        }
    }

    /** Inserta una recompensa y devuelve su id. */
    public long insertReward(RewardRow r) throws StorageException {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO rewards(grant_key, campaign_id, run, uuid, name,"
                + " payload, status, attempts, last_error, created_at, delivered_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, r.grantKey());
            ps.setString(2, r.campaignId());
            ps.setInt(3, r.run());
            ps.setString(4, r.player().toString());
            ps.setString(5, r.name());
            ps.setString(6, r.payload());
            ps.setString(7, r.status());
            ps.setInt(8, r.attempts());
            ps.setString(9, r.lastError());
            ps.setLong(10, r.createdAt());
            ps.setLong(11, r.deliveredAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new StorageException("guardar una recompensa de " + r.name() + ": " + e.getMessage(), e);
        }
    }

    public void updateReward(RewardRow r) throws StorageException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE rewards SET payload=?, status=?, attempts=?,"
                + " last_error=?, delivered_at=? WHERE id=?")) {
            ps.setString(1, r.payload());
            ps.setString(2, r.status());
            ps.setInt(3, r.attempts());
            ps.setString(4, r.lastError());
            ps.setLong(5, r.deliveredAt());
            ps.setLong(6, r.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("actualizar la recompensa " + r.id() + ": " + e.getMessage(), e);
        }
    }

    public List<RewardRow> pendingRewards(UUID player) throws StorageException {
        return rewards("SELECT * FROM rewards WHERE uuid=? AND status='PENDING' ORDER BY id", ps -> ps.setString(1, player.toString()));
    }

    /** Recompensas de una campaña, más recientes primero. */
    public List<RewardRow> campaignRewards(String campaignId, int limit, int offset) throws StorageException {
        return rewards("SELECT * FROM rewards WHERE campaign_id=? ORDER BY id DESC LIMIT ? OFFSET ?", ps -> {
            ps.setString(1, campaignId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
        });
    }

    /** Número de recompensas pendientes de cada jugador conectado. */
    public Map<UUID, Integer> pendingCounts(List<UUID> players) throws StorageException {
        Map<UUID, Integer> out = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM rewards WHERE uuid=? AND status='PENDING'")) {
            for (UUID p : players) {
                ps.setString(1, p.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        out.put(p, rs.getInt(1));
                    }
                }
            }
        } catch (SQLException e) {
            throw new StorageException("contar recompensas pendientes: " + e.getMessage(), e);
        }
        return out;
    }

    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private List<RewardRow> rewards(String sql, Binder binder) throws StorageException {
        List<RewardRow> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = parseUuid(rs.getString("uuid"));
                    if (id == null) {
                        continue;
                    }
                    out.add(new RewardRow(rs.getLong("id"), rs.getString("grant_key"), rs.getString("campaign_id"),
                            rs.getInt("run"), id, rs.getString("name"), rs.getString("payload"), rs.getString("status"),
                            rs.getInt("attempts"), rs.getString("last_error"), rs.getLong("created_at"),
                            rs.getLong("delivered_at")));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("leer recompensas: " + e.getMessage(), e);
        }
        return out;
    }

    // ---- entregas ----

    public void insertDelivery(DeliveryRow d) throws StorageException {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO deliveries(at, campaign_id, run, uuid, name,"
                + " material, amount, status) VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setLong(1, d.at());
            ps.setString(2, d.campaignId());
            ps.setInt(3, d.run());
            ps.setString(4, d.player().toString());
            ps.setString(5, d.name());
            ps.setString(6, d.material());
            ps.setLong(7, d.amount());
            ps.setString(8, d.status());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("guardar una entrega de " + d.name() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Para devolver: suma por jugador y material de las entregas aún no devueltas de una ejecución, y las marca
     * como devueltas en la misma transacción (así no se devuelven dos veces).
     */
    public List<DeliveryRow> takeRefunds(String campaignId, int run, long at) throws StorageException {
        List<DeliveryRow> out = new ArrayList<>();
        transaction(() -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT uuid, MAX(name), material, SUM(amount)"
                    + " FROM deliveries WHERE campaign_id=? AND run=? AND status='DELIVERED' GROUP BY uuid, material")) {
                ps.setString(1, campaignId);
                ps.setInt(2, run);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID id = parseUuid(rs.getString(1));
                        if (id != null) {
                            out.add(new DeliveryRow(at, campaignId, run, id, rs.getString(2), rs.getString(3),
                                    rs.getLong(4), "REFUNDED"));
                        }
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE deliveries SET status='REFUNDED' WHERE campaign_id=? AND run=? AND status='DELIVERED'")) {
                ps.setString(1, campaignId);
                ps.setInt(2, run);
                ps.executeUpdate();
            }
        }, "preparar devoluciones");
        return out;
    }

    // ---- permisos temporales ----

    public void saveTempPermission(UUID player, String permission, long expiresAt) throws StorageException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO temp_permissions(uuid, permission, expires_at) VALUES (?,?,?)")) {
            ps.setString(1, player.toString());
            ps.setString(2, permission);
            ps.setLong(3, expiresAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("guardar un permiso temporal: " + e.getMessage(), e);
        }
    }

    /** Permisos temporales vigentes de un jugador: permiso → caducidad. */
    public Map<String, Long> tempPermissions(UUID player, long now) throws StorageException {
        Map<String, Long> out = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT permission, expires_at FROM temp_permissions WHERE uuid=? AND expires_at>?")) {
            ps.setString(1, player.toString());
            ps.setLong(2, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString(1), rs.getLong(2));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("leer permisos temporales: " + e.getMessage(), e);
        }
        return out;
    }

    // ---- historial ----

    public void appendHistory(List<HistoryEntry> entries) throws StorageException {
        transaction(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO history(at, campaign_id, actor, action, details) VALUES (?,?,?,?,?)")) {
                for (HistoryEntry h : entries) {
                    ps.setLong(1, h.at());
                    ps.setString(2, h.campaignId());
                    ps.setString(3, h.actor());
                    ps.setString(4, h.action().name());
                    ps.setString(5, h.details() == null ? "" : h.details());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }, "guardar historial");
    }

    /** Historial más reciente primero; {@code campaignId} nulo = de todas. */
    public List<HistoryEntry> history(String campaignId, int limit, int offset) throws StorageException {
        String sql = campaignId == null
                ? "SELECT * FROM history ORDER BY at DESC, id DESC LIMIT ? OFFSET ?"
                : "SELECT * FROM history WHERE campaign_id=? ORDER BY at DESC, id DESC LIMIT ? OFFSET ?";
        List<HistoryEntry> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int i = 1;
            if (campaignId != null) {
                ps.setString(i++, campaignId);
            }
            ps.setInt(i++, limit);
            ps.setInt(i, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    HistoryAction action;
                    try {
                        action = HistoryAction.valueOf(rs.getString("action"));
                    } catch (IllegalArgumentException e) {
                        action = HistoryAction.ERROR;
                    }
                    out.add(new HistoryEntry(rs.getLong("at"), rs.getString("campaign_id"), rs.getString("actor"), action,
                            rs.getString("details")));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("leer historial: " + e.getMessage(), e);
        }
        return out;
    }

    /**
     * Retención: borra historial, entregas, recompensas ya entregadas, claves y aportaciones de ejecuciones
     * terminadas anteriores a {@code before}, y permisos caducados. Nunca borra recompensas pendientes ni datos de
     * la ejecución actual de una campaña.
     */
    public int purge(long before, long now) throws StorageException {
        int[] total = {0};
        String finishedRun = " AND NOT EXISTS (SELECT 1 FROM campaign_state s WHERE s.campaign_id = %1$s.campaign_id"
                + " AND s.run = %1$s.run)";
        transaction(() -> {
            String[] sqls = {
                    "DELETE FROM history WHERE at < ?",
                    "DELETE FROM deliveries WHERE at < ?" + String.format(finishedRun, "deliveries"),
                    "DELETE FROM rewards WHERE status='DELIVERED' AND created_at < ?",
                    "DELETE FROM grants WHERE at < ?" + String.format(finishedRun, "grants"),
                    "DELETE FROM contributions WHERE updated_at < ?" + String.format(finishedRun, "contributions"),
                    "DELETE FROM runs WHERE ended_at > 0 AND ended_at < ?"
            };
            for (String sql : sqls) {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setLong(1, before);
                    total[0] += ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM temp_permissions WHERE expires_at <= ?")) {
                ps.setLong(1, now);
                total[0] += ps.executeUpdate();
            }
        }, "limpiar datos antiguos");
        return total[0];
    }

    // ---- utilidades ----

    public String meta(String key) throws StorageException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT value FROM meta WHERE key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new StorageException("leer meta " + key + ": " + e.getMessage(), e);
        }
    }

    private static UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private interface SqlWork {
        void run() throws SQLException;
    }

    private void transaction(SqlWork work, String what) throws StorageException {
        try {
            boolean auto = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                work.run();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(auto);
            }
        } catch (SQLException e) {
            throw new StorageException(what + ": " + e.getMessage(), e);
        }
    }
}
