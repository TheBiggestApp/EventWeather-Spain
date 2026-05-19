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
				ctx.json(ResultSetMapper.toList(rs));
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