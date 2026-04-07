package com.thebiggestapp.app.config;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class Config {
    private static final Properties props = new Properties();

    static {
        // Usamos un File para ver exactamente dónde está buscando
        File file = new File("thebiggestapp/src/main/resources/config.properties");
        try (FileInputStream f = new FileInputStream(file)) {
            props.load(f);
        } catch (Exception e) {
            System.err.println("ERROR: No se encuentra el archivo en: " + file.getAbsolutePath());
        }
    }

    public static String get(String key) {
        String value = props.getProperty(key);
        if (value == null) System.err.println("ADVERTENCIA: La clave [" + key + "] no existe en el config.");
        return value;
    }
}