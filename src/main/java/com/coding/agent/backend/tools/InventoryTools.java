package com.coding.agent.backend.tools;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tool component providing inventory stock status and catalog queries for e-commerce items.
 * Registered as an AI tool for Spring AI ChatClient.
 */
@Component
public class InventoryTools {

    private static final Logger log = LoggerFactory.getLogger(InventoryTools.class);

    private final Map<String, Integer> stock = Map.of(
            "Bluetooth Headphone", 5,
            "Speaker", 4,
            "USB-C", 7,
            "Laptop", 8,
            "Desktop", 3,
            "Monitor with Set", 10,
            "WebCam", 10,
            "Noise-cancellation Headphones", 10
    );

    /**
     * Checks current stock quantity and availability for a specific product name.
     */
    @Tool(name = "checkStock", description = "Check the availability of a product by its name")
    public String checkStock(@ToolParam(description = "Exact or partial product name, e.g. Laptop, USB-C") String productName) {
        log.info("[TOOL CALL] checkStock - product: {}", productName);
        try {
            if (productName == null || productName.isBlank()) {
                String validation = "Product name is required.";
                log.warn("[TOOL VALIDATION] checkStock failed: {}", validation);
                return validation;
            }

            String cleanName = productName.trim();
            // Direct or case-insensitive search
            String matchedKey = stock.keySet().stream()
                    .filter(k -> k.equalsIgnoreCase(cleanName) || k.toLowerCase().contains(cleanName.toLowerCase()))
                    .findFirst()
                    .orElse(null);

            String result;
            if (matchedKey == null) {
                result = "Product Not Found";
            } else {
                int quantity = stock.get(matchedKey);
                result = quantity > 0
                        ? "Product Name : " + matchedKey + " | Available Quantity : " + quantity
                        : "Product Name : " + matchedKey + " | Out Of Stock";
            }

            log.info("[TOOL RESPONSE] checkStock: {}", result);
            return result;
        } catch (Exception e) {
            log.error("[TOOL ERROR] checkStock failed for {}: {}", productName, e.getMessage(), e);
            return "Unable to check stock for " + productName;
        }
    }

    /**
     * Calculates the aggregate sum of all inventory units in stock.
     */
    @Tool(name = "getTotalProductsInStock", description = "Get the total count of the products in the stock")
    public Integer getTotalProductsInStock() {
        log.info("[TOOL CALL] getTotalProductsInStock");
        try {
            int total = stock.values().stream().mapToInt(Integer::intValue).sum();
            log.info("[TOOL RESPONSE] getTotalProductsInStock: {}", total);
            return total;
        } catch (Exception e) {
            log.error("[TOOL ERROR] getTotalProductsInStock failed: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Retrieves the names of all products currently having at least 1 unit in stock.
     */
    @Tool(name = "getAllProductsInStocks", description = "Get the list of all products in stock")
    public List<String> getAllProductsInStocks() {
        log.info("[TOOL CALL] getAllProductsInStocks");
        try {
            List<String> list = stock.keySet().stream()
                    .filter(name -> stock.get(name) > 0)
                    .collect(Collectors.toList());
            log.info("[TOOL RESPONSE] getAllProductsInStocks: {} items", list.size());
            return list;
        } catch (Exception e) {
            log.error("[TOOL ERROR] getAllProductsInStocks failed: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
