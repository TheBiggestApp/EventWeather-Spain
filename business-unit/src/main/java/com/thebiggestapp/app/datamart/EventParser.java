package com.thebiggestapp.app.datamart;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class EventParser {

    private static final String SOURCE_WEATHER      = "Weather";
    private static final String SOURCE_PREDICTHQ    = "PredictHQ";
    private static final String SOURCE_TICKETMASTER = "Ticketmaster";

    private final DatamartDB   datamart     = DatamartDB.getInstance();
    private final CityResolver cityResolver = new CityResolver();

    public void process(String rawJson, String sourceTopic) {
        try {
            JsonObject event     = JsonParser.parseString(rawJson.trim()).getAsJsonObject();
            String     senderTag = readString(event, "ss");
            routeEvent(event, senderTag, sourceTopic);
        } catch (Exception e) {
            System.err.println("[EventParser] Error al procesar evento: " + e.getMessage());
        }
    }

    private void routeEvent(JsonObject event, String senderTag, String sourceTopic) {
        if (isWeather(senderTag, sourceTopic)) {
            processWeather(event);
        } else if (isPredictHQ(senderTag, sourceTopic)) {
            processPredictHQ(event);
        } else if (isTicketmaster(senderTag, sourceTopic)) {
            processTicketmaster(event);
        } else {
            System.err.println("[EventParser] Fuente no reconocida — ss: " + senderTag);
        }
    }

    private void processWeather(JsonObject event) {
        JsonObject payload = resolvePayload(event);
        String ciudad = readString(payload, "ciudad");
        if (isBlank(ciudad)) return;

        datamart.upsertWeather(new WeatherRecord(
                ciudad,
                readString(event,   "ts"),
                readDouble(payload, "temp"),
                readDouble(payload, "temp_min"),
                readDouble(payload, "temp_max"),
                readString(payload, "descripcion"),
                readInt(payload,    "humedad"),      // era "humidity" — el JSON usa "humedad"
                readDouble(payload, "viento_ms"),    // era "wind_speed" — el JSON usa "viento_ms"
                readString(event,   "ss")
        ));
    }

    private void processPredictHQ(JsonObject event) {
        String id = readString(event, "id");
        if (isBlank(id)) return;

        double lat    = readDouble(event, "latitud");
        double lon    = readDouble(event, "longitud");
        String ciudad = readString(event, "ciudad");
        if (isBlank(ciudad)) ciudad = cityResolver.resolve(lat, lon);

        datamart.upsertPredictHQ(new PredictHQRecord(
                id,
                readString(event, "ts"),
                readString(event, "titulo"),
                readString(event, "categoria"),
                ciudad,
                readString(event, "fecha_inicio"),
                readString(event, "fecha_fin"),
                lat,
                lon,
                readInt(event,    "impacto"),
                readString(event, "ss")
        ));
    }

    private void processTicketmaster(JsonObject event) {
        String id = readString(event, "id");
        if (isBlank(id)) return;

        datamart.upsertTicketmaster(new TicketmasterRecord(
                id,
                readString(event, "ts"),
                readString(event, "nombre"),
                readString(event, "fecha"),
                readString(event, "hora"),
                readString(event, "ciudad"),
                readString(event, "venue"),
                readString(event, "categoria"),
                readString(event, "url"),
                readString(event, "ss")
        ));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private JsonObject resolvePayload(JsonObject event) {
        if (!isBlank(readString(event, "ciudad"))) return event;
        if (event.has("payload")) return event.getAsJsonObject("payload");
        return event;
    }

    private boolean isWeather(String senderTag, String sourceTopic) {
        return contains(senderTag, "openweather") || SOURCE_WEATHER.equalsIgnoreCase(sourceTopic);
    }

    private boolean isPredictHQ(String senderTag, String sourceTopic) {
        return contains(senderTag, "predicthq") || SOURCE_PREDICTHQ.equalsIgnoreCase(sourceTopic);
    }

    private boolean isTicketmaster(String senderTag, String sourceTopic) {
        return contains(senderTag, "ticketmaster") || SOURCE_TICKETMASTER.equalsIgnoreCase(sourceTopic);
    }

    private String readString(JsonObject obj, String key) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull())
                ? obj.get(key).getAsString() : null;
    }

    private double readDouble(JsonObject obj, String key) {
        try { return (obj != null && obj.has(key)) ? obj.get(key).getAsDouble() : 0.0; }
        catch (Exception e) { return 0.0; }
    }

    private int readInt(JsonObject obj, String key) {
        try { return (obj != null && obj.has(key)) ? obj.get(key).getAsInt() : 0; }
        catch (Exception e) { return 0; }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean contains(String text, String substring) {
        return text != null && text.contains(substring);
    }
}