package com.thebiggestapp.app.scheduler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thebiggestapp.app.model.Event;
import com.thebiggestapp.app.model.EventoPHQ;
import com.thebiggestapp.app.publisher.ActiveMQPublisher;
import com.thebiggestapp.app.services.PredictHQService;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Feeder PredictHQ: captura eventos de todas las ciudades españolas
 * definidas en cities.properties cada 24h y publica en el topic "PredictHQ".
 */
public class PredictHQController {

    private static final String   TOPIC     = "PredictHQ";
    private static final String   SOURCE_ID = "predicthq-feeder";
    private static final long     PERIOD_H  = 24;

    private final PredictHQService predictHQ = new PredictHQService();

    public void start() {
        List<String> ciudades = predictHQ.getCities();
        System.out.println("[PredictHQController] " + ciudades.size() + " ciudades cargadas desde cities.properties.");

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::captureAndPublish, 0, PERIOD_H, TimeUnit.HOURS);
        System.out.println("[PredictHQController] Iniciado. Topic '" + TOPIC + "' cada " + PERIOD_H + "h.");
    }

    // NUEVO MÉTODO: Ejecuta una sola ciudad al instante (sin temporizador de 24h)
    public void startSingleCity(String ciudadElegida) {
        System.out.println("[PredictHQController] Iniciando búsqueda manual solo para: " + ciudadElegida);

        // Abrimos la conexión con ActiveMQ
        try (ActiveMQPublisher publisher = new ActiveMQPublisher(TOPIC)) {
            // Llamamos al método que ya tienes para procesar una sola ciudad
            synchronizeCity(ciudadElegida, publisher);
            System.out.println("[PredictHQController] Búsqueda finalizada.");
        } catch (Exception e) {
            System.err.println("[PredictHQController] Error al procesar la ciudad " + ciudadElegida + ": " + e.getMessage());
        }
    }

    private void captureAndPublish() {
        List<String> ciudades = predictHQ.getCities();
        try (ActiveMQPublisher publisher = new ActiveMQPublisher(TOPIC)) {
            for (String ciudad : ciudades) {
                try {
                    synchronizeCity(ciudad, publisher);
                } catch (Exception e) {
                    System.err.println("[PredictHQController] Fallo en " + ciudad + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[PredictHQController] Error de conexión con ActiveMQ: " + e.getMessage());
        }
    }

    private void synchronizeCity(String ciudad, ActiveMQPublisher publisher) throws Exception {
        String json = predictHQ.fetchEventsJson(ciudad);
        JsonArray events = predictHQ.getEventsArray(json);
        System.out.println("  → " + events.size() + " eventos en " + ciudad);

        for (JsonElement element : events) {
            try {
                EventoPHQ evento = predictHQ.parseToEventoPHQ(element.getAsJsonObject());
                Event event = buildEvent(evento, ciudad);
                publisher.publish(event);
                System.out.printf("[PredictHQController] -> [%s] %s | %s (impacto: %d)%n",
                        ciudad, evento.getFechaInicio(), evento.getTitulo(), evento.getImpacto());
            } catch (Exception e) {
                System.err.println("[PredictHQController] Evento omitido: " + e.getMessage());
            }
        }
    }

    private Event buildEvent(EventoPHQ evento, String ciudad) {
        JsonObject payload = new JsonObject();
        payload.addProperty("id",           evento.getId());
        payload.addProperty("titulo",       evento.getTitulo());
        payload.addProperty("categoria",    evento.getCategoria());
        payload.addProperty("ciudad",       ciudad);
        payload.addProperty("fecha_inicio", evento.getFechaInicio());
        payload.addProperty("fecha_fin",    evento.getFechaFin());
        payload.addProperty("latitud",      evento.getLatitud());
        payload.addProperty("longitud",     evento.getLongitud());
        payload.addProperty("impacto",      evento.getImpacto());

        return new Event(evento.getFechaInicio(), SOURCE_ID, payload);
    }
}
