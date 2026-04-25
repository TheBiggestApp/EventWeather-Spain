package com.thebiggestapp.app.scheduler;

import com.thebiggestapp.app.model.EventoPHQ;
import com.thebiggestapp.app.services.PredictHQService;
import com.thebiggestapp.app.persistence.DatabaseManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public class PredictHQController implements Runnable {
    private final PredictHQService predictHQ = new PredictHQService();
    private final DatabaseManager database = new DatabaseManager();

    @Override
    public void run() {
        String[] ciudades = {"Barcelona", "Madrid", "Valencia", "Sevilla", "Zaragoza",
                "Murcia", "Palma de Mallorca", "Las Palmas", "Bilbao", "Alicante",
                "Cordoba", "Valladolid", "Vigo", "Gijon"};

        for (String ciudad : ciudades) {
            try {
                synchronizeCity(ciudad);
            } catch (Exception e) {
                System.err.println("Fallo en la sincronización de eventos de " + ciudad + ": " + e.getMessage());
            }
        }
    }

    private void synchronizeCity(String city) throws Exception {
        System.out.println("Buscando eventos en: " + city);
        String json = predictHQ.fetchEventsJson(city);
        JsonArray events = predictHQ.getEventsArray(json);
        System.out.println("  → " + events.size() + " eventos encontrados");

        for (JsonElement element : events) {
            processEvent(element.getAsJsonObject(), city);
        }
    }

    private void processEvent(com.google.gson.JsonObject json, String city) {
        try {
            EventoPHQ evento = predictHQ.parseToEventoPHQ(json);
            database.guardar(evento, city);
            printSummary(evento, city);
        } catch (Exception e) {
            // Mostrar el error real para facilitar el diagnóstico
            System.err.println("Evento omitido por error de datos: " + e.getMessage());
        }
    }

    private void printSummary(EventoPHQ e, String city) {
        System.out.printf("[%s] %s → %s | %s (impacto: %d)%n",
                city, e.getFechaInicio(), e.getFechaFin(), e.getTitulo(), e.getImpacto());
    }
}
