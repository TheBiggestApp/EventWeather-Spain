package com.thebiggestapp.app.scheduler;

import com.google.gson.JsonObject;
import com.thebiggestapp.app.model.Clima;
import com.thebiggestapp.app.model.Event;
import com.thebiggestapp.app.publisher.ActiveMQPublisher;
import com.thebiggestapp.app.services.OpenWeatherService;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Feeder OpenWeather: captura datos meteorológicos periódicamente
 * y los publica como eventos JSON en el topic "Weather" de ActiveMQ.
 * Las ciudades se leen dinámicamente de cities.properties.
 */
public class WeatherController {

    private static final String TOPIC     = "Weather";
    private static final String SOURCE_ID = "openweather-feeder";
    private static final long   PERIOD_H  = 6;

    private final OpenWeatherService weatherService = new OpenWeatherService();
    private final List<String>       ciudades       = loadCiudades();

    public void start() {
        System.out.printf("[WeatherController] %d ciudades cargadas desde cities.properties%n", ciudades.size());
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::captureAndPublish, 0, PERIOD_H, TimeUnit.HOURS);
        System.out.println("[WeatherController] Iniciado. Publicando en topic '" + TOPIC + "' cada " + PERIOD_H + "h.");
    }

    /** Una pasada: captura todas las ciudades y publica un evento por ciudad. */
    private void captureAndPublish() {
        try (ActiveMQPublisher publisher = new ActiveMQPublisher(TOPIC)) {
            for (String ciudad : ciudades) {
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

    /**
     * Lee cities.properties y extrae los nombres de ciudad.
     * Formato esperado: NombreCiudad=lat,lon,radius  (las líneas con '#' se ignoran).
     * Los guiones bajos en el nombre se reemplazan por espacios para la query a OpenWeather.
     */
    private static List<String> loadCiudades() {
        List<String> result = new ArrayList<>();
        try (InputStream is = WeatherController.class
                .getClassLoader().getResourceAsStream("cities.properties")) {
            if (is == null) {
                System.err.println("[WeatherController] No se encontró cities.properties en el classpath.");
                return result;
            }
            Properties props = new Properties();
            props.load(is);
            for (String key : props.stringPropertyNames()) {
                // Reemplazamos guion bajo por espacio: "Palma_de_Mallorca" -> "Palma de Mallorca"
                result.add(key.replace("_", " "));
            }
            result.sort(String::compareTo);
        } catch (Exception e) {
            System.err.println("[WeatherController] Error al leer cities.properties: " + e.getMessage());
        }
        return result;
    }

    /** Construye el evento con la estructura mínima: ts, ss + payload. */
    private Event buildEvent(Clima clima) {
        JsonObject payload = new JsonObject();
        payload.addProperty("ciudad",      clima.getCiudad());
        payload.addProperty("temp",        clima.getTemp());
        payload.addProperty("temp_min",    clima.getTempMin());
        payload.addProperty("temp_max",    clima.getTempMax());
        payload.addProperty("humedad",     clima.getHumidity());
        payload.addProperty("viento_ms",   clima.getWindSpeed());
        payload.addProperty("descripcion", clima.getDesc());

        return new Event(
                Instant.now().toString(),
                SOURCE_ID,
                payload
        );
    }
}