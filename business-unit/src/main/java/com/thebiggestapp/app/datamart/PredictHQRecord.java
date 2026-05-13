package com.thebiggestapp.app.datamart;

public record PredictHQRecord(
        String id,
        String ts,
        String titulo,
        String categoria,
        String ciudad,
        String fechaInicio,
        String fechaFin,
        double latitud,
        double longitud,
        int    impacto,
        String ss
) {}