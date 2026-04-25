package com.thebiggestapp.app.model;

public class EventoPHQ {
    private final String id;
    private final String titulo;
    private final String categoria;
    private final String fechaInicio;
    private final String fechaFin;
    private final double latitud;
    private final double longitud;
    private final int impacto;

    public EventoPHQ(String id, String titulo, String categoria, String fechaInicio, String fechaFin,
                     double latitud, double longitud, int impacto) {
        this.id = id;
        this.titulo = titulo;
        this.categoria = categoria;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.latitud = latitud;
        this.longitud = longitud;
        this.impacto = impacto;
    }

    public String getId()          { return id; }
    public String getTitulo()      { return titulo; }
    public String getCategoria()   { return categoria; }
    public String getFechaInicio() { return fechaInicio; }
    public String getFechaFin()    { return fechaFin; }
    public double getLatitud()     { return latitud; }
    public double getLongitud()    { return longitud; }
    public int    getImpacto()     { return impacto; }
}
