package com.thebiggestapp.app.scheduler;

import com.thebiggestapp.app.model.Clima;
import com.thebiggestapp.app.services.OpenWeatherService;
import com.thebiggestapp.app.persistence.DatabaseManager;

public class WeatherController implements Runnable {
    private final OpenWeatherService weatherService = new OpenWeatherService();
    private final DatabaseManager database = new DatabaseManager();

    @Override
    public void run() {
        String[] ciudades = {"Barcelona", "Madrid", "Valencia", "Sevilla", "Zaragoza", "Murcia", "Palma de Mallorca", "Las Palmas", "Bilbao", "Alicante", "Cordoba", "Valladolid", "Vigo", "Gijon"};

        for (String ciudad : ciudades) {
            try {
                System.out.println("Buscando clima en: " + ciudad);
                Clima clima = weatherService.getClimaPorCiudad(ciudad);
                database.guardar(clima);
                System.out.printf("-> [%s] %.2f°C, %s%n", clima.getCiudad(), clima.getTemp(), clima.getDesc());
            } catch (Exception e) {
                System.err.println("Fallo al obtener clima de " + ciudad + ": " + e.getMessage());
            }
        }
    }
}