package com.thebiggestapp.app.services;

import com.thebiggestapp.app.config.Config;
import com.thebiggestapp.app.model.Clima;
import com.google.gson.*;
import okhttp3.*;

public class OpenWeatherService {
    private final OkHttpClient client = new OkHttpClient();
    private final String key = Config.get("OPENWEATHER_KEY");

    public Clima getClima(double lat, double lon) throws Exception {
        String url = "https://api.openweathermap.org/data/2.5/weather?lat="+lat+"&lon="+lon+"&appid="+key+"&units=metric&lang=es";
        Request req = new Request.Builder().url(url).build();
        try (Response res = client.newCall(req).execute()) {
            JsonObject json = JsonParser.parseString(res.body().string()).getAsJsonObject();
            double temp = json.getAsJsonObject("main").get("temp").getAsDouble();
            String desc = json.getAsJsonArray("weather").get(0).getAsJsonObject().get("description").getAsString();
            return new Clima(temp, desc);
        }
    }
}