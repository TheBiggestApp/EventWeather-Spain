package com.thebiggestapp.app.scheduler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thebiggestapp.app.model.Event;
import com.thebiggestapp.app.model.Evento;
import com.thebiggestapp.app.publisher.ActiveMQPublisher;
import com.thebiggestapp.app.services.TicketmasterService;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Feeder Ticketmaster: captura eventos cada 24h y publica en el topic "Ticketmaster".
 */
public class TicketmasterController {

    private static final String   TOPIC     = "Ticketmaster";
    private static final String   SOURCE_ID = "ticketmaster-feeder";
    private static final long     PERIOD_H  = 24;

    private final TicketmasterService ticketmaster = new TicketmasterService();

    private static final String[] CIUDADES = {
        "Barcelona", "Madrid", "Valencia", "Sevilla", "Zaragoza",
        "Murcia", "Palma de Mallorca", "Las Palmas", "Bilbao", "Alicante",
        "Cordoba", "Valladolid", "Vigo", "Gijon"
    };

    public void start() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::captureAndPublish, 0, PERIOD_H, TimeUnit.HOURS);
        System.out.println("[TicketmasterController] Iniciado. Topic '" + TOPIC + "' cada " + PERIOD_H + "h.");
    }

    private void captureAndPublish() {
        try (ActiveMQPublisher publisher = new ActiveMQPublisher(TOPIC)) {
            for (String ciudad : CIUDADES) {
                try {
                    synchronizeCity(ciudad, publisher);
                } catch (Exception e) {
                    System.err.println("[TicketmasterController] Fallo en " + ciudad + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[TicketmasterController] Error de conexión con ActiveMQ: " + e.getMessage());
        }
    }

    private void synchronizeCity(String ciudad, ActiveMQPublisher publisher) throws Exception {
        System.out.println("[TicketmasterController] Buscando eventos en: " + ciudad);
        JsonArray events = ticketmaster.getEventsArray(ticketmaster.fetchEventsJson(ciudad));

        for (JsonElement element : events) {
            try {
                Evento evento = ticketmaster.parseToEvento(element.getAsJsonObject());
                Event event   = buildEvent(evento, ciudad);
                publisher.publish(event);
                System.out.printf("[TicketmasterController] -> [%s] %s a las %s | %s%n",
                        ciudad, evento.getFecha(), evento.getHora(), evento.getNombre());
            } catch (Exception e) {
                System.err.println("[TicketmasterController] Evento omitido: " + e.getMessage());
            }
        }
    }

    private Event buildEvent(Evento evento, String ciudad) {
        JsonObject payload = new JsonObject();
        payload.addProperty("id",      evento.getId());
        payload.addProperty("nombre",  evento.getNombre());
        payload.addProperty("ciudad",  ciudad);
        payload.addProperty("fecha",   evento.getFecha());
        payload.addProperty("hora",    evento.getHora());

        String ts = evento.getFecha() + "T" + evento.getHora() + "Z";
        return new Event(ts, SOURCE_ID, payload);
    }
}
