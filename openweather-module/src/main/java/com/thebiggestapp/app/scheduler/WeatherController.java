package com.thebiggestapp.app.scheduler;

import com.google.gson.JsonObject;
import com.thebiggestapp.app.model.Clima;
import com.thebiggestapp.app.model.Event;
import com.thebiggestapp.app.publisher.ActiveMQPublisher;
import com.thebiggestapp.app.services.OpenWeatherService;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Feeder OpenWeather: captura datos meteorológicos periódicamente
 * y los publica como eventos JSON en el topic "Weather" de ActiveMQ.
 */
public class WeatherController {

    private static final String TOPIC      = "Weather";
    private static final String SOURCE_ID  = "openweather-feeder";
    // Frecuencia de captura: cada 6 horas (según Sprint 1)
    private static final long   PERIOD_H   = 6;

    private final OpenWeatherService weatherService = new OpenWeatherService();

    private static final String[] CIUDADES = {
        "Barcelona", "Madrid", "Valencia", "Sevilla", "Zaragoza",
        "Murcia", "Palma de Mallorca", "Las Palmas", "Bilbao", "Alicante",
        "Cordoba", "Valladolid", "Vigo", "Gijon"
    };

    public void start() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        // Ejecuta ahora y luego cada PERIOD_H horas
        scheduler.scheduleAtFixedRate(this::captureAndPublish, 0, PERIOD_H, TimeUnit.HOURS);
        System.out.println("[WeatherController] Iniciado. Publicando en topic '" + TOPIC + "' cada " + PERIOD_H + "h.");
    }

    /** Una pasada: captura todas las ciudades y publica un evento por ciudad. */
    private void captureAndPublish() {
        try (ActiveMQPublisher publisher = new ActiveMQPublisher(TOPIC)) {
            for (String ciudad : CIUDADES) {
                try {
                    Clima clima = weatherService.getClimaPorCiudad(ciudad);
                    Event event = buildEvent(clima);
                    publisher.publish(event);
                    System.out.printf("[WeatherController] -> [%s] %.1f°C, %s%n",
                            clima.getCiudad(), clima.getTemp(), clima.getDesc());
                } catch (Exception e) {
                    System.err.println("[WeatherController] Fallo en " + ciudad + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[WeatherController] Error de conexión con ActiveMQ: " + e.getMessage());
        }
    }

    /** Construye el evento con la estructura mínima: ts, ss + payload. */
    private Event buildEvent(Clima clima) {
        JsonObject payload = new JsonObject();
        payload.addProperty("ciudad",     clima.getCiudad());
        payload.addProperty("temp",       clima.getTemp());
        payload.addProperty("temp_min",   clima.getTempMin());
        payload.addProperty("temp_max",   clima.getTempMax());
        payload.addProperty("humedad",    clima.getHumidity());
        payload.addProperty("viento_ms",  clima.getWindSpeed());
        payload.addProperty("descripcion", clima.getDesc());

        return new Event(
                Instant.now().toString(),  // ts: timestamp UTC de captura
                SOURCE_ID,                 // ss: identificador del feeder
                payload
        );
    }
}
