package com.thebiggestapp.app;

import com.thebiggestapp.app.subscriber.EventStoreSubscriber;

public class Main {
    public static void main(String[] args) {
        System.out.println("--- INICIANDO EVENT STORE BUILDER (Sprint 2) ---");
        try {
            new EventStoreSubscriber().start();
        } catch (Exception e) {
            System.err.println("[EventStoreBuilder] Error fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
