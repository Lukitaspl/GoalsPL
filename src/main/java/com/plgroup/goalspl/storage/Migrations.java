package com.plgroup.goalspl.storage;

import java.util.List;

/**
 * Esquema versionado de la base de datos. Cada migración lleva su número; al arrancar se aplican en orden las que
 * falten, cada una en su propia transacción (si falla, se deshace y la base de datos queda como estaba). Antes de
 * migrar una base de datos con datos se hace una copia en {@code data/backups/}.
 */
public final class Migrations {

    /** Una migración: número de versión, descripción y sentencias SQL. */
    public record Migration(int version, String description, List<String> statements) {
    }

    public static final List<Migration> ALL = List.of(
            new Migration(1, "Esquema inicial", List.of(
                    "CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
                    "CREATE TABLE IF NOT EXISTS campaign_state (campaign_id TEXT PRIMARY KEY, state TEXT NOT NULL,"
                            + " run INTEGER NOT NULL, progress INTEGER NOT NULL, started_at INTEGER NOT NULL,"
                            + " ends_at INTEGER NOT NULL, paused_remaining INTEGER NOT NULL, updated_at INTEGER NOT NULL)",
                    "CREATE TABLE IF NOT EXISTS runs (campaign_id TEXT NOT NULL, run INTEGER NOT NULL,"
                            + " started_at INTEGER NOT NULL, ended_at INTEGER NOT NULL DEFAULT 0,"
                            + " outcome TEXT NOT NULL DEFAULT 'RUNNING', progress INTEGER NOT NULL DEFAULT 0,"
                            + " goal INTEGER NOT NULL, PRIMARY KEY (campaign_id, run))",
                    "CREATE TABLE IF NOT EXISTS contributions (campaign_id TEXT NOT NULL, run INTEGER NOT NULL,"
                            + " uuid TEXT NOT NULL, name TEXT NOT NULL, amount INTEGER NOT NULL, updated_at INTEGER NOT NULL,"
                            + " PRIMARY KEY (campaign_id, run, uuid))",
                    "CREATE TABLE IF NOT EXISTS grants (grant_key TEXT PRIMARY KEY, campaign_id TEXT NOT NULL,"
                            + " run INTEGER NOT NULL, at INTEGER NOT NULL)",
                    "CREATE INDEX IF NOT EXISTS idx_grants_run ON grants(campaign_id, run)",
                    "CREATE TABLE IF NOT EXISTS rewards (id INTEGER PRIMARY KEY AUTOINCREMENT, grant_key TEXT NOT NULL,"
                            + " campaign_id TEXT NOT NULL, run INTEGER NOT NULL, uuid TEXT NOT NULL, name TEXT NOT NULL,"
                            + " payload TEXT NOT NULL, status TEXT NOT NULL, attempts INTEGER NOT NULL,"
                            + " last_error TEXT NOT NULL, created_at INTEGER NOT NULL, delivered_at INTEGER NOT NULL)",
                    "CREATE INDEX IF NOT EXISTS idx_rewards_player ON rewards(uuid, status)",
                    "CREATE INDEX IF NOT EXISTS idx_rewards_campaign ON rewards(campaign_id, created_at)",
                    "CREATE TABLE IF NOT EXISTS deliveries (id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL,"
                            + " campaign_id TEXT NOT NULL, run INTEGER NOT NULL, uuid TEXT NOT NULL, name TEXT NOT NULL,"
                            + " material TEXT NOT NULL, amount INTEGER NOT NULL, status TEXT NOT NULL)",
                    "CREATE INDEX IF NOT EXISTS idx_deliveries_run ON deliveries(campaign_id, run, status)",
                    "CREATE TABLE IF NOT EXISTS temp_permissions (uuid TEXT NOT NULL, permission TEXT NOT NULL,"
                            + " expires_at INTEGER NOT NULL, PRIMARY KEY (uuid, permission))",
                    "CREATE TABLE IF NOT EXISTS history (id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL,"
                            + " campaign_id TEXT NOT NULL, actor TEXT NOT NULL, action TEXT NOT NULL, details TEXT NOT NULL)",
                    "CREATE INDEX IF NOT EXISTS idx_history_campaign ON history(campaign_id, at)",
                    "CREATE INDEX IF NOT EXISTS idx_history_at ON history(at)"
            ))
    );

    public static int latest() {
        return ALL.get(ALL.size() - 1).version();
    }

    private Migrations() {
    }
}
