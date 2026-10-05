package com.coding.agent.backend.tools;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tool component managing customer order tracking and cancellation.
 * Registered as an AI tool for Spring AI ChatClient.
 */
@Component
public class OrderTools {

    private static final Logger log = LoggerFactory.getLogger(OrderTools.class);

    private final Map<String, String> orders = Map.of(
            "1042", "Shipped - arriving tomorrow",
            "1043", "Processing - arriving not yet shipped",
            "1044", "Not Ordered - out of stock",
            "1045", "Delivered at your address"
    );

    /**
     * Queries the delivery/shipping status of a customer order by order ID.
     */
    @Tool(name = "getOrderStatus", description = "Get the status of a customer order by its ID")
    public String getOrderStatus(@ToolParam(description = "Order ID number, e.g. 1042") String orderId) {
        log.info("[TOOL CALL] getOrderStatus - orderId: {}", orderId);
        try {
            if (orderId == null || orderId.isBlank()) {
                String validation = "Order ID is required.";
                log.warn("[TOOL VALIDATION] getOrderStatus failed: {}", validation);
                return validation;
            }
            String status = orders.getOrDefault(orderId.trim(), "Order Not Found");
            log.info("[TOOL RESPONSE] getOrderStatus: {}", status);
            return status;
        } catch (Exception e) {
            log.error("[TOOL ERROR] getOrderStatus failed for {}: {}", orderId, e.getMessage(), e);
            return "Unable to fetch order status for " + orderId;
        }
    }

    /**
     * Cancels an existing customer order by order ID.
     */
    @Tool(name = "cancelOrder", description = "Cancel a customer order by its ID")
    public String cancelOrder(@ToolParam(description = "Order ID number to cancel, e.g. 1042") String orderId) {
        log.info("[TOOL CALL] cancelOrder - orderId: {}", orderId);
        try {
            if (orderId == null || orderId.isBlank()) {
                String validation = "Order ID is required to cancel an order.";
                log.warn("[TOOL VALIDATION] cancelOrder failed: {}", validation);
                return validation;
            }
            String result = orders.containsKey(orderId.trim())
                    ? "Order " + orderId.trim() + " has been canceled"
                    : "Order Not Found";
            log.info("[TOOL RESPONSE] cancelOrder: {}", result);
            return result;
        } catch (Exception e) {
            log.error("[TOOL ERROR] cancelOrder failed for {}: {}", orderId, e.getMessage(), e);
            return "Unable to cancel order " + orderId;
        }
    }

    /**
     * Returns the aggregate count of all orders recorded in the system.
     */
    @Tool(name = "getOrderCount", description = "Get the total count of customer orders")
    public Integer getOrderCount() {
        log.info("[TOOL CALL] getOrderCount");
        try {
            int count = orders.size();
            log.info("[TOOL RESPONSE] getOrderCount: {}", count);
            return count;
        } catch (Exception e) {
            log.error("[TOOL ERROR] getOrderCount failed: {}", e.getMessage(), e);
            return 0;
        }
    }
}
