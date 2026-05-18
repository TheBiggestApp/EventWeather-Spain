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
    // Schema - ONE BIG TABLE (OBT)
    // -----------------------------------------------------------------------

    private void initSchema() {
        try (Statement st = connection.createStatement()) {
            // Creamos una única tabla maestra que agrupa todos los campos posibles
            st.execute("""
                CREATE TABLE IF NOT EXISTS unified_datamart (
                    id           TEXT PRIMARY KEY,
                    fuente       TEXT NOT NULL,  -- 'WEATHER', 'PREDICTHQ', o 'TICKETMASTER'
                    ts           TEXT NOT NULL,
                    ciudad       TEXT,
                    titulo       TEXT,           -- Sirve para el nombre del evento o la descripción del clima
                    categoria    TEXT,
                    fecha_inicio TEXT,           -- Fecha del evento o fecha del registro del clima
                    fecha_fin    TEXT,
                    latitud      REAL,
                    longitud     REAL,
                    temperatura  REAL,
                    temp_min     REAL,
                    temp_max     REAL,
                    wind_speed   REAL,
                    humidity     INTEGER,
                    impacto      INTEGER,
                    venue        TEXT,
                    url          TEXT,
                    ss           TEXT
                )
                """);

            // Índices para que las búsquedas en la tabla gigante sean rápidas
            st.execute("CREATE INDEX IF NOT EXISTS idx_uni_fuente ON unified_datamart(fuente)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_uni_ciudad ON unified_datamart(ciudad)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_uni_fecha  ON unified_datamart(fecha_inicio)");
        } catch (SQLException e) {
            throw new IllegalStateException("Error al crear el schema unificado: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Upserts hacia la tabla unificada
    // -----------------------------------------------------------------------

    public synchronized void upsertWeather(WeatherRecord r) {
        String sql = """
            INSERT OR REPLACE INTO unified_datamart
              (id, fuente, ts, ciudad, titulo, temperatura, temp_min, temp_max, humidity, wind_speed, ss)
            VALUES (?, 'WEATHER', ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, "W_" + r.ciudad() + "_" + r.ts()); // ID compuesto para clima
            ps.setString(2, r.ts());
            ps.setString(3, r.ciudad());
            ps.setString(4, r.descripcion()); // Usamos 'titulo' para la descripción del cielo
            ps.setDouble(5, r.temp());
            ps.setDouble(6, r.tempMin());
            ps.setDouble(7, r.tempMax());
            ps.setInt(8, r.humidity());
            ps.setDouble(9, r.windSpeed());
            ps.setString(10, r.ss());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DatamartDB] Error al guardar clima: " + e.getMessage());
        }
    }

    public synchronized void upsertPredictHQ(PredictHQRecord r) {
        String sql = """
            INSERT OR REPLACE INTO unified_datamart
              (id, fuente, ts, ciudad, titulo, categoria, fecha_inicio, fecha_fin, latitud, longitud, impacto, ss)
            VALUES (?, 'PREDICTHQ', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1,  r.id());
            ps.setString(2,  r.ts());
            ps.setString(3,  r.ciudad());
            ps.setString(4,  r.titulo());
            ps.setString(5,  r.categoria());
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
            INSERT OR REPLACE INTO unified_datamart
              (id, fuente, ts, ciudad, titulo, categoria, fecha_inicio, venue, url, ss)
            VALUES (?, 'TICKETMASTER', ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1,  r.id());
            ps.setString(2,  r.ts());
            ps.setString(3,  r.ciudad());
            ps.setString(4,  r.nombre()); // Usamos 'titulo' para el nombre del evento
            ps.setString(5,  r.categoria());
            ps.setString(6,  r.fecha() + " " + r.hora()); // Unificamos en fecha_inicio
            ps.setString(7,  r.venue());
            ps.setString(8,  r.url());
            ps.setString(9, r.ss());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DatamartDB] Error al guardar evento Ticketmaster: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Queries filtradas por la columna 'fuente'
    // -----------------------------------------------------------------------

    public ResultSet findAllWeather() throws SQLException {
        return connection.createStatement()
                .executeQuery("SELECT * FROM unified_datamart WHERE fuente = 'WEATHER' ORDER BY ciudad");
    }

    public ResultSet findWeatherByCiudad(String ciudad) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM unified_datamart WHERE fuente = 'WEATHER' AND LOWER(ciudad) = LOWER(?)");
        ps.setString(1, ciudad);
        return ps.executeQuery();
    }

    public ResultSet findPredictHQByMinImpact(int minImpacto, String ciudad) throws SQLException {
        boolean filterByCiudad = ciudad != null;
        String sql = filterByCiudad
                ? "SELECT * FROM unified_datamart WHERE fuente = 'PREDICTHQ' AND impacto >= ? AND LOWER(ciudad) = LOWER(?) ORDER BY impacto DESC LIMIT 100"
                : "SELECT * FROM unified_datamart WHERE fuente = 'PREDICTHQ' AND impacto >= ? ORDER BY impacto DESC LIMIT 100";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setInt(1, minImpacto);
        if (filterByCiudad) ps.setString(2, ciudad);
        return ps.executeQuery();
    }

    public ResultSet findPredictHQByCategory(String categoria) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM unified_datamart WHERE fuente = 'PREDICTHQ' AND LOWER(categoria) LIKE LOWER(?) ORDER BY fecha_inicio LIMIT 100");
        ps.setString(1, "%" + categoria + "%");
        return ps.executeQuery();
    }

    public ResultSet findAllPredictHQCategories() throws SQLException {
        return connection.createStatement().executeQuery(
                "SELECT categoria, COUNT(*) as total, AVG(impacto) as avg_impacto " +
                        "FROM unified_datamart WHERE fuente = 'PREDICTHQ' GROUP BY categoria ORDER BY total DESC");
    }

    public ResultSet findTicketmaster(String ciudad, String fecha) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM unified_datamart WHERE fuente = 'TICKETMASTER'");
        if (ciudad != null) sql.append(" AND LOWER(ciudad) = LOWER(?)");
        if (fecha  != null) sql.append(" AND fecha_inicio LIKE ?");
        sql.append(" ORDER BY fecha_inicio LIMIT 100");

        PreparedStatement ps = connection.prepareStatement(sql.toString());
        int index = 1;
        if (ciudad != null) ps.setString(index++, ciudad);
        if (fecha  != null) ps.setString(index,   fecha + "%");
        return ps.executeQuery();
    }

    public ResultSet findDatamartSummary() throws SQLException {
        return connection.createStatement().executeQuery(
                "SELECT fuente as tabla, COUNT(*) as total FROM unified_datamart GROUP BY fuente");
    }

    public ResultSet findTopCiudades(int limit) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
                "SELECT ciudad, COUNT(*) as total_eventos, AVG(impacto) as avg_impacto " +
                        "FROM unified_datamart WHERE fuente = 'PREDICTHQ' AND ciudad IS NOT NULL AND ciudad != '' " +
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

    public ResultSet findEventosConClima(String ciudad, String fecha) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT e.id, e.ciudad, e.titulo, e.categoria,
                   e.fecha_inicio, e.impacto, e.venue, e.url, e.fuente,
                   w.temperatura, w.temp_min, w.temp_max, w.titulo as tiempo
            FROM unified_datamart e
            LEFT JOIN unified_datamart w
                   ON LOWER(e.ciudad) = LOWER(w.ciudad)
                   AND DATE(e.fecha_inicio) = DATE(w.ts)
                   AND w.fuente = 'WEATHER'
            WHERE e.fuente != 'WEATHER'
            """);

        if (ciudad != null) sql.append(" AND LOWER(e.ciudad) = LOWER(?)");
        if (fecha  != null) sql.append(" AND DATE(e.fecha_inicio) = ?");
        sql.append(" ORDER BY e.fecha_inicio DESC LIMIT 200");

        PreparedStatement ps = connection.prepareStatement(sql.toString());
        int idx = 1;
        if (ciudad != null) ps.setString(idx++, ciudad);
        if (fecha  != null) ps.setString(idx,   fecha);
        return ps.executeQuery();
    }
}
