package com.thebiggestapp.app;

import com.thebiggestapp.app.api.RestApi;
import com.thebiggestapp.app.broker.ActiveMQSubscriber;
import com.thebiggestapp.app.store.EventStoreReader;

public class Main {
	public static void main(String[] args) {
		System.out.println("==================================================");
		System.out.println("   INICIANDO MÓDULO BUSINESS UNIT (COMMIT 7)      ");
		System.out.println("==================================================");

		System.out.println("\n[Main] 1. Cargando eventos históricos...");
		EventStoreReader reader = new EventStoreReader();
		reader.loadAll();

		System.out.println("\n[Main] 2. Iniciando API REST...");
		RestApi api = new RestApi();
		api.start();

		System.out.println("\n[Main] 3. Conectando al broker ActiveMQ en tiempo real...");
		Thread subscriberThread = new Thread(() -> {
			ActiveMQSubscriber subscriber = new ActiveMQSubscriber();
			subscriber.startListening();
		});
		subscriberThread.start();

		System.out.println("\n[Main] -> Sistema listo y esperando peticiones HTTP en http://localhost:7070");
	}
}