package com.thebiggestapp.app;

import com.thebiggestapp.app.api.RestApi;
import com.thebiggestapp.app.broker.ActiveMQSubscriber;
import com.thebiggestapp.app.datamart.DatamartDB;
import com.thebiggestapp.app.store.EventStoreReader;

import java.sql.ResultSet;
import java.util.Scanner;

public class Main {
	public static void main(String[] args) {
		System.out.println("==================================================");
		System.out.println("   INICIANDO MÓDULO BUSINESS UNIT (CON CLI)       ");
		System.out.println("==================================================");

		// 1. Cargar el histórico
		EventStoreReader reader = new EventStoreReader();
		reader.loadAll();

		// 2. Arrancar la API REST
		RestApi api = new RestApi();
		api.start();

		// 3. Arrancar ActiveMQ en un hilo separado
		ActiveMQSubscriber subscriber = new ActiveMQSubscriber();
		Thread subscriberThread = new Thread(subscriber::startListening);
		subscriberThread.start();

		// Hook de apagado seguro
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			System.out.println("\n[Sistema] Apagando servicios de forma segura...");
			api.stop();
			subscriber.stopListening();
		}));

		// 4. Iniciar la CLI (bloquea el hilo principal para leer teclado)
		iniciarCLI();
	}

	private static void iniciarCLI() {
		Scanner scanner = new Scanner(System.in);
		DatamartDB db = DatamartDB.getInstance();

		System.out.println("\n--------------------------------------------------");
		System.out.println("  CLI INICIADA - Escribe 'help' para ver comandos");
		System.out.println("--------------------------------------------------");

		while (true) {
			System.out.print("business-unit> ");
			String input = scanner.nextLine().trim();
			String[] parts = input.split(" ", 2);
			String comando = parts[0].toLowerCase();
			String argumento = parts.length > 1 ? parts[1] : null;

			try {
				switch (comando) {
					case "help":
						System.out.println("  Comandos disponibles:");
						System.out.println("  - status         : Muestra el estado del datamart (totales).");
						System.out.println("  - top            : Muestra las 5 ciudades con más eventos.");
						System.out.println("  - weather <city> : Muestra el clima de una ciudad. Ej: weather Madrid");
						System.out.println("  - clear          : Limpia la consola.");
						System.out.println("  - exit           : Apaga el programa de forma segura.");
						break;

					case "status":
						ResultSet rsStatus = db.findDatamartSummary();
						System.out.println("  --- ESTADO DEL DATAMART ---");
						while (rsStatus.next()) {
							System.out.println("  " + rsStatus.getString("tabla") + ": " + rsStatus.getInt("total") + " registros");
						}
						break;

					case "top":
						ResultSet rsTop = db.findTopCiudades(5);
						System.out.println("  --- TOP 5 CIUDADES (Eventos) ---");
						int i = 1;
						while (rsTop.next()) {
							System.out.println("  " + i + ". " + rsTop.getString("ciudad") + " - " + rsTop.getInt("total_eventos") + " eventos");
							i++;
						}
						break;

					case "weather":
						if (argumento == null) {
							System.out.println("  Error: Debes indicar una ciudad. Ej: weather Madrid");
						} else {
							ResultSet rsW = db.findWeatherByCiudad(argumento);
							boolean encontrado = false;
							while (rsW.next()) {
								encontrado = true;
								System.out.println("  Clima en " + rsW.getString("ciudad") + ": " +
										rsW.getDouble("temperatura") + "ºC (" + rsW.getString("titulo") + ")");
							}
							if (!encontrado) System.out.println("  No hay datos de clima para " + argumento);
						}
						break;

					case "clear":
						for (int c = 0; c < 50; ++c) System.out.println();
						break;

					case "exit":
					case "quit":
						System.out.println("  Saliendo de la CLI y apagando el sistema...");
						scanner.close();
						System.exit(0); // Ejecuta el Shutdown Hook automáticamente
						return;

					case "":
						break; // Si pulsas enter sin texto, no hace nada

					default:
						System.out.println("  Comando '" + comando + "' no reconocido. Escribe 'help'.");
				}
			} catch (Exception e) {
				System.err.println("  [Error CLI] " + e.getMessage());
			}
		}
	}
}