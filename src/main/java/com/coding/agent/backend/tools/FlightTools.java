package com.coding.agent.backend.tools;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tool component providing live real-time flight search and flight details via booking-com15 RapidAPI.
 * Registered as an AI tool for Spring AI ChatClient.
 */
@Component
public class FlightTools {

    private static final Logger log = LoggerFactory.getLogger(FlightTools.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiHost;
    private final String baseUrl;

    private static final Map<String, String> AIRPORTS = Map.ofEntries(
            Map.entry("delhi", "DEL"), Map.entry("new delhi", "DEL"),
            Map.entry("mumbai", "BOM"), Map.entry("bangalore", "BLR"),
            Map.entry("bengaluru", "BLR"), Map.entry("goa", "GOI"),
            Map.entry("kolkata", "CCU"), Map.entry("chennai", "MAA"),
            Map.entry("hyderabad", "HYD"), Map.entry("patna", "PAT"),
            Map.entry("jaipur", "JAI"), Map.entry("ahmedabad", "AMD"),
            Map.entry("pune", "PNQ"), Map.entry("kochi", "COK"),
            Map.entry("cochin", "COK"), Map.entry("lucknow", "LKO"),
            Map.entry("srinagar", "SXR"), Map.entry("varanasi", "VNS"),
            Map.entry("guwahati", "GAU"), Map.entry("chandigarh", "IXC"),
            Map.entry("amritsar", "ATQ"), Map.entry("bhubaneswar", "BBI"),
            Map.entry("indore", "IDR"),
            Map.entry("rome", "ROM"), Map.entry("milan", "MIL"), Map.entry("milan malpensa", "MXP"),
            Map.entry("venice", "VCE"), Map.entry("florence", "FLR"), Map.entry("naples", "NAP"),
            Map.entry("bologna", "BLQ"), Map.entry("turin", "TRN"), Map.entry("pisa", "PSA"),
            Map.entry("london", "LON"), Map.entry("paris", "PAR"), Map.entry("amsterdam", "AMS"),
            Map.entry("madrid", "MAD"), Map.entry("barcelona", "BCN"), Map.entry("berlin", "BER"),
            Map.entry("frankfurt", "FRA"), Map.entry("munich", "MUC"), Map.entry("vienna", "VIE"),
            Map.entry("zurich", "ZRH"), Map.entry("lisbon", "LIS"), Map.entry("athens", "ATH"),
            Map.entry("new york", "JFK"), Map.entry("los angeles", "LAX"), Map.entry("tokyo", "TYO"),
            Map.entry("singapore", "SIN"), Map.entry("dubai", "DXB"), Map.entry("istanbul", "IST"),
            Map.entry("cairo", "CAI"), Map.entry("sydney", "SYD"), Map.entry("toronto", "YYZ")
    );

    public FlightTools(
            @Value("${rapidapi.flights.key:${rapidapi.booking.key}}") String apiKey,
            @Value("${rapidapi.flights.host:booking-com15.p.rapidapi.com}") String apiHost,
            @Value("${rapidapi.flights.base-url:https://booking-com15.p.rapidapi.com}") String baseUrl) {
        this.apiKey = apiKey;
        this.apiHost = apiHost;
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder().build();
        this.objectMapper = new ObjectMapper();
    }

    private String resolveAirportId(String location) {
        if (location == null || location.isBlank()) return "DEL.AIRPORT";
        String clean = location.replaceAll("\\(.*?\\)", "").trim().toLowerCase();
        if (AIRPORTS.containsKey(clean)) return AIRPORTS.get(clean) + ".AIRPORT";
        if (clean.length() == 3) return clean.toUpperCase() + ".AIRPORT";
        for (var entry : AIRPORTS.entrySet()) {
            if (clean.contains(entry.getKey()) || entry.getKey().contains(clean)) {
                return entry.getValue() + ".AIRPORT";
            }
        }
        return clean.toUpperCase() + ".AIRPORT";
    }

    /**
     * Searches for available domestic flights with real-time live prices in INR (₹).
     */
    @Tool(name = "searchFlight", description = "Search for available real-time domestic or international flights worldwide between source and destination cities or airport codes on a travel date. Prices are in EUR by default.")
    public String searchFlight(
            @ToolParam(description = "Departure city or airport code (e.g. Patna, Delhi, Mumbai, DEL, BOM)") String source,
            @ToolParam(description = "Arrival city or airport code (e.g. Delhi, Goa, Mumbai, BOM, GOI)") String destination,
            @ToolParam(description = "Travel departure date in YYYY-MM-DD format", required = false) String date) {

        log.info("[TOOL CALL] searchFlight - source: {}, destination: {}, date: {}", source, destination, date);

        if (source == null || source.isBlank() || destination == null || destination.isBlank()) {
            String validationMsg = "Source and Destination are required to search flights.";
            log.warn("[TOOL VALIDATION] searchFlight failed: {}", validationMsg);
            return validationMsg;
        }

        String fromId = resolveAirportId(source);
        String toId = resolveAirportId(destination);
        String departDate = (date != null && !date.isBlank()) ? date.trim() : LocalDate.now().plusWeeks(1).toString();

        try {
            String searchUri = UriComponentsBuilder.fromUriString(baseUrl)
                    .path("/api/v1/flights/searchFlights")
                    .queryParam("fromId", fromId)
                    .queryParam("toId", toId)
                    .queryParam("departDate", departDate)
                    .queryParam("currency_code", "EUR")
                    .queryParam("adults", "1")
                    .queryParam("cabinClass", "ECONOMY")
                    .queryParam("sort", "BEST")
                    .queryParam("pageNo", "1")
                    .toUriString();

            String rawJson = restClient.get()
                    .uri(searchUri)
                    .header("x-rapidapi-key", apiKey)
                    .header("x-rapidapi-host", apiHost)
                    .retrieve()
                    .body(String.class);

            if (rawJson == null || rawJson.isBlank()) {
                String errorResult = String.format("No response received from flight service for %s to %s.", source, destination);
                log.warn("[TOOL RESPONSE] searchFlight: {}", errorResult);
                return errorResult;
            }

            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode offersNode = root.path("data").path("flightOffers");

            if (!offersNode.isArray() || offersNode.isEmpty()) {
                String noResults = String.format("No live flights found from %s to %s on %s.", source, destination, departDate);
                log.info("[TOOL RESPONSE] searchFlight: {}", noResults);
                return noResults;
            }

            String fallbackSrc = fromId.replace(".AIRPORT", "");
            String fallbackDest = toId.replace(".AIRPORT", "");

            String result = StreamSupport.stream(offersNode.spliterator(), false)
                    .limit(5)
                    .map(offer -> formatOffer(offer, fallbackSrc, fallbackDest, departDate))
                    .collect(Collectors.joining(", "));

            log.info("[TOOL RESPONSE] searchFlight - found results: {}", result);
            return result;

        } catch (Exception e) {
            log.error("[TOOL ERROR] searchFlight failed for {} -> {} on {}: {}", source, destination, departDate, e.getMessage(), e);
            return String.format("Error fetching real-time flights for %s to %s on %s: %s", source, destination, departDate, e.getMessage());
        }
    }

    /**
     * Fetches detailed flight policies, baggage rules, and fare breakdown using getFlightDetails endpoint.
     */
    @Tool(name = "getFlightDetails", description = "Get detailed baggage allowances, policies, and fare breakdown for a specific flight using its token or identifier.")
    public String getFlightDetails(
            @ToolParam(description = "Flight token or identifier from search results") String token) {

        log.info("[TOOL CALL] getFlightDetails - token: {}", token);
        if (token == null || token.isBlank()) {
            String errorMsg = "Flight token is required to get flight details.";
            log.warn("[TOOL VALIDATION] getFlightDetails failed: {}", errorMsg);
            return errorMsg;
        }

        try {
            String detailsUri = UriComponentsBuilder.fromUriString(baseUrl)
                    .path("/api/v1/flights/getFlightDetails")
                    .queryParam("token", token.trim())
                    .queryParam("currency_code", "EUR")
                    .toUriString();

            String rawJson = restClient.get()
                    .uri(detailsUri)
                    .header("x-rapidapi-key", apiKey)
                    .header("x-rapidapi-host", apiHost)
                    .retrieve()
                    .body(String.class);

            if (rawJson == null || rawJson.isBlank()) {
                String notFound = "No details found for the specified flight token.";
                log.info("[TOOL RESPONSE] getFlightDetails: {}", notFound);
                return notFound;
            }

            JsonNode dataNode = objectMapper.readTree(rawJson).path("data");
            String baggage = dataNode.path("baggagePolicy").asText("Standard 15kg check-in + 7kg cabin baggage");
            String cancellation = dataNode.path("cancellationPolicy").asText("Cancellation subject to airline fare rules");
            long fare = dataNode.path("priceBreakdown").path("total").path("units").asLong(0);
            String priceStr = fare > 0 ? " | Total Price: ₹" + String.format("%,d", fare) : "";

            String response = String.format("Flight Details: Baggage: %s | Policy: %s%s", baggage, cancellation, priceStr);
            log.info("[TOOL RESPONSE] getFlightDetails: {}", response);
            return response;

        } catch (Exception e) {
            log.error("[TOOL ERROR] getFlightDetails failed for token {}: {}", token, e.getMessage(), e);
            return "Flight details: Standard 15kg check-in + 7kg cabin baggage. Refundable/reschedulable per fare tier.";
        }
    }

    private String formatOffer(JsonNode offer, String fallbackSrc, String fallbackDest, String departDate) {
        JsonNode firstSegment = offer.path("segments").path(0);
        String depTime = extractTime(firstSegment.path("departureTime").asText(""));
        String arrTime = extractTime(firstSegment.path("arrivalTime").asText(""));

        String src = firstSegment.path("departureAirport").path("code").asText(fallbackSrc);
        String dest = firstSegment.path("arrivalAirport").path("code").asText(fallbackDest);

        int totalSeconds = firstSegment.path("totalTime").asInt(0);
        String duration = totalSeconds > 0 ? String.format("%dh %02dm", totalSeconds / 3600, (totalSeconds % 3600) / 60) : "";

        JsonNode legs = firstSegment.path("legs");
        String stops = legs.size() > 1 ? (legs.size() - 1) + "-Stop" : "Direct";

        JsonNode firstLeg = legs.path(0);
        String airline = firstLeg.path("carriersData").path(0).path("name").asText("Airline");
        int fn = firstLeg.path("flightInfo").path("flightNumber").asInt(0);
        String carrier = firstLeg.path("flightInfo").path("carrierInfo").path("operatingCarrier").asText("");
        String flightNum = fn > 0 ? "(" + (carrier.isBlank() ? "" : carrier + "-") + fn + ") " : "";

        // Extract price (per-adult rate or total)
        long price = offer.path("travellerPrices").path(0).path("travellerPriceBreakdown").path("total").path("units").asLong(
                offer.path("priceBreakdown").path("total").path("units").asLong(0)
        );

        String priceStr = price > 0 ? "EUR " + String.format("%,d", price) : "Price on request";
        String timing = (!depTime.isEmpty() && !arrTime.isEmpty())
                ? String.format(" [Dep: %s, Arr: %s%s, %s]", depTime, arrTime, duration.isBlank() ? "" : ", " + duration, stops)
                : "";

        return String.format("%s %s%s -> %s on %s%s : %s", airline, flightNum, src, dest, departDate, timing, priceStr);
    }

    private String extractTime(String dt) {
        if (dt == null || dt.isBlank()) return "";
        int tIdx = dt.indexOf('T');
        if (tIdx >= 0 && dt.length() >= tIdx + 6) return dt.substring(tIdx + 1, tIdx + 6);
        int sIdx = dt.indexOf(' ');
        if (sIdx >= 0 && dt.length() >= sIdx + 6) return dt.substring(sIdx + 1, sIdx + 6);
        return dt;
    }
}
