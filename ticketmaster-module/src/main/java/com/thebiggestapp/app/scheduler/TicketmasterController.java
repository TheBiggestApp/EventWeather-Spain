package com.thebiggestapp.app.scheduler;

import com.thebiggestapp.app.model.Evento;
import com.thebiggestapp.app.services.TicketmasterService;
import com.thebiggestapp.app.persistence.DatabaseManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public class TicketmasterController implements Runnable {
    private final TicketmasterService ticketmaster = new TicketmasterService();
    private final DatabaseManager database = new DatabaseManager();

    @Override
    public void run() {
        String[] ciudades = {"Barcelona", "Madrid", "Valencia", "Sevilla", "Zaragoza", "Murcia", "Palma de Mallorca", "Las Palmas", "Bilbao", "Alicante", "Cordoba", "Valladolid", "Vigo", "Gijon"};

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
        JsonArray events = ticketmaster.getEventsArray(ticketmaster.fetchEventsJson(city));

        for (JsonElement element : events) {
            processEvent(element.getAsJsonObject(), city);
        }
    }

    private void processEvent(com.google.gson.JsonObject json, String city) {
        try {
            Evento evento = ticketmaster.parseToEvento(json);
            database.guardar(evento, city);
            printSummary(evento, city);
        } catch (Exception e) {
            System.err.println("Evento omitido por error de datos.");
        }
    }

    private void printSummary(Evento e, String city) {
        System.out.printf("[%s] %s a las %s | %s%n",
                city, e.getFecha(), e.getHora(), e.getNombre());
    }
}