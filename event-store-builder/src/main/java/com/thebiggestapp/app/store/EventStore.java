package com.thebiggestapp.app.store;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class EventStore {

    private static final String BASE_DIR = "eventstore";
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    public synchronized void store(String topic, String rawJson) {
        try {
            JsonObject event = JsonParser.parseString(rawJson).getAsJsonObject();

            String ss = event.has("ss") ? event.get("ss").getAsString() : "unknown";
            String ts = event.has("ts") ? event.get("ts").getAsString() : Instant.now().toString();

            String date = resolveDate(ts);

            Path dir  = Paths.get(BASE_DIR, topic, ss);
            Files.createDirectories(dir);
            Path file = dir.resolve(date + ".events");

            Files.writeString(file, rawJson + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            System.out.printf("[EventStore] Guardado en %s%n", file);

        } catch (IOException e) {
            System.err.println("[EventStore] Error al escribir evento: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[EventStore] Evento malformado, ignorado: " + e.getMessage());
        }
    }

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
