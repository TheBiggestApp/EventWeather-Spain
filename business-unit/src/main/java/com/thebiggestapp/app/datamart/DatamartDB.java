package com.thebiggestapp.app.datamart;

import com.thebiggestapp.app.config.Config;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;

public class DatamartDB {

    private static final String JDBC_PREFIX = "jdbc:sqlite:";
    private static final String KEY_DATAMART_PATH = "datamart.path";
    private static final String DEFAULT_DATAMART_PATH = "datamart/business_unit.db";

    private static DatamartDB instance;
    private final Connection connection;

    private DatamartDB() {
        String dbPath = Config.get(KEY_DATAMART_PATH, DEFAULT_DATAMART_PATH);
        connection = openConnection(dbPath);
        initSchema();
        System.out.println("[DatamartDB] Base de datos lista en: " + dbPath);
    }

    public static synchronized DatamartDB getInstance() {
        if (instance == null) {
            instance = new DatamartDB();
        }
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }

    // -----------------------------------------------------------------------
    // Schema
    // -----------------------------------------------------------------------

    private void initSchema() {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS weather_events (
                    ciudad      TEXT,
                    ts          TEXT NOT NULL,
                    temp        REAL,
                    temp_min    REAL,
                    temp_max    REAL,
                    descripcion TEXT,
                    humidity    INTEGER,
                    wind_speed  REAL,
                    ss          TEXT,
                    PRIMARY KEY (ciudad, ts)
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
            st.execute("CREATE INDEX IF NOT EXISTS idx_phq_ciudad    ON predicthq_events(ciudad)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_phq_categoria ON predicthq_events(categoria)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_phq_fecha     ON predicthq_events(fecha_inicio)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_tm_ciudad     ON ticketmaster_events(ciudad)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_tm_fecha      ON ticketmaster_events(fecha)");
        } catch (SQLException e) {
            throw new IllegalStateException("Error al crear el schema: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Upserts
    // -----------------------------------------------------------------------

    public synchronized void upsertWeather(WeatherRecord r) {
        String sql = """
            INSERT OR REPLACE INTO weather_events
              (ciudad, ts, temp, temp_min, temp_max, descripcion, humidity, wind_speed, ss)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, r.ciudad());
            ps.setString(2, r.ts());
            ps.setDouble(3, r.temp());
            ps.setDouble(4, r.tempMin());
            ps.setDouble(5, r.tempMax());
            ps.setString(6, r.descripcion());
            ps.setInt(7,    r.humidity());
            ps.setDouble(8, r.windSpeed());
            ps.setString(9, r.ss());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DatamartDB] Error al guardar clima: " + e.getMessage());
        }
    }

    public synchronized void upsertPredictHQ(PredictHQRecord r) {
        String sql = """
            INSERT OR REPLACE INTO predicthq_events
              (id, ts, titulo, categoria, ciudad, fecha_inicio, fecha_fin,
               latitud, longitud, impacto, ss)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1,  r.id());
            ps.setString(2,  r.ts());
            ps.setString(3,  r.titulo());
            ps.setString(4,  r.categoria());
            ps.setString(5,  r.ciudad());
            ps.setString(6,  r.fechaInicio());
            ps.setString(7,  r.fechaFin());
            ps.setDouble(8,  r.latitud());
            ps.setDouble(9,  r.longitud());
            ps.setInt(10,    r.impacto());
            ps.setString(11, r.ss());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DatamartDB] Error al guardar evento PredictHQ: " + e.getMessage());
        }
    }

    public synchronized void upsertTicketmaster(TicketmasterRecord r) {
        String sql = """
            INSERT OR REPLACE INTO ticketmaster_events
              (id, ts, nombre, fecha, hora, ciudad, venue, categoria, url, ss)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1,  r.id());
            ps.setString(2,  r.ts());
            ps.setString(3,  r.nombre());
            ps.setString(4,  r.fecha());
            ps.setString(5,  r.hora());
            ps.setString(6,  r.ciudad());
            ps.setString(7,  r.venue());
            ps.setString(8,  r.categoria());
            ps.setString(9,  r.url());
            ps.setString(10, r.ss());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DatamartDB] Error al guardar evento Ticketmaster: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    public ResultSet findAllWeather() throws SQLException {
        return connection.createStatement()
                .executeQuery("SELECT * FROM weather_events ORDER BY ciudad");
    }

    public ResultSet findWeatherByCiudad(String ciudad) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM weather_events WHERE LOWER(ciudad) = LOWER(?)");
        ps.setString(1, ciudad);
        return ps.executeQuery();
    }

    public ResultSet findPredictHQByMinImpact(int minImpacto, String ciudad) throws SQLException {
        boolean filterByCiudad = ciudad != null;
        String sql = filterByCiudad
                ? "SELECT * FROM predicthq_events WHERE impacto >= ? AND LOWER(ciudad) = LOWER(?) ORDER BY impacto DESC LIMIT 100"
                : "SELECT * FROM predicthq_events WHERE impacto >= ? ORDER BY impacto DESC LIMIT 100";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setInt(1, minImpacto);
        if (filterByCiudad) ps.setString(2, ciudad);
        return ps.executeQuery();
    }

    public ResultSet findPredictHQByCategory(String categoria) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM predicthq_events WHERE LOWER(categoria) LIKE LOWER(?) ORDER BY fecha_inicio LIMIT 100");
        ps.setString(1, "%" + categoria + "%");
        return ps.executeQuery();
    }

    public ResultSet findAllPredictHQCategories() throws SQLException {
        return connection.createStatement().executeQuery(
                "SELECT categoria, COUNT(*) as total, AVG(impacto) as avg_impacto " +
                        "FROM predicthq_events GROUP BY categoria ORDER BY total DESC");
    }

    public ResultSet findTicketmaster(String ciudad, String fecha) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM ticketmaster_events WHERE 1=1");
        if (ciudad != null) sql.append(" AND LOWER(ciudad) = LOWER(?)");
        if (fecha  != null) sql.append(" AND fecha LIKE ?");
        sql.append(" ORDER BY fecha, hora LIMIT 100");

        PreparedStatement ps = connection.prepareStatement(sql.toString());
        int index = 1;
        if (ciudad != null) ps.setString(index++, ciudad);
        if (fecha  != null) ps.setString(index,   fecha + "%");
        return ps.executeQuery();
    }

    public ResultSet findDatamartSummary() throws SQLException {
        return connection.createStatement().executeQuery(
                "SELECT 'weather'      as tabla, COUNT(*) as total FROM weather_events      " +
                        "UNION ALL SELECT 'predicthq',   COUNT(*) FROM predicthq_events             " +
                        "UNION ALL SELECT 'ticketmaster', COUNT(*) FROM ticketmaster_events");
    }

    public ResultSet findTopCiudades(int limit) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
                "SELECT ciudad, COUNT(*) as total_eventos, AVG(impacto) as avg_impacto " +
                        "FROM predicthq_events WHERE ciudad IS NOT NULL AND ciudad != '' " +
                        "GROUP BY ciudad ORDER BY total_eventos DESC LIMIT ?");
        ps.setInt(1, limit);
        return ps.executeQuery();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Connection openConnection(String dbPath) {
        try {
            var dir = Paths.get(dbPath).getParent();
            if (dir != null) Files.createDirectories(dir);
            Connection conn = DriverManager.getConnection(JDBC_PREFIX + dbPath);
            conn.setAutoCommit(true);
            return conn;
        } catch (Exception e) {
            throw new IllegalStateException("No se puede abrir la base de datos: " + e.getMessage());
        }
    }
}