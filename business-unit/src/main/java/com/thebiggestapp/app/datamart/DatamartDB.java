package com.thebiggestapp.app.datamart;

import com.thebiggestapp.app.config.Config;

import java.sql.*;

/**
 * Gestiona el datamart SQLite con tres tablas:
 *   - weather_events      : últimas lecturas meteorológicas por ciudad
 *   - predicthq_events    : eventos de impacto (vuelos, conciertos, deportes...)
 *   - ticketmaster_events : eventos de entretenimiento
 *
 * Diseño justificado:
 *   - SQLite: portable, sin servidor, suficiente para el volumen del proyecto.
 *   - weather_events usa ciudad como PK (INSERT OR REPLACE) → siempre el dato más reciente.
 *   - Índices en ciudad, categoría y fecha para acelerar las consultas del usuario final.
 *   - La reconstrucción desde el event store se basa en INSERT OR REPLACE (idempotente).
 */
public class DatamartDB {

    private static final String DB_URL_PREFIX = "jdbc:sqlite:";
    private static DatamartDB instance;
    private Connection conn;

    private DatamartDB() {
        try {
            String dbPath = Config.get("DATAMART_PATH", "datamart/business_unit.db");
            java.nio.file.Path dir = java.nio.file.Paths.get(dbPath).getParent();
            if (dir != null) java.nio.file.Files.createDirectories(dir);

            conn = DriverManager.getConnection(DB_URL_PREFIX + dbPath);
            conn.setAutoCommit(true);
            initSchema();
            System.out.println("[DatamartDB] Base de datos SQLite lista en: " + dbPath);
        } catch (Exception e) {
            System.err.println("[DatamartDB] Error al inicializar: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static synchronized DatamartDB getInstance() {
        if (instance == null) instance = new DatamartDB();
        return instance;
    }

    // -----------------------------------------------------------------------
    // Schema
    // -----------------------------------------------------------------------

    private void initSchema() throws SQLException {
        try (Statement st = conn.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS weather_events (
                    ciudad      TEXT PRIMARY KEY,
                    ts          TEXT NOT NULL,
                    temp        REAL,
                    temp_min    REAL,
                    temp_max    REAL,
                    descripcion TEXT,
                    humidity    INTEGER,
                    wind_speed  REAL,
                    ss          TEXT
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS predicthq_events (
                    id           TEXT PRIMARY KEY,
                    ts           TEXT NOT NULL,
                    titulo       TEXT,
                    categoria    TEXT,
                    ciudad       TEXT,
                    fecha_inicio TEXT,
                    fecha_fin    TEXT,
                    latitud      REAL,
                    longitud     REAL,
                    impacto      INTEGER,
                    ss           TEXT
                )
                """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_phq_ciudad    ON predicthq_events(ciudad)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_phq_categoria ON predicthq_events(categoria)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_phq_fecha     ON predicthq_events(fecha_inicio)");

            st.execute("""
                CREATE TABLE IF NOT EXISTS ticketmaster_events (
                    id        TEXT PRIMARY KEY,
                    ts        TEXT NOT NULL,
                    nombre    TEXT,
                    fecha     TEXT,
                    hora      TEXT,
                    ciudad    TEXT,
                    venue     TEXT,
                    categoria TEXT,
                    url       TEXT,
                    ss        TEXT
                )
                """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_tm_ciudad ON ticketmaster_events(ciudad)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_tm_fecha  ON ticketmaster_events(fecha)");
        }
    }

    // -----------------------------------------------------------------------
    // Upserts
    // -----------------------------------------------------------------------

    public synchronized void upsertWeather(String ciudad, String ts, double temp,
                                           double tempMin, double tempMax, String descripcion,
                                           int humidity, double windSpeed, String ss) {
        String sql = """
            INSERT OR REPLACE INTO weather_events
              (ciudad, ts, temp, temp_min, temp_max, descripcion, humidity, wind_speed, ss)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ciudad);
            ps.setString(2, ts);
            ps.setDouble(3, temp);
            ps.setDouble(4, tempMin);
            ps.setDouble(5, tempMax);
            ps.setString(6, descripcion);
            ps.setInt(7, humidity);
            ps.setDouble(8, windSpeed);
            ps.setString(9, ss);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DatamartDB] Error upsertWeather: " + e.getMessage());
        }
    }

    public synchronized void upsertPredictHQ(String id, String ts, String titulo,
                                             String categoria, String ciudad,
                                             String fechaInicio, String fechaFin,
                                             double latitud, double longitud,
                                             int impacto, String ss) {
        String sql = """
            INSERT OR REPLACE INTO predicthq_events
              (id, ts, titulo, categoria, ciudad, fecha_inicio, fecha_fin,
               latitud, longitud, impacto, ss)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, ts);
            ps.setString(3, titulo);
            ps.setString(4, categoria);
            ps.setString(5, ciudad);
            ps.setString(6, fechaInicio);
            ps.setString(7, fechaFin);
            ps.setDouble(8, latitud);
            ps.setDouble(9, longitud);
            ps.setInt(10, impacto);
            ps.setString(11, ss);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DatamartDB] Error upsertPredictHQ: " + e.getMessage());
        }
    }

    public synchronized void upsertTicketmaster(String id, String ts, String nombre,
                                                String fecha, String hora, String ciudad,
                                                String venue, String categoria, String url,
                                                String ss) {
        String sql = """
            INSERT OR REPLACE INTO ticketmaster_events
              (id, ts, nombre, fecha, hora, ciudad, venue, categoria, url, ss)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, ts);
            ps.setString(3, nombre);
            ps.setString(4, fecha);
            ps.setString(5, hora);
            ps.setString(6, ciudad);
            ps.setString(7, venue);
            ps.setString(8, categoria);
            ps.setString(9, url);
            ps.setString(10, ss);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DatamartDB] Error upsertTicketmaster: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    public Connection getConnection() { return conn; }

    public ResultSet queryAllWeather() throws SQLException {
        return conn.createStatement()
                .executeQuery("SELECT * FROM weather_events ORDER BY ciudad");
    }

    public ResultSet queryWeatherByCiudad(String ciudad) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM weather_events WHERE LOWER(ciudad) = LOWER(?)");
        ps.setString(1, ciudad);
        return ps.executeQuery();
    }

    public ResultSet queryPredictHQByImpact(int minImpacto, String ciudad) throws SQLException {
        String sql = ciudad == null
                ? "SELECT * FROM predicthq_events WHERE impacto >= ? ORDER BY impacto DESC LIMIT 100"
                : "SELECT * FROM predicthq_events WHERE impacto >= ? AND LOWER(ciudad) = LOWER(?) ORDER BY impacto DESC LIMIT 100";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, minImpacto);
        if (ciudad != null) ps.setString(2, ciudad);
        return ps.executeQuery();
    }

    public ResultSet queryPredictHQByCategory(String categoria) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM predicthq_events WHERE LOWER(categoria) LIKE LOWER(?) ORDER BY fecha_inicio LIMIT 100");
        ps.setString(1, "%" + categoria + "%");
        return ps.executeQuery();
    }

    public ResultSet queryPredictHQCategories() throws SQLException {
        return conn.createStatement().executeQuery(
                "SELECT categoria, COUNT(*) as total, AVG(impacto) as avg_impacto " +
                        "FROM predicthq_events GROUP BY categoria ORDER BY total DESC");
    }

    public ResultSet queryTicketmaster(String ciudad, String fecha) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM ticketmaster_events WHERE 1=1");
        if (ciudad != null) sql.append(" AND LOWER(ciudad) = LOWER(?)");
        if (fecha  != null) sql.append(" AND fecha LIKE ?");
        sql.append(" ORDER BY fecha, hora LIMIT 100");

        PreparedStatement ps = conn.prepareStatement(sql.toString());
        int idx = 1;
        if (ciudad != null) ps.setString(idx++, ciudad);
        if (fecha  != null) ps.setString(idx,   fecha + "%");
        return ps.executeQuery();
    }

    public ResultSet querySummary() throws SQLException {
        return conn.createStatement().executeQuery(
                "SELECT 'weather' as tabla, COUNT(*) as total FROM weather_events " +
                        "UNION ALL SELECT 'predicthq', COUNT(*) FROM predicthq_events " +
                        "UNION ALL SELECT 'ticketmaster', COUNT(*) FROM ticketmaster_events");
    }

    public ResultSet queryTopCiudades(int limit) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT ciudad, COUNT(*) as total_eventos, AVG(impacto) as avg_impacto " +
                        "FROM predicthq_events WHERE ciudad IS NOT NULL AND ciudad != '' " +
                        "GROUP BY ciudad ORDER BY total_eventos DESC LIMIT ?");
        ps.setInt(1, limit);
        return ps.executeQuery();
    }
}