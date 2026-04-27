package com.thebiggestapp.app.services;

import com.thebiggestapp.app.config.Config;
import com.thebiggestapp.app.model.Clima;
import com.google.gson.*;
import okhttp3.*;

public class OpenWeatherService {
    private final OkHttpClient client = new OkHttpClient();
    private final String key = Config.get("OPENWEATHER_KEY");

    public Clima getClimaPorCiudad(String ciudad) throws Exception {
        String url = "https://api.openweathermap.org/data/2.5/weather?q="
                + ciudad + ",ES&appid=" + key + "&units=metric&lang=es";
        Request req = new Request.Builder().url(url).build();

        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new RuntimeException("Error en API OpenWeather: " + res.code());

            JsonObject json = JsonParser.parseString(res.body().string()).getAsJsonObject();
            JsonObject main = json.getAsJsonObject("main");

            double temp      = main.get("temp").getAsDouble();
            double tempMin   = main.get("temp_min").getAsDouble();
            double tempMax   = main.get("temp_max").getAsDouble();
            int    humidity  = main.get("humidity").getAsInt();
            double windSpeed = json.getAsJsonObject("wind").get("speed").getAsDouble();
            String desc      = json.getAsJsonArray("weather").get(0)
                                   .getAsJsonObject().get("description").getAsString();

            return new Clima(ciudad, temp, desc, tempMin, tempMax, humidity, windSpeed);
        }
    }
}
