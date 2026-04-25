package com.thebiggestapp.app;

import com.thebiggestapp.app.scheduler.PredictHQController;

public class Main {
    public static void main(String[] args) {
        System.out.println("--- INICIANDO PREDICTHQ MODULE ---");
        try {
            PredictHQController task = new PredictHQController();
            task.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("--- PROGRAMA FINALIZADO ---");
    }
}
