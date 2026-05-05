package com.thebiggestapp.app;

import com.thebiggestapp.app.scheduler.PredictHQController;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        System.out.println("--- INICIANDO PREDICTHQ MODULE (Sprint 2) ---");

        // Preparamos el lector de teclado
        Scanner scanner = new Scanner(System.in);
        System.out.println("¿Qué deseas hacer?");
        System.out.println("1. Escribe el nombre de una ciudad (ej. 'Madrid' o 'Las_Palmas') para buscar solo esa.");
        System.out.println("2. Escribe 'TODAS' para iniciar el modo automático de 24h para todas las ciudades.");
        System.out.print("> ");

        String respuesta = scanner.nextLine().trim();

        PredictHQController controller = new PredictHQController();

        if (respuesta.equalsIgnoreCase("TODAS")) {
            // Comportamiento original: todas las ciudades cada 24 horas
            controller.start();
        } else {
            // Nuevo comportamiento: solo la ciudad que has escrito
            controller.startSingleCity(respuesta);
        }

        scanner.close();
    }
}