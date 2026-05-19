package com.thebiggestapp.app.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;


public class HistoricalWeatherService {

    private static final String BASE_URL = "https://archive-api.open-meteo.com/v1/archive";
    private static final int    YEARS_BACK = 5;
    private static final int    WINDOW_DAYS = 3; // días antes y después para suavizar

    private final HttpClient   http   = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();


    public Map<String, Object> getEstimacion(double lat, double lon, String fechaEvento) {
        Map<String, Object> result = new HashMap<>();
        result.put("weather_state",   "PREDICCION_HISTORICA");
        result.put("weather_warning", "Estimación basada en medias históricas de los últimos " + YEARS_BACK + " años. Precisión limitada.");

        try {
            LocalDate fecha = LocalDate.parse(fechaEvento.substring(0, 10));

            double sumTempMax = 0, sumTempMin = 0, sumPrecip = 0;
            int count = 0;

            for (int y = 1; y <= YEARS_BACK; y++) {
                LocalDate from = fecha.minusYears(y).minusDays(WINDOW_DAYS);
                LocalDate to   = fecha.minusYears(y).plusDays(WINDOW_DAYS);

                // No pedimos datos del futuro ni de antes de 1940
                if (from.getYear() < 1940) continue;

                String url = String.format(
                    "%s?latitude=%.4f&longitude=%.4f&start_date=%s&end_date=%s" +
                    "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum" +
                    "&timezone=Europe%%2FMadrid",
                    BASE_URL, lat, lon,
                    from.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    to.format(DateTimeFormatter.ISO_LOCAL_DATE)
                );

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) continue;

                JsonNode root  = mapper.readTree(res.body());
                JsonNode daily = root.path("daily");
                JsonNode maxArr    = daily.path("temperature_2m_max");
                JsonNode minArr    = daily.path("temperature_2m_min");
                JsonNode precipArr = daily.path("precipitation_sum");

                for (int i = 0; i < maxArr.size(); i++) {
                    if (!maxArr.get(i).isNull() && !minArr.get(i).isNull()) {
                        sumTempMax += maxArr.get(i).asDouble();
                        sumTempMin += minArr.get(i).asDouble();
                        if (!precipArr.get(i).isNull()) {
                            sumPrecip += precipArr.get(i).asDouble();
                        }
                        count++;
                    }
                }
            }

            if (count > 0) {
                double avgMax    = round1(sumTempMax / count);
                double avgMin    = round1(sumTempMin / count);
                double avgTemp   = round1((avgMax + avgMin) / 2.0);
                double avgPrecip = round1(sumPrecip / count);

                result.put("temperatura",  avgTemp);
                result.put("temp_min",     avgMin);
                result.put("temp_max",     avgMax);
                result.put("precip_media_mm", avgPrecip);
                result.put("tiempo",       buildDescripcion(avgTemp, avgPrecip));
                result.put("muestras_historicas", count);
            } else {
                result.put("weather_warning", "No se pudieron obtener datos históricos para esta ubicación.");
            }

        } catch (Exception e) {
            System.err.println("[HistoricalWeatherService] Error: " + e.getMessage());
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
