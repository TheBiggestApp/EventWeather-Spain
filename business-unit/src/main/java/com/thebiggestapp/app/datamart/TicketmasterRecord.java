package com.thebiggestapp.app.datamart;

public record TicketmasterRecord(
        String id,
        String ts,
        String nombre,
        String fecha,
        String hora,
        String ciudad,
        String venue,
        String categoria,
        String url,
        String ss
) {}