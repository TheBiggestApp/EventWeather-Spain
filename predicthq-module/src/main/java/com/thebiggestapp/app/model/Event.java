package com.thebiggestapp.app.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Representa un evento publicado en el broker ActiveMQ.
 * Estructura mínima requerida por el Sprint 2:
 *   - ts: timestamp UTC del dato
 *   - ss: identificador de la fuente/módulo
 *   - payload: atributos específicos de la fuente
 */
public class Event {

    private final String ts;   // timestamp UTC (ISO-8601)
    private final String ss;   // source/sender identifier
    private final JsonObject payload;

    public Event(String ts, String ss, JsonObject payload) {
        this.ts = ts;
        this.ss = ss;
        this.payload = payload;
    }

    public String getTs()           { return ts; }
    public String getSs()           { return ss; }
    public JsonObject getPayload()  { return payload; }

    /** Serializa el evento completo a JSON (una sola línea). */
    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("ts", ts);
        root.addProperty("ss", ss);
        // añade todos los campos del payload al nivel raíz
        payload.entrySet().forEach(e -> root.add(e.getKey(), e.getValue()));
        return new Gson().toJson(root);
    }

    @Override
    public String toString() {
        return toJson();
    }
}