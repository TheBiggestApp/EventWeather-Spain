package com.thebiggestapp.app.services;

import com.thebiggestapp.app.config.Config;
import com.thebiggestapp.app.model.Evento;
import com.google.gson.*;
import okhttp3.*;

public class TicketmasterService {
    private final OkHttpClient client = new OkHttpClient();
    private final String apiKey = Config.get("TICKETMASTER_KEY");

    public String fetchEventsJson(String city) throws Exception {
        String url = String.format("https://app.ticketmaster.com/discovery/v2/events.json?apikey=%s&city=%s&countryCode=ES", apiKey, city);
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    public JsonArray getEventsArray(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("_embedded")) return new JsonArray();
        return root.getAsJsonObject("_embedded").getAsJsonArray("events");
    }

    public Evento parseToEvento(JsonObject jsonObject) {
        String id = jsonObject.get("id").getAsString();
        String name = jsonObject.get("name").getAsString();

        JsonObject start = jsonObject.getAsJsonObject("dates").getAsJsonObject("start");
        String date = start.get("localDate").getAsString();

        String time = start.has("localTime") ? start.get("localTime").getAsString() : "00:00:00";

        JsonObject venue = jsonObject.getAsJsonObject("_embedded").getAsJsonArray("venues").get(0).getAsJsonObject();
        JsonObject location = venue.getAsJsonObject("location");
        double lat = location.get("latitude").getAsDouble();
        double lon = location.get("longitude").getAsDouble();

        Evento evento = new Evento(id, name, date, lat, lon);
        evento.setHora(time);
        return evento;
    }
}