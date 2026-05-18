package com.thebiggestapp.app;

import com.thebiggestapp.app.scheduler.PredictHQController;

public class Main {
    public static void main(String[] args) {
        System.out.println("--- INICIANDO PREDICTHQ MODULE ---");
        new PredictHQController().start();
    }
}