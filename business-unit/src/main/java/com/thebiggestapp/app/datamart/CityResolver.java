package com.thebiggestapp.app.datamart;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class CityResolver {

    private static final String CITIES_FILE = "cities.properties";
    private static final double MAX_DISTANCE = 1.0;

    private final Map<String, double[]> cities = new HashMap<>();

    public CityResolver() {
        loadCities();
    }

    private void loadCities() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CITIES_FILE)) {
            Properties props = new Properties();
            props.load(input);
            for (String name : props.stringPropertyNames()) {
                String[] parts = props.getProperty(name).split(",");
                double lat = Double.parseDouble(parts[0].trim());
                double lon = Double.parseDouble(parts[1].trim());
                cities.put(name.replace("_", " "), new double[]{lat, lon});
            }
        } catch (Exception e) {
            System.err.println("[CityResolver] Error al cargar ciudades: " + e.getMessage());
        }
    }

    public String resolve(double lat, double lon) {
        return cities.entrySet().stream()
                .min((a, b) -> Double.compare(distance(lat, lon, a.getValue()),
                        distance(lat, lon, b.getValue())))
                .filter(entry -> distance(lat, lon, entry.getValue()) < MAX_DISTANCE)
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private double distance(double lat, double lon, double[] coords) {
        return Math.hypot(lat - coords[0], lon - coords[1]);
    }
}