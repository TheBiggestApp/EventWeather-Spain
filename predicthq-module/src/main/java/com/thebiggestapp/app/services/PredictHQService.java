package com.thebiggestapp.app.services;

import com.thebiggestapp.app.config.Config;
import com.thebiggestapp.app.model.EventoPHQ;
import com.google.gson.*;
import okhttp3.*;

import java.io.InputStream;
import java.util.*;

public class PredictHQService {
    private static final String BASE_URL = "https://api.predicthq.com/v1/events/";
    private final OkHttpClient client = new OkHttpClient();
    private final String token = Config.get("PREDICTHQ_TOKEN");

    // Mapa ordenado: nombre ciudad -> [latitud, longitud, radio_km]
    private final Map<String, double[]> cityCoords = new LinkedHashMap<>();

    public PredictHQService() {
        loadCityCoords();
    }

    /** Carga coordenadas desde cities.properties */
    private void loadCityCoords() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("cities.properties")) {
            if (input == null) {
                System.err.println("[PredictHQService] No se encuentra cities.properties");
                return;
            }
            // Leer línea a línea para respetar el orden y saltar comentarios
            String content = new String(input.readAllBytes());
            for (String line : content.split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] kv = line.split("=", 2);
                if (kv.length != 2) continue;
                String city = kv[0].trim();
                String[] parts = kv[1].trim().split(",");
                if (parts.length < 3) continue;
                double lat    = Double.parseDouble(parts[0].trim());
                double lon    = Double.parseDouble(parts[1].trim());
                double radius = Double.parseDouble(parts[2].trim());
                cityCoords.put(city, new double[]{lat, lon, radius});
            }
            System.out.println("[PredictHQService] Coordenadas cargadas para " + cityCoords.size() + " ciudades.");
        } catch (Exception e) {
            System.err.println("[PredictHQService] Error cargando cities.properties: " + e.getMessage());
        }
    }

    /** Devuelve la lista de ciudades cargadas desde cities.properties */
    public List<String> getCities() {
        return new ArrayList<>(cityCoords.keySet());
    }

    /**
     * Busca eventos por radio geográfico: within=30km@lat,lon
     * Evita resultados fuera de la ciudad buscada.
     */
    public String fetchEventsJson(String city) throws Exception {
        double[] coords = cityCoords.get(city);

        HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL).newBuilder()
                .addQueryParameter("country", "ES")
                .addQueryParameter("sort", "-rank")
                .addQueryParameter("limit", "50");

        if (coords != null) {
            String within = (int) coords[2] + "km@" + coords[0] + "," + coords[1];
            urlBuilder.addQueryParameter("within", within);
            System.out.println("[PredictHQService] Buscando en " + city + " | within=" + within);
        } else {
            System.out.println("[PredictHQService] Sin coordenadas para " + city + ", usando nombre.");
            urlBuilder.addQueryParameter("q", city);
        }

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Accept", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful())
                throw new RuntimeException("Error en la API de PredictHQ: HTTP " + response.code());
            return response.body().string();
        }
    }

    public JsonArray getEventsArray(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("results")) return new JsonArray();
        return root.getAsJsonArray("results");
    }

    public EventoPHQ parseToEventoPHQ(JsonObject jsonObject) {
        String id        = jsonObject.get("id").getAsString();
        String titulo    = jsonObject.get("title").getAsString();
        String categoria = jsonObject.has("category") ? jsonObject.get("category").getAsString() : "unknown";

        String fechaInicio = jsonObject.has("start_local") && !jsonObject.get("start_local").isJsonNull()
                ? jsonObject.get("start_local").getAsString()
                : jsonObject.get("start").getAsString();
        String fechaFin = jsonObject.has("end_local") && !jsonObject.get("end_local").isJsonNull()
                ? jsonObject.get("end_local").getAsString()
                : (jsonObject.has("end") && !jsonObject.get("end").isJsonNull()
                        ? jsonObject.get("end").getAsString() : fechaInicio);

        int impacto = jsonObject.has("rank") ? jsonObject.get("rank").getAsInt() : 0;

        double latitud = 0.0, longitud = 0.0;

        // Primero intentamos usar "location", que SIEMPRE es un array simple de [longitud, latitud]
        if (jsonObject.has("location") && !jsonObject.get("location").isJsonNull()) {
            JsonArray loc = jsonObject.getAsJsonArray("location");
            longitud = loc.get(0).getAsDouble();
            latitud  = loc.get(1).getAsDouble();
        }
        // Si no hay location, intentamos con "geo" pero asegurándonos de que sea un "Point"
        else if (jsonObject.has("geo") && !jsonObject.get("geo").isJsonNull()) {
            JsonObject geo = jsonObject.getAsJsonObject("geo");
            if (geo.has("geometry") && !geo.get("geometry").isJsonNull()) {
                JsonObject geometry = geo.getAsJsonObject("geometry");

                // Solo extraemos si el tipo de geometría es un punto simple
                if (geometry.has("type") && "Point".equals(geometry.get("type").getAsString())) {
                    JsonArray coords = geometry.getAsJsonArray("coordinates");
                    longitud = coords.get(0).getAsDouble();
                    latitud  = coords.get(1).getAsDouble();
                }
            }
        }
        return new EventoPHQ(id, titulo, categoria, fechaInicio, fechaFin, latitud, longitud, impacto);
    }
}
