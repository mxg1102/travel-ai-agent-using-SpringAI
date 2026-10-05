package com.coding.agent.backend.tools;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Tool component providing live weather forecasts and atmospheric metrics via WeatherStack API.
 * Registered as an AI tool for Spring AI ChatClient.
 */
@Component
public class WeatherTools {

    private static final Logger log = LoggerFactory.getLogger(WeatherTools.class);

    private final RestClient restClient;
    private final String accessKey;
    private final String baseUrl;

    public WeatherTools(
            @Value("${weatherstack.access-key:3588ddfccae2530c28d16c345180ad17}") String accessKey,
            @Value("${weatherstack.base-url:http://api.weatherstack.com}") String baseUrl) {
        this.accessKey = accessKey;
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder().build();
    }

    /**
     * Fetches current real-time weather, temperature, humidity, wind, and precipitation for a city.
     */
    @Tool(name = "getForecast", description = "Get current weather for a city. Returns temperature, weather condition, and humidity.")
    public String getForecast(String city) {
        log.info("[TOOL CALL] getForecast - city: {}", city);

        if (city == null || city.isBlank()) {
            String validationMsg = "City name cannot be empty.";
            log.warn("[TOOL VALIDATION] getForecast failed: {}", validationMsg);
            return validationMsg;
        }

        String cleanCity = city.replaceAll("\\(.*?\\)", "").trim();

        try {
            String uri = UriComponentsBuilder.fromUriString(baseUrl)
                    .path("/current")
                    .queryParam("access_key", accessKey)
                    .queryParam("query", cleanCity)
                    .toUriString();

            WeatherResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(WeatherResponse.class);

            if (response != null && response.current() != null && response.location() != null) {
                String condition = (response.current().weatherDescriptions() != null && !response.current().weatherDescriptions().isEmpty())
                        ? response.current().weatherDescriptions().getFirst().trim()
                        : "Clear";

                int wind = response.current().windSpeed() != null ? response.current().windSpeed() : 18;
                double precip = response.current().precip() != null ? response.current().precip() : 0.0;
                String cityName = response.location().name();
                String countryName = response.location().country();

                String result = String.format("Weather in %s, %s: %d°C, %s, Humidity: %d%%, Wind: %d km/h, Precipitation: %.1f mm",
                        cityName, countryName, response.current().temperature(), condition, response.current().humidity(), wind, precip);

                log.info("[TOOL RESPONSE] getForecast: {}", result);
                return result;

            } else if (response != null && response.error() != null) {
                String apiErr = String.format("Weather unavailable for '%s': %s", cleanCity, response.error().info());
                log.warn("[TOOL RESPONSE] getForecast API Error: {}", apiErr);
                return apiErr;
            }
        } catch (Exception e) {
            log.error("[TOOL ERROR] getForecast failed for city {}: {}", cleanCity, e.getMessage(), e);
            return String.format("Weather service error for '%s': %s", cleanCity, e.getMessage());
        }

        String noData = String.format("No weather data found for '%s'.", cleanCity);
        log.info("[TOOL RESPONSE] getForecast: {}", noData);
        return noData;
    }

    public record WeatherResponse(
            Location location,
            CurrentWeather current,
            ApiError error
    ) {}

    public record Location(
            String name,
            String country,
            String localtime
    ) {}

    public record CurrentWeather(
            int temperature,
            @JsonProperty("weather_descriptions") List<String> weatherDescriptions,
            int humidity,
            @JsonProperty("wind_speed") Integer windSpeed,
            Double precip,
            @JsonProperty("uv_index") Integer uvIndex,
            Integer feelslike
    ) {}

    public record ApiError(
            String info
    ) {}
}
