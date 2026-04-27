package com.thebiggestapp.app.model;

public class Evento {
    private final String id;
    private final String nombre;
    private final String fecha;
    private String hora;

    public Evento(String id, String nombre, String fecha) {
        this.id = id;
        this.nombre = nombre;
        this.fecha = fecha;
    }

    public String getId()     { return id; }
    public String getNombre() { return nombre; }
    public String getFecha()  { return fecha; }
    public String getHora()   { return hora; }
    public void setHora(String hora) { this.hora = hora; }
}
