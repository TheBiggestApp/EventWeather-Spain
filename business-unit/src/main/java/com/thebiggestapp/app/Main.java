package com.thebiggestapp.app;

import com.thebiggestapp.app.store.EventStoreReader;

public class Main {

    public static void main(String[] args) {
        new EventStoreReader().loadAll();
        System.out.println("Carga completada");
    }
}