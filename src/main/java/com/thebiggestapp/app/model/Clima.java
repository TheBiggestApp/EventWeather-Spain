package com.thebiggestapp.app.model;

public class Clima {
    private final double temp;
    private final String desc;

    public Clima(double temp, String desc) {
        this.temp = temp;
        this.desc = desc;
    }

    public double getTemp() { return temp; }
    public String getDesc() { return desc; }
}