package com.thebiggestapp.app.datamart;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Parsea eventos JSON crudos (de ActiveMQ o del event store) y los
 * persiste en el datamart según su campo "ss" (source/sender).
 *
 * Fuentes reconocidas:
 *   - openweather-feeder  → tabla weather_events
 *   - predicthq-feeder    → tabla predicthq_events
 *   - ticketmaster-feeder → tabla ticketmaster_events
 */
public class EventParser {

    private final DatamartDB db = DatamartDB.getInstance();

    public void process(String rawJson, String source) {
        try {
            JsonObject event = JsonParser.parseString(rawJson.trim()).getAsJsonObject();
            String ss = event.has("ss") ? event.get("ss").getAsString() : "";
            String ts = event.has("ts") ? event.get("ts").getAsString() : "";

            if (ss.contains("openweather") || (source != null && source.equalsIgnoreCase("Weather"))) {
                processWeather(event, ts, ss);
            } else if (ss.contains("predicthq") || (source != null && source.equalsIgnoreCase("PredictHQ"))) {
                processPredictHQ(event, ts, ss);
            } else if (ss.contains("ticketmaster") || (source != null && source.equalsIgnoreCase("Ticketmaster"))) {
                processTicketmaster(event, ts, ss);
            } else {
                System.err.println("[EventParser] ss desconocido: " + ss);
            }

        } catch (Exception e) {
            System.err.println("[EventParser] Error al procesar evento: " + e.getMessage());
        }
    }

    private void processWeather(JsonObject e, String ts, String ss) {
        String ciudad    = getString(e, "ciudad");
        double temp      = getDouble(e, "temp");
        double tempMin   = getDouble(e, "temp_min");
        double tempMax   = getDouble(e, "temp_max");
        String desc      = getString(e, "descripcion");
        int    humidity  = getInt(e, "humidity");
        double wind      = getDouble(e, "wind_speed");

        if (ciudad == null || ciudad.isBlank()) {
            if (e.has("payload")) {
                JsonObject p = e.getAsJsonObject("payload");
                ciudad   = getString(p, "ciudad");
                temp     = getDouble(p, "temp");
                tempMin  = getDouble(p, "temp_min");
                tempMax  = getDouble(p, "temp_max");
                desc     = getString(p, "descripcion");
                humidity = getInt(p, "humidity");
                wind     = getDouble(p, "wind_speed");
            }
        }

        if (ciudad == null || ciudad.isBlank()) return;

        db.upsertWeather(ciudad, ts, temp, tempMin, tempMax, desc, humidity, wind, ss);
    }

    private void processPredictHQ(JsonObject e, String ts, String ss) {
        String id          = getString(e, "id");
        String titulo      = getString(e, "titulo");
        String categoria   = getString(e, "categoria");
        String ciudad      = getString(e, "ciudad");
        String fechaInicio = getString(e, "fecha_inicio");
        String fechaFin    = getString(e, "fecha_fin");
        double lat         = getDouble(e, "latitud");
        double lon         = getDouble(e, "longitud");
        int    impacto     = getInt(e, "impacto");

        if (id == null || id.isBlank()) return;

        if (ciudad == null || ciudad.isBlank()) {
            ciudad = inferCiudad(lat, lon);
        }

        db.upsertPredictHQ(id, ts, titulo, categoria, ciudad,
                fechaInicio, fechaFin, lat, lon, impacto, ss);
    }

    private void processTicketmaster(JsonObject e, String ts, String ss) {
        String id        = getString(e, "id");
        String nombre    = getString(e, "nombre");
        String fecha     = getString(e, "fecha");
        String hora      = getString(e, "hora");
        String ciudad    = getString(e, "ciudad");
        String venue     = getString(e, "venue");
        String categoria = getString(e, "categoria");
        String url       = getString(e, "url");

        if (id == null || id.isBlank()) return;

        db.upsertTicketmaster(id, ts, nombre, fecha, hora, ciudad, venue, categoria, url, ss);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString() : null;
    }

    private double getDouble(JsonObject obj, String key) {
        try { return obj.has(key) ? obj.get(key).getAsDouble() : 0.0; }
        catch (Exception e) { return 0.0; }
    }

    private int getInt(JsonObject obj, String key) {
        try { return obj.has(key) ? obj.get(key).getAsInt() : 0; }
        catch (Exception e) { return 0; }
    }

    /**
     * Inferencia básica de ciudad española a partir de coordenadas (~55 km de margen).
     */
    private String inferCiudad(double lat, double lon) {
        double[][] coords = {
                {40.4168, -3.7038}, {41.3851, 2.1734},  {37.3891, -5.9845},
                {39.4699, -0.3763}, {43.2630, -2.9350},  {36.7213, -4.4213},
                {37.9922, -1.1307}, {41.6488, -0.8891},  {37.1773, -3.5986},
                {43.3623, -8.4115}
        };
        String[] nombres = {
                "Madrid", "Barcelona", "Sevilla", "Valencia", "Bilbao",
                "Málaga", "Murcia",    "Zaragoza","Granada",  "A Coruña"
        };

        double best = Double.MAX_VALUE;
        String result = null;
        for (int i = 0; i < coords.length; i++) {
            double d = Math.hypot(lat - coords[i][0], lon - coords[i][1]);
            if (d < best) { best = d; result = nombres[i]; }
        }
        return (best < 1.0) ? result : null;
    }
}