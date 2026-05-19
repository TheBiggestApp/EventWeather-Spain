package com.thebiggestapp.app.services;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class EventWeatherState {

    public static final String PRONOSTICO_CONFIRMADO = "PRONOSTICO_CONFIRMADO";
    public static final String TENDENCIA_GENERAL     = "TENDENCIA_GENERAL";
    public static final String PREDICCION_HISTORICA  = "PREDICCION_HISTORICA";

   
    public static String calcular(String fechaInicio) {
        if (fechaInicio == null || fechaInicio.isBlank()) return PRONOSTICO_CONFIRMADO;
        try {
            LocalDate fechaEvento = LocalDate.parse(fechaInicio.substring(0, 10));
            long dias = ChronoUnit.DAYS.between(LocalDate.now(), fechaEvento);
            if (dias > 14) return PREDICCION_HISTORICA;
            if (dias >= 5) return TENDENCIA_GENERAL;
            return PRONOSTICO_CONFIRMADO;
        } catch (Exception e) {
            return PRONOSTICO_CONFIRMADO;
        }
    }
}
