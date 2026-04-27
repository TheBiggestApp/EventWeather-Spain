package com.thebiggestapp.app;

import com.thebiggestapp.app.scheduler.WeatherController;

public class Main {
    public static void main(String[] args) {
        System.out.println("--- INICIANDO OPENWEATHER MODULE (Sprint 2) ---");
        WeatherController controller = new WeatherController();
        controller.start();
        // El scheduler mantiene el proceso vivo indefinidamente
    }
}
