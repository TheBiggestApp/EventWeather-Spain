package com.thebiggestapp.app.datamart;

public record WeatherRecord(
        String ciudad,
        String ts,
        double temp,
        double tempMin,
        double tempMax,
        String descripcion,
        int    humidity,
        double windSpeed,
        String ss
) {}