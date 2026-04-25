package com.thebiggestapp.app.services;

import com.thebiggestapp.app.config.Config;
import com.thebiggestapp.app.model.EventoPHQ;
import com.google.gson.*;
import okhttp3.*;

public class PredictHQService {
    private static final String BASE_URL = "https://api.predicthq.com/v1/events/";
    private final OkHttpClient client = new OkHttpClient();
    private final String token = Config.get("PREDICTHQ_TOKEN");

    public String fetchEventsJson(String city) throws Exception {
        HttpUrl url = HttpUrl.parse(BASE_URL).newBuilder()
                .addQueryParameter("country", "ES")
                .addQueryParameter("q", city)
                .addQueryParameter("sort", "-rank")
                .addQueryParameter("limit", "50")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Accept", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Error en la API de PredictHQ: HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    public JsonArray getEventsArray(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("results")) return new JsonArray();
        return root.getAsJsonArray("results");
    }

    /**
     * Convierte un objeto JSON de PredictHQ en un EventoPHQ.
     * Usa "geo.geometry.coordinates" (preferido) o "location" como fallback.
     * Ambos siguen GeoJSON: [longitud, latitud].
     */
    public EventoPHQ parseToEventoPHQ(JsonObject jsonObject) {
        String id        = jsonObject.get("id").getAsString();
        String titulo    = jsonObject.get("title").getAsString();
        String categoria = jsonObject.has("category") ? jsonObject.get("category").getAsString() : "unknown";

        // Preferir hora local; fallback a UTC
        String fechaInicio = jsonObject.has("start_local") && !jsonObject.get("start_local").isJsonNull()
                ? jsonObject.get("start_local").getAsString()
                : jsonObject.get("start").getAsString();
        String fechaFin = jsonObject.has("end_local") && !jsonObject.get("end_local").isJsonNull()
                ? jsonObject.get("end_local").getAsString()
                : (jsonObject.has("end") && !jsonObject.get("end").isJsonNull()
                        ? jsonObject.get("end").getAsString()
                        : fechaInicio);

        int impacto = jsonObject.has("rank") ? jsonObject.get("rank").getAsInt() : 0;

        // Coordenadas: preferir "geo.geometry.coordinates", fallback a "location"
        double latitud  = 0.0;
        double longitud = 0.0;
        if (jsonObject.has("geo") && !jsonObject.get("geo").isJsonNull()) {
            JsonObject geo = jsonObject.getAsJsonObject("geo");
            if (geo.has("geometry") && !geo.get("geometry").isJsonNull()) {
                JsonObject geometry = geo.getAsJsonObject("geometry");
                if (geometry.has("coordinates")) {
                    JsonArray coords = geometry.getAsJsonArray("coordinates");
                    longitud = coords.get(0).getAsDouble();
                    latitud  = coords.get(1).getAsDouble();
                }
            }
        } else if (jsonObject.has("location") && !jsonObject.get("location").isJsonNull()) {
            JsonArray loc = jsonObject.getAsJsonArray("location");
            longitud = loc.get(0).getAsDouble();
            latitud  = loc.get(1).getAsDouble();
        }

        return new EventoPHQ(id, titulo, categoria, fechaInicio, fechaFin, latitud, longitud, impacto);
    }
}
