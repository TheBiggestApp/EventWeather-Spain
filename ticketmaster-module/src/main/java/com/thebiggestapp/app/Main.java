package com.thebiggestapp.app;

import com.thebiggestapp.app.scheduler.TicketmasterController;

public class Main {
    public static void main(String[] args) {
        System.out.println("--- INICIANDO TICKETMASTER MODULE ---");
        try {
            TicketmasterController task = new TicketmasterController();
            task.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("--- PROGRAMA FINALIZADO ---");
    }
}