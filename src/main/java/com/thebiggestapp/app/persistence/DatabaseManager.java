package com.thebiggestapp.app.persistence;

import com.thebiggestapp.app.config.Config;
import com.thebiggestapp.app.model.Evento;
import java.sql.*;

public class DatabaseManager {
    private Connection connection;

    public DatabaseManager() {
        initConnection();
        createTableAndMigrate();
    }

    private void initConnection() {
        try {
            connection = DriverManager.getConnection(Config.get("DB_URL"));
        } catch (SQLException e) {
            throw new RuntimeException("Error crítico: No se pudo conectar a la base de datos", e);
        }
    }

    private void createTableAndMigrate() {
        // SQL con la nueva columna 'hora'
        String createTableSql = "CREATE TABLE IF NOT EXISTS eventos (" +
                "id TEXT PRIMARY KEY, " +
                "nombre TEXT, " +
                "fecha TEXT, " +
                "hora TEXT, " +
                "latitud REAL, " +
                "longitud REAL, " +
                "temperatura REAL, " +
                "clima TEXT, " +
                "ciudad TEXT)";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSql);

            try {
                stmt.execute("ALTER TABLE eventos ADD COLUMN hora TEXT");
            } catch (SQLException e) {
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void guardar(Evento evento, String ciudad) {
        String sql = "INSERT OR REPLACE INTO eventos VALUES(?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, evento.getId());
            pstmt.setString(2, evento.getNombre());
            pstmt.setString(3, evento.getFecha());
            pstmt.setString(4, evento.getHora());
            pstmt.setDouble(5, evento.getLatitud());
            pstmt.setDouble(6, evento.getLongitud());
            pstmt.setDouble(7, evento.getTemperatura());
            pstmt.setString(8, evento.getClimaDescripcion());
            pstmt.setString(9, ciudad);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al persistir evento en la base de datos: " + e.getMessage());
        }
    }
}