package com.thebiggestapp.app.store;

import com.thebiggestapp.app.config.Config;
import com.thebiggestapp.app.datamart.EventParser;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

public class EventStoreReader {

    private static final String KEY_EVENT_STORE_PATH     = "event.store.path";
    private static final String DEFAULT_EVENT_STORE_PATH = "../event-store-builder/eventstore";
    private static final String EVENTS_EXTENSION         = ".events";

    private final Path        eventStorePath;
    private final EventParser eventParser;

    public EventStoreReader() {
        eventStorePath = Paths.get(Config.get(KEY_EVENT_STORE_PATH, DEFAULT_EVENT_STORE_PATH));
        eventParser    = new EventParser();
    }

    public void loadAll() {
        if (!Files.exists(eventStorePath)) {
            System.out.println("[EventStoreReader] Event store no encontrado en: "
                    + eventStorePath.toAbsolutePath());
            return;
        }

        System.out.println("[EventStoreReader] Cargando histórico desde: "
                + eventStorePath.toAbsolutePath());

        int total = loadEventsFromStore();

        System.out.printf("[EventStoreReader] Carga completada: %d eventos procesados%n", total);
    }

    private int loadEventsFromStore() {
        try (Stream<Path> files = Files.walk(eventStorePath)) {
            return files.filter(this::isEventsFile)
                    .sorted()
                    .mapToInt(this::loadFile)
                    .sum();
        } catch (IOException e) {
            System.err.println("[EventStoreReader] Error al recorrer el event store: "
                    + e.getMessage());
            return 0;
        }
    }

    private int loadFile(Path file) {
        List<String> lines = readLines(file);
        String topic = extractTopic(file);

        lines.stream()
                .filter(line -> !line.isBlank())
                .forEach(line -> eventParser.process(line, topic));

        System.out.printf("[EventStoreReader] %s → %d eventos%n", file.getFileName(), lines.size());
        return lines.size();
    }

    private List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            System.err.println("[EventStoreReader] Error al leer " + file + ": " + e.getMessage());
            return List.of();
        }
    }

    private boolean isEventsFile(Path path) {
        return path.toString().endsWith(EVENTS_EXTENSION);
    }

    private String extractTopic(Path file) {
        try {
            Path parent = file.getParent();
            if (parent != null && parent.getParent() != null) {
                return parent.getParent().getFileName().toString();
            }
        } catch (Exception ignored) {}
        return null;
    }
}