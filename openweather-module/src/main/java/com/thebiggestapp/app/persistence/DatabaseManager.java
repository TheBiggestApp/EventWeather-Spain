package com.thebiggestapp.app.persistence;

import com.thebiggestapp.app.config.Config;
import com.thebiggestapp.app.model.Clima;
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
        // Creamos una tabla específica para el clima
        String createTableSql = "CREATE TABLE IF NOT EXISTS clima (" +
                "ciudad TEXT PRIMARY KEY, " +
                "temperatura REAL, " +
                "descripcion TEXT)";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void guardar(Clima clima) {
        String sql = "INSERT OR REPLACE INTO clima VALUES(?,?,?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, clima.getCiudad());
            pstmt.setDouble(2, clima.getTemp());
            pstmt.setString(3, clima.getDesc());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al persistir clima: " + e.getMessage());
        }
    }
}