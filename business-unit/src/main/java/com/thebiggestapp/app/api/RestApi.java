package com.thebiggestapp.app.api;

import com.thebiggestapp.app.datamart.DatamartDB;
import com.thebiggestapp.app.services.EventWeatherState;
import com.thebiggestapp.app.services.HistoricalWeatherService;
import io.javalin.Javalin;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RestApi {

	private final DatamartDB db = DatamartDB.getInstance();
	private final HistoricalWeatherService historicalWeather = new HistoricalWeatherService();
	private Javalin app;

	public void start() {
		int port = Integer.parseInt(com.thebiggestapp.app.config.Config.get("API_PORT", "7070"));
		app = Javalin.create().start(port);
		System.out.println("[RestApi] Servidor iniciado en http://localhost:" + port);

		app.get("/", ctx -> ctx.json(Map.of(
				"status", "UP",
				"endpoints", List.of(
						"/api/status",
						"/api/weather",
						"/api/weather/{ciudad}",
						"/api/events/impact?min=X&ciudad=Y",
						"/api/events/categories",
						"/api/events/category/{cat}",
						"/api/events/entertainment?ciudad=X&fecha=Y",
						"/api/analysis/top-cities?limit=N",
						"/api/analysis/weather-vs-events/{ciudad}",
						"/api/analysis/events-with-weather?ciudad=X&fecha=Y"
				)
		)));

		app.get("/api/status", ctx -> {
			try {
				ResultSet rs = db.findDatamartSummary();
				ctx.json(ResultSetMapper.toList(rs));
			} catch (SQLException e) {
				ctx.status(500).result("Error en Datamart: " + e.getMessage());
			}
		});

		app.get("/api/weather", ctx -> {
			try {
				ResultSet rs = db.findAllWeather();
				ctx.json(ResultSetMapper.toList(rs));
			} catch (SQLException e) {
				ctx.status(500).result("Error en Datamart: " + e.getMessage());
			}
		});

		app.get("/api/weather/{ciudad}", ctx -> {
			try {
				String ciudad = ctx.pathParam("ciudad");
				ResultSet rs = db.findWeatherByCiudad(ciudad);
				ctx.json(ResultSetMapper.toList(rs));
			} catch (SQLException e) {
				ctx.status(500).result("Error en Datamart: " + e.getMessage());
			}
		});

		app.get("/api/events/impact", ctx -> {
			try {
				int minImpacto = ctx.queryParamAsClass("min", Integer.class).getOrDefault(0);
				String ciudad = ctx.queryParam("ciudad");
				ResultSet rs = db.findPredictHQByMinImpact(minImpacto, ciudad);
				ctx.json(ResultSetMapper.toList(rs));
			} catch (SQLException e) {
				ctx.status(500).result("Error en Datamart: " + e.getMessage());
			}
		});

		app.get("/api/events/categories", ctx -> {
			try {
				ResultSet rs = db.findAllPredictHQCategories();
				ctx.json(ResultSetMapper.toList(rs));
			} catch (SQLException e) {
				ctx.status(500).result("Error en Datamart: " + e.getMessage());
			}
		});

		app.get("/api/events/category/{cat}", ctx -> {
			try {
				String categoria = ctx.pathParam("cat");
				ResultSet rs = db.findPredictHQByCategory(categoria);
				ctx.json(ResultSetMapper.toList(rs));
			} catch (SQLException e) {
				ctx.status(500).result("Error en Datamart: " + e.getMessage());
			}
		});

		app.get("/api/events/entertainment", ctx -> {
			try {
				String ciudad = ctx.queryParam("ciudad");
				String fecha = ctx.queryParam("fecha");
				ResultSet rs = db.findTicketmaster(ciudad, fecha);
				ctx.json(ResultSetMapper.toList(rs));
			} catch (SQLException e) {
				ctx.status(500).result("Error en Datamart: " + e.getMessage());
			}
		});

		app.get("/api/analysis/top-cities", ctx -> {
			try {
				int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(5);
				ResultSet rs = db.findTopCiudades(limit);
				ctx.json(ResultSetMapper.toList(rs));
			} catch (SQLException e) {
				ctx.status(500).result("Error en Datamart: " + e.getMessage());
			}
		});

		app.get("/api/analysis/weather-vs-events/{ciudad}", ctx -> {
			try {
				String ciudad = ctx.pathParam("ciudad");
				Map<String, List<Map<String, Object>>> combinedData = new HashMap<>();

				ResultSet weatherRs = db.findWeatherByCiudad(ciudad);
				combinedData.put("clima", ResultSetMapper.toList(weatherRs));

				ResultSet phqRs = db.findPredictHQByMinImpact(0, ciudad);
				combinedData.put("eventos_predicthq", ResultSetMapper.toList(phqRs));

				ResultSet tmRs = db.findTicketmaster(ciudad, null);
				combinedData.put("eventos_ticketmaster", ResultSetMapper.toList(tmRs));

				ctx.json(combinedData);
			} catch (SQLException e) {
				ctx.status(500).result("Error al generar análisis combinado: " + e.getMessage());
			}
		});

		app.get("/api/analysis/events-with-weather", ctx -> {
			try {
				String ciudad = ctx.queryParam("ciudad");
				String fecha  = ctx.queryParam("fecha");
				ResultSet rs  = db.findEventosConClima(ciudad, fecha);
				List<Map<String, Object>> eventos = ResultSetMapper.toList(rs);

				for (Map<String, Object> evento : eventos) {
					String fechaInicio = (String) evento.get("fecha_inicio");
					String estado      = EventWeatherState.calcular(fechaInicio);
					evento.put("weather_state", estado);

					boolean sinDatosClima = evento.get("temperatura") == null;

					if (EventWeatherState.PREDICCION_HISTORICA.equals(estado)) {
						// Para eventos a más de 14 días: estimación histórica de Open-Meteo
						String fechaSolo = (fechaInicio != null && fechaInicio.length() >= 10)
								? fechaInicio.substring(0, 10) : null;
						double[] latLon = (ciudad != null && fechaSolo != null)
								? db.findLatLonForEvent(ciudad, fechaSolo) : null;

						if (latLon != null && latLon[0] != 0 && latLon[1] != 0) {
							Map<String, Object> hist = historicalWeather.getEstimacion(latLon[0], latLon[1], fechaInicio);
							// Solo sobreescribimos campos de clima, no los del evento
							hist.forEach((k, v) -> evento.putIfAbsent(k, v));
							// Sobreescribimos siempre el estado y el warning
							evento.put("weather_state",   hist.get("weather_state"));
							evento.put("weather_warning", hist.get("weather_warning"));
						} else {
							evento.put("weather_warning", "Estimación histórica no disponible: coordenadas desconocidas.");
						}

					} else if (EventWeatherState.TENDENCIA_GENERAL.equals(estado)) {
						evento.put("weather_warning", "Pronóstico estimado (5–14 días). Puede variar.");

					} else {
						// PRONOSTICO_CONFIRMADO: datos reales de OpenWeather, sin warning
						if (sinDatosClima) {
							evento.put("weather_warning", "Datos de clima aún no disponibles para esta fecha.");
						}
					}
				}

				ctx.json(eventos);
			} catch (SQLException e) {
				ctx.status(500).result("Error al obtener eventos con clima: " + e.getMessage());
			}
		});
	}

	public void stop() {
		if (app != null) {
			app.stop();
		}
	}
}