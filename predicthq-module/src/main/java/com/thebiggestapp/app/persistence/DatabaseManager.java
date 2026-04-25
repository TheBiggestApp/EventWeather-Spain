package com.thebiggestapp.app.persistence;

import com.thebiggestapp.app.config.Config;
import com.thebiggestapp.app.model.EventoPHQ;
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
        String createTableSql = "CREATE TABLE IF NOT EXISTS eventos_phq (" +
                "id TEXT PRIMARY KEY, " +
                "titulo TEXT, " +
                "categoria TEXT, " +
                "fecha_inicio TEXT, " +
                "fecha_fin TEXT, " +
                "latitud REAL, " +
                "longitud REAL, " +
                "impacto INTEGER, " +
                "ciudad TEXT)";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void guardar(EventoPHQ evento, String ciudad) {
        String sql = "INSERT OR REPLACE INTO eventos_phq VALUES(?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, evento.getId());
            pstmt.setString(2, evento.getTitulo());
            pstmt.setString(3, evento.getCategoria());
            pstmt.setString(4, evento.getFechaInicio());
            pstmt.setString(5, evento.getFechaFin());
            pstmt.setDouble(6, evento.getLatitud());
            pstmt.setDouble(7, evento.getLongitud());
            pstmt.setInt(8, evento.getImpacto());
            pstmt.setString(9, ciudad);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al persistir evento en la base de datos: " + e.getMessage());
        }
    }
}
