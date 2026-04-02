package com.thebiggestapp.app.model;

public class Evento {
    private final String id;
    private final String nombre;
    private final String fecha;
    private final double latitud;
    private final double longitud;
    private double temperatura;
    private String climaDescripcion;
    private String hora;

    public Evento(String id, String nombre, String fecha, double latitud, double longitud) {
        this.id = id;
        this.nombre = nombre;
        this.fecha = fecha;
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public String getFecha() { return fecha; }
    public double getLatitud() { return latitud; }
    public double getLongitud() { return longitud; }
    public double getTemperatura() { return temperatura; }
    public String getClimaDescripcion() { return climaDescripcion; }
    public String getHora() { return hora; }

    public void setTemperatura(double temperatura) { this.temperatura = temperatura; }
    public void setClimaDescripcion(String desc) { this.climaDescripcion = desc; }

    public void setHora(String hora) { this.hora = hora; }
}