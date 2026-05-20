package com.thebiggestapp.app.services;

import com.thebiggestapp.app.config.Config;
import com.thebiggestapp.app.model.Clima;
import com.google.gson.*;
import okhttp3.*;

import java.util.ArrayList;
import java.util.List;

public class OpenWeatherService {
    private final OkHttpClient client = new OkHttpClient();
    private final String key = Config.get("OPENWEATHER_KEY");

    public List<Clima> getForecastPorCiudad(String ciudad) throws Exception {
        String url = "https://api.openweathermap.org/data/2.5/forecast?q="
                + ciudad + ",ES&appid=" + key + "&units=metric&lang=es";

        Request req = new Request.Builder().url(url).build();

        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful())
                throw new RuntimeException("Error en API OpenWeather: " + res.code());

            JsonObject json  = JsonParser.parseString(res.body().string()).getAsJsonObject();
            JsonArray  lista = json.getAsJsonArray("list");

            List<Clima> resultado = new ArrayList<>();
            for (JsonElement elem : lista) {
                JsonObject entry = elem.getAsJsonObject();
                JsonObject main  = entry.getAsJsonObject("main");

                String fecha     = entry.get("dt_txt").getAsString(); // "2026-05-21 15:00:00"
                double temp      = main.get("temp").getAsDouble();
                double tempMin   = main.get("temp_min").getAsDouble();
                double tempMax   = main.get("temp_max").getAsDouble();
                int    humidity  = main.get("humidity").getAsInt();
                double windSpeed = entry.getAsJsonObject("wind").get("speed").getAsDouble();
                String desc      = entry.getAsJsonArray("weather").get(0)
                        .getAsJsonObject().get("description").getAsString();

                resultado.add(new Clima(ciudad, fecha, temp, desc,
                        tempMin, tempMax, humidity, windSpeed));
            }
            return resultado;
        }
    }
}