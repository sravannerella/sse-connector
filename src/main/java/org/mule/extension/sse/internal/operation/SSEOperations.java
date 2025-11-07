package org.mule.extension.sse.internal.operation;
import org.mule.extension.sse.internal.connection.SSEConnection;

import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * SSE Operations
 * 
 * This class contains all operations for the SSE connector.
 * 
 * @author MuleSoft
 * @version 1.0.0
 */
public class SSEOperations {

    private static final Logger LOGGER = LoggerFactory.getLogger(SSEOperations.class);

    /**
     * Send Event Operation
     * 
     * Sends an event to all connected SSE clients.
     * This operation allows you to programmatically broadcast events from your Mule flow.
     * 
     * Usage example:
     * <code>
     * <sse:send-event config-ref="SSE_Config" 
     *                  eventName="notification" 
     *                  eventData='{"message": "Hello World"}' />
     * </code>
     * 
     * @param connection the SSE connection
     * @param eventName the name of the event to send
     * @param eventData the data payload of the event (can be JSON, XML, or plain text)
     * @return a confirmation message
     */
    @MediaType(value = MediaType.TEXT_PLAIN, strict = false)
    @DisplayName("Send Event")
    @Summary("Sends an event to all connected SSE clients")
    public String sendEvent(
            @Connection SSEConnection connection,
            @DisplayName("Event Name") @Summary("The name of the event to send")
            String eventName,
            @DisplayName("Event Data") @Summary("The data payload of the event")
            String eventData) {
        
        LOGGER.info("Sending event '{}' to all connected clients", eventName);
        
        try {
            // Validate inputs
            if (eventName == null || eventName.trim().isEmpty()) {
                throw new IllegalArgumentException("Event name cannot be null or empty");
            }
            
            if (eventData == null) {
                eventData = "";
            }

            // Broadcast the event to all connected clients
            connection.broadcastEvent(eventName, eventData);
            
            int clientCount = connection.getConnectedClientCount();
            String message = String.format("Event '%s' sent successfully to %d client(s)", 
                                         eventName, clientCount);
            
            LOGGER.info(message);
            return message;
            
        } catch (Exception e) {
            LOGGER.error("Failed to send event '{}'", eventName, e);
            throw new RuntimeException("Failed to send event: " + e.getMessage(), e);
        }
    }

    /**
     * Send Custom Event Operation
     * 
     * Sends a custom event with additional metadata to all connected SSE clients.
     * 
     * @param connection the SSE connection
     * @param eventName the name of the event to send
     * @param eventData the data payload of the event
     * @param eventId optional event ID for client-side tracking
     * @return a confirmation message
     */
    @MediaType(value = MediaType.TEXT_PLAIN, strict = false)
    @DisplayName("Send Custom Event")
    @Summary("Sends a custom event with metadata to all connected SSE clients")
    public String sendCustomEvent(
            @Connection SSEConnection connection,
            @DisplayName("Event Name") @Summary("The name of the event to send")
            String eventName,
            @DisplayName("Event Data") @Summary("The data payload of the event")
            String eventData,
            @Optional @DisplayName("Event ID") @Summary("Optional event ID for client-side tracking")
            String eventId) {
        
        LOGGER.info("Sending custom event '{}' with ID '{}' to all connected clients", 
                   eventName, eventId);
        
        try {
            // Validate inputs
            if (eventName == null || eventName.trim().isEmpty()) {
                throw new IllegalArgumentException("Event name cannot be null or empty");
            }
            
            if (eventData == null) {
                eventData = "";
            }

            // For custom events, we could include the event ID in the data
            // In a real implementation, you might format this according to SSE spec
            String formattedData = eventData;
            if (eventId != null && !eventId.isEmpty()) {
                formattedData = String.format("{\"id\":\"%s\",\"data\":%s}", eventId, eventData);
            }

            // Broadcast the event
            connection.broadcastEvent(eventName, formattedData);
            
            int clientCount = connection.getConnectedClientCount();
            String message = String.format("Custom event '%s' sent successfully to %d client(s)", 
                                         eventName, clientCount);
            
            LOGGER.info(message);
            return message;
            
        } catch (Exception e) {
            LOGGER.error("Failed to send custom event '{}'", eventName, e);
            throw new RuntimeException("Failed to send custom event: " + e.getMessage(), e);
        }
    }

    /**
     * Get Connected Clients Operation
     * 
     * Returns the number of currently connected SSE clients.
     * 
     * @param connection the SSE connection
     * @return the number of connected clients
     */
    @MediaType(value = MediaType.TEXT_PLAIN, strict = false)
    @DisplayName("Get Connected Clients")
    @Summary("Returns the number of currently connected SSE clients")
    public int getConnectedClients(@Connection SSEConnection connection) {
        
        int clientCount = connection.getConnectedClientCount();
        LOGGER.debug("Current connected clients: {}", clientCount);
        
        return clientCount;
    }

    /**
     * Broadcast Message Operation
     * 
     * Broadcasts a simple text message to all connected clients without an event name.
     * 
     * @param connection the SSE connection
     * @param message the message to broadcast
     * @return a confirmation message
     */
    @MediaType(value = MediaType.TEXT_PLAIN, strict = false)
    @DisplayName("Broadcast Message")
    @Summary("Broadcasts a simple message to all connected SSE clients")
    public String broadcastMessage(
            @Connection SSEConnection connection,
            @DisplayName("Message") @Summary("The message to broadcast to all clients")
            String message) {
        
        LOGGER.info("Broadcasting message to all connected clients");
        
        try {
            if (message == null) {
                message = "";
            }

            // Broadcast with empty event name (default message event)
            connection.broadcastEvent("", message);
            
            int clientCount = connection.getConnectedClientCount();
            String response = String.format("Message broadcast successfully to %d client(s)", 
                                          clientCount);
            
            LOGGER.info(response);
            return response;
            
        } catch (Exception e) {
            LOGGER.error("Failed to broadcast message", e);
            throw new RuntimeException("Failed to broadcast message: " + e.getMessage(), e);
        }
    }

    /**
     * Disconnect Client Operation
     * 
     * Disconnects a specific SSE client by their client ID.
     * 
     * @param connection the SSE connection
     * @param clientId the ID of the client to disconnect
     * @return a confirmation message
     */
    @MediaType(value = MediaType.TEXT_PLAIN, strict = false)
    @DisplayName("Disconnect Client")
    @Summary("Disconnects a specific SSE client by client ID")
    public String disconnectClient(
            @Connection SSEConnection connection,
            @DisplayName("Client ID") @Summary("The ID of the client to disconnect")
            String clientId) {
        
        LOGGER.info("Disconnecting client: {}", clientId);
        
        try {
            // Validate input
            if (clientId == null || clientId.trim().isEmpty()) {
                throw new IllegalArgumentException("Client ID cannot be null or empty");
            }

            // Unregister the client (this will close their connection)
            connection.unregisterClient(clientId);
            
            String message = String.format("Client '%s' disconnected successfully", clientId);
            LOGGER.info(message);
            return message;
            
        } catch (Exception e) {
            LOGGER.error("Failed to disconnect client '{}'", clientId, e);
            throw new RuntimeException("Failed to disconnect client: " + e.getMessage(), e);
        }
    }
}
