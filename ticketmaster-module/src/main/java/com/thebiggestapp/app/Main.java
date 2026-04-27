package com.thebiggestapp.app;

import com.thebiggestapp.app.scheduler.TicketmasterController;

public class Main {
    public static void main(String[] args) {
        System.out.println("--- INICIANDO TICKETMASTER MODULE (Sprint 2) ---");
        new TicketmasterController().start();
    }
}
