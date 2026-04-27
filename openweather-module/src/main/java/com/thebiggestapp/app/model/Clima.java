package com.thebiggestapp.app.model;

public class Clima {
    private final String ciudad;
    private final double temp;
    private final String desc;
    private final double tempMin;
    private final double tempMax;
    private final int humidity;
    private final double windSpeed;

    public Clima(String ciudad, double temp, String desc, double tempMin, double tempMax,
                 int humidity, double windSpeed) {
        this.ciudad = ciudad;
        this.temp = temp;
        this.desc = desc;
        this.tempMin = tempMin;
        this.tempMax = tempMax;
        this.humidity = humidity;
        this.windSpeed = windSpeed;
    }

    public String getCiudad()  { return ciudad; }
    public double getTemp()    { return temp; }
    public String getDesc()    { return desc; }
    public double getTempMin() { return tempMin; }
    public double getTempMax() { return tempMax; }
    public int getHumidity()   { return humidity; }
    public double getWindSpeed() { return windSpeed; }
}
