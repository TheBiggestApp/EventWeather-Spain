package com.thebiggestapp.app.scheduler;

import com.thebiggestapp.app.model.*;
import com.thebiggestapp.app.services.*;
import com.thebiggestapp.app.persistence.DatabaseManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public class SyncTask implements Runnable {
    private final TicketmasterService ticketmaster = new TicketmasterService();
    private final OpenWeatherService weather = new OpenWeatherService();
    private final DatabaseManager database = new DatabaseManager();

    @Override
    public void run() {
        try {
            synchronizeCity("Madrid");
        } catch (Exception e) {
            System.err.println("Fallo en la sincronización: " + e.getMessage());
        }
    }

    private void synchronizeCity(String city) throws Exception {
        System.out.println("Sincronizando ciudad: " + city);
        JsonArray events = ticketmaster.getEventsArray(ticketmaster.fetchEventsJson(city));

        for (JsonElement element : events) {
            processEvent(element.getAsJsonObject(), city);
        }
    }

    private void processEvent(com.google.gson.JsonObject json, String city) {
        try {
            Evento evento = ticketmaster.parseToEvento(json);
            enrichWithWeather(evento);
            database.guardar(evento, city);
            printSummary(evento, city);
        } catch (Exception e) {
            System.err.println("Evento omitido por error de datos.");
        }
    }

    private void enrichWithWeather(Evento evento) throws Exception {
        Clima clima = weather.getClima(evento.getLatitud(), evento.getLongitud());
        evento.setTemperatura(clima.getTemp());
        evento.setClimaDescripcion(clima.getDesc());
    }

    private void printSummary(Evento e, String city) {
        // Hemos añadido e.getHora() al formato
        System.out.printf("[%s] %s a las %s | %s -> %.2f°C, %s%n",
                city, e.getFecha(), e.getHora(), e.getNombre(), e.getTemperatura(), e.getClimaDescripcion());
    }
}