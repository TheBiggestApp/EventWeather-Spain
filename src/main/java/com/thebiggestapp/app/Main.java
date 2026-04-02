package com.thebiggestapp.app;

import com.thebiggestapp.app.scheduler.SyncTask;

public class Main {
    public static void main(String[] args) {
        System.out.println("--- PROGRAMA INICIADO ---");
        try {
            SyncTask task = new SyncTask();
            task.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("--- PROGRAMA FINALIZADO ---");
    }
}