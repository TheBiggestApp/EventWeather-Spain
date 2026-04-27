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
        String createTableSql = "CREATE TABLE IF NOT EXISTS eventos (" +
                "id TEXT PRIMARY KEY, " +
                "nombre TEXT, " +
                "fecha TEXT, " +
                "hora TEXT, " +
                "latitud REAL, " +
                "longitud REAL, " +
                "ciudad TEXT)";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void guardar(Evento evento, String ciudad) {
        String sql = "INSERT OR REPLACE INTO eventos VALUES(?,?,?,?,?,?,?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, evento.getId());
            pstmt.setString(2, evento.getNombre());
            pstmt.setString(3, evento.getFecha());
            pstmt.setString(4, evento.getHora());
            pstmt.setString(7, ciudad);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al persistir evento en la base de datos: " + e.getMessage());
        }
    }
}