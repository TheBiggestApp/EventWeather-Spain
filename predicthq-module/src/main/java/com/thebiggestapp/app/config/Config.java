package com.thebiggestapp.app.config;

import java.io.InputStream;
import java.util.Properties;

public class Config {
    private static final Properties props = new Properties();

    static {
        try (InputStream input = Config.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.err.println("ERROR: No se encuentra config.properties en la carpeta resources del módulo.");
            } else {
                props.load(input);
            }
        } catch (Exception e) {
            System.err.println("ERROR al cargar config.properties: " + e.getMessage());
        }
    }

    public static String get(String key) {
        String value = props.getProperty(key);
        if (value == null) System.err.println("ADVERTENCIA: La clave [" + key + "] no existe en el config.");
        return value;
    }
}
