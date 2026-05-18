package com.thebiggestapp.app.api;

import com.thebiggestapp.app.datamart.DatamartDB;
import io.javalin.Javalin;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RestApi {

	private final DatamartDB db = DatamartDB.getInstance();
	private Javalin app;

	/**
	 * Arranca el servidor de la API REST en el puerto 7070 y define las rutas.
	 */
	public void start() {
		// Leemos el puerto del config, si no existe usamos el 7070 por defecto
		int port = Integer.parseInt(com.thebiggestapp.app.config.Config.get("API_PORT", "7070"));

		app = Javalin.create().start(port);

		System.out.println("[RestApi] Servidor iniciado en http://localhost:" + port);

		// -------------------------------------------------------------------
		// RUTA RAÍZ — lista de endpoints disponibles
		// -------------------------------------------------------------------
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

		// -------------------------------------------------------------------
		// DEFINICIÓN DE LOS ENDPOINTS
		// -------------------------------------------------------------------

		// 1. Estado del datamart
		app.get("/api/status", ctx -> {
			try {
				ResultSet rs = db.findDatamartSummary();
				ctx.json(ResultSetMapper.toList(rs));
			} catch (SQLException e) {
				ctx.status(500).result("Error en Datamart: " + e.getMessage());
			}
		});

		// 2. Clima completo general
		app.get("/api/weather", ctx -> {
			try {
				ResultSet rs = db.findAllWeather();
				ctx.json(ResultSetMapper.toList(rs));
			} catch (SQLException e) {
				ctx.status(500).result("Error en Datamart: " + e.getMessage());
			}
		});

		// 3. Clima filtrado por una ciudad específica
		app.get("/api/weather/{ciudad}", ctx -> {
			try {
				String ciudad = ctx.pathParam("ciudad");
				ResultSet rs = db.findWeatherByCiudad(ciudad);
				ctx.json(ResultSetMapper.toList(rs));
			} catch (SQLException e) {
				ctx.status(500).result("Error en Datamart: " + e.getMessage());
			}
		});

		// 4. Eventos PredictHQ filtrados por impacto mínimo (acepta parámetro ?min=X y ?ciudad=Y)
		app.get("/api/events/impact", ctx -> {
			try {
				int minImpacto = ctx.queryParamAsClass("min", Integer.class).getOrDefault(0);
				String ciudad = ctx.queryParam("ciudad"); // Puede ser null y el método de tu DB ya lo controla
				ResultSet rs = db.findPredictHQByMinImpact(minImpacto, ciudad);
				ctx.json(ResultSetMapper.toList(rs));
			} catch (SQLException e) {
				ctx.status(500).result("Error en Datamart: " + e.getMessage());
			}
		});

		// 5. Categorías globales de PredictHQ y sus totales
		app.get("/api/events/categories", ctx -> {
			try {
				ResultSet rs = db.findAllPredictHQCategories();
				ctx.json(ResultSetMapper.toList(rs));
			} catch (SQLException e) {
				ctx.status(500).result("Error en Datamart: " + e.getMessage());
			}
		});

		// 6. Eventos PredictHQ filtrados por una categoría en específico
		app.get("/api/events/category/{cat}", ctx -> {
			try {
				String categoria = ctx.pathParam("cat");
				ResultSet rs = db.findPredictHQByCategory(categoria);
				ctx.json(ResultSetMapper.toList(rs));
			} catch (SQLException e) {
				ctx.status(500).result("Error en Datamart: " + e.getMessage());
			}
		});

		// 7. Eventos de entretenimiento (Ticketmaster - acepta ?ciudad=X y ?fecha=Y opcionales)
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

		// 8. Análisis: Ciudades con más actividad (acepta un ?limit=X, por defecto 5)
		app.get("/api/analysis/top-cities", ctx -> {
			try {
				int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(5);
				ResultSet rs = db.findTopCiudades(limit);
				ctx.json(ResultSetMapper.toList(rs));
			} catch (SQLException e) {
				ctx.status(500).result("Error en Datamart: " + e.getMessage());
			}
		});

		// 9. Análisis combinado: Clima + Eventos actuales para una ciudad concreta
		app.get("/api/analysis/weather-vs-events/{ciudad}", ctx -> {
			try {
				String ciudad = ctx.pathParam("ciudad");

				// Construimos una respuesta combinada personalizada en un Mapa de Java
				Map<String, List<Map<String, Object>>> combinedData = new HashMap<>();

				ResultSet weatherRs = db.findWeatherByCiudad(ciudad);
				combinedData.put("clima", ResultSetMapper.toList(weatherRs));

				ResultSet phqRs = db.findPredictHQByMinImpact(0, ciudad); // Todos los eventos de esa ciudad
				combinedData.put("eventos_predicthq", ResultSetMapper.toList(phqRs));

				ResultSet tmRs = db.findTicketmaster(ciudad, null);
				combinedData.put("eventos_ticketmaster", ResultSetMapper.toList(tmRs));

				ctx.json(combinedData);
			} catch (SQLException e) {
				ctx.status(500).result("Error al generar análisis combinado: " + e.getMessage());
			}
		});

		// 10. Eventos con clima unificado — JOIN por ciudad, clima más reciente disponible
		// Acepta ?ciudad=X (opcional) y ?fecha=YYYY-MM-DD (opcional)
		app.get("/api/analysis/events-with-weather", ctx -> {
			try {
				String ciudad = ctx.queryParam("ciudad");
				String fecha  = ctx.queryParam("fecha");
				ResultSet rs  = db.findEventosConClima(ciudad, fecha);
				ctx.json(ResultSetMapper.toList(rs));
			} catch (SQLException e) {
				ctx.status(500).result("Error al obtener eventos con clima: " + e.getMessage());
			}
		});
	}

	/**
	 * Detiene el servidor de la API (útil para cuando se cierre la app).
	 */
	public void stop() {
		if (app != null) {
			app.stop();
		}
	}
}