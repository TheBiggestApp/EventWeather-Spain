package com.thebiggestapp.app.servicies;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class HistoricalWeatherService {

    private static final String BASE_URL   = "https://archive-api.open-meteo.com/v1/archive";
    private static final int    YEARS_BACK = 5;

    private static final Map<String, double[]> CITY_COORDS = loadCityCoords();

    private final HttpClient   http   = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private static Map<String, double[]> loadCityCoords() {
        Map<String, double[]> map = new HashMap<>();
        try (InputStream is = HistoricalWeatherService.class
                .getClassLoader().getResourceAsStream("cities.properties")) {
            Properties props = new Properties();
            props.load(is);
            for (String name : props.stringPropertyNames()) {
                String[] parts = props.getProperty(name).split(",");
                double lat = Double.parseDouble(parts[0].trim());
                double lon = Double.parseDouble(parts[1].trim());
                map.put(name.replace("_", " ").toLowerCase(), new double[]{lat, lon});
            }
        } catch (Exception e) {
            System.err.println("[HistoricalWeatherService] Error cargando cities.properties: " + e.getMessage());
        }
        return map;
    }

    public static double[] getCoordsForCity(String ciudad) {
        return ciudad != null ? CITY_COORDS.get(ciudad.toLowerCase()) : null;
    }

    public Map<String, Object> getEstimacion(double lat, double lon, String fechaEvento) {
        Map<String, Object> result = new HashMap<>();
        result.put("weather_state",   "PREDICCION_HISTORICA");
        result.put("weather_warning", "Estimación basada en medias históricas de los últimos " + YEARS_BACK + " años. Precisión limitada.");

        try {
            LocalDate fecha = LocalDate.parse(fechaEvento.substring(0, 10));

            double sumTempMax = 0, sumTempMin = 0, sumPrecip = 0;
            int count = 0;

            for (int y = 1; y <= YEARS_BACK; y++) {
                LocalDate day = fecha.minusYears(y);
                if (day.getYear() < 1940) continue;

                String url = String.format(java.util.Locale.US,
                        "%s?latitude=%.4f&longitude=%.4f&start_date=%s&end_date=%s" +
                                "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum" +
                                "&timezone=Europe%%2FMadrid",
                        BASE_URL, lat, lon,
                        day.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        day.format(DateTimeFormatter.ISO_LOCAL_DATE)
                );

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) continue;

                JsonNode root      = mapper.readTree(res.body());
                JsonNode daily     = root.path("daily");
                JsonNode maxArr    = daily.path("temperature_2m_max");
                JsonNode minArr    = daily.path("temperature_2m_min");
                JsonNode precipArr = daily.path("precipitation_sum");

                if (maxArr.size() > 0 && !maxArr.get(0).isNull() && !minArr.get(0).isNull()) {
                    sumTempMax += maxArr.get(0).asDouble();
                    sumTempMin += minArr.get(0).asDouble();
                    if (!precipArr.get(0).isNull()) {
                        sumPrecip += precipArr.get(0).asDouble();
                    }
                    count++;
                }
            }

            if (count > 0) {
                double avgMax    = round1(sumTempMax / count);
                double avgMin    = round1(sumTempMin / count);
                double avgTemp   = round1((avgMax + avgMin) / 2.0);
                double avgPrecip = round1(sumPrecip / count);

                result.put("temperatura",         avgTemp);
                result.put("temp_min",            avgMin);
                result.put("temp_max",            avgMax);
                result.put("precip_media_mm",     avgPrecip);
                result.put("tiempo",              buildDescripcion(avgTemp, avgPrecip));
                result.put("muestras_historicas", count);
            } else {
                result.put("weather_warning", "No se pudieron obtener datos históricos para esta ubicación.");
            }

        } catch (Exception e) {
            System.err.println("[HistoricalWeatherService] Error: " + e.getMessage());
            e.printStackTrace();
            result.put("weather_warning", "Estimación histórica no disponible temporalmente.");
        }

        return result;
    }

    private String buildDescripcion(double temp, double precip) {
        String lluvia = precip > 5 ? "lluvia probable" : precip > 1 ? "posible lluvia ligera" : "cielo despejado";
        String calor;
        if      (temp >= 30) calor = "calor intenso";
        else if (temp >= 22) calor = "caluroso";
        else if (temp >= 15) calor = "templado";
        else if (temp >= 8)  calor = "fresco";
        else                 calor = "frío";
        return calor + ", " + lluvia;
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}