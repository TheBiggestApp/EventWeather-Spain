package com.thebiggestapp.app.model;

public class Clima {
    private final String ciudad;
    private final double temp;
    private final String desc;

    public Clima(String ciudad, double temp, String desc) {
        this.ciudad = ciudad;
        this.temp = temp;
        this.desc = desc;
    }

    public String getCiudad() { return ciudad; }
    public double getTemp() { return temp; }
    public String getDesc() { return desc; }
}