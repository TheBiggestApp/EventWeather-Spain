package com.thebiggestapp.app.store;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Escribe eventos en el sistema de ficheros siguiendo la estructura:
 *   eventstore/{topic}/{ss}/{YYYYMMDD}.events
 *
 * Cada línea del fichero es un evento JSON (formato JSON Lines / NDJSON).
 * Las escrituras son append-only y thread-safe por fichero.
 */
public class EventStore {

    private static final String BASE_DIR = "eventstore";
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    /**
     * Persiste un mensaje JSON recibido del broker.
     *
     * @param topic   nombre del topic de origen (p.ej. "Weather")
     * @param rawJson texto JSON del evento
     */
    public synchronized void store(String topic, String rawJson) {
        try {
            JsonObject event = JsonParser.parseString(rawJson).getAsJsonObject();

            // Extraer ss y ts del evento
            String ss = event.has("ss") ? event.get("ss").getAsString() : "unknown";
            String ts = event.has("ts") ? event.get("ts").getAsString() : Instant.now().toString();

            // Obtener fecha YYYYMMDD desde el ts
            String date = resolveDate(ts);

            // Construir ruta: eventstore/{topic}/{ss}/{YYYYMMDD}.events
            Path dir  = Paths.get(BASE_DIR, topic, ss);
            Files.createDirectories(dir);
            Path file = dir.resolve(date + ".events");

            // Append: una línea por evento
            Files.writeString(file, rawJson + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            System.out.printf("[EventStore] Guardado en %s%n", file);

        } catch (IOException e) {
            System.err.println("[EventStore] Error al escribir evento: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[EventStore] Evento malformado, ignorado: " + e.getMessage());
        }
    }

    /**
     * Intenta parsear el timestamp del evento para obtener la fecha YYYYMMDD.
     * Si el ts no es un Instant ISO-8601 válido, usa la fecha actual.
     */
    private String resolveDate(String ts) {
        try {
            Instant instant = Instant.parse(ts);
            return DATE_FMT.format(instant);
        } catch (Exception e) {
            // ts puede ser "2025-11-03" (solo fecha) sin hora; intentamos extraer YYYYMMDD directamente
            if (ts != null && ts.length() >= 10) {
                return ts.substring(0, 10).replace("-", "");
            }
            return DATE_FMT.format(Instant.now());
        }
    }
}
