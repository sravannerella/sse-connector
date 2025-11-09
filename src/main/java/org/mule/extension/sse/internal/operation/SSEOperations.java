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
     * Sends an event to a specific SSE client (unicast).
     * This operation allows you to send targeted events from your Mule flow to individual clients.
     * 
     * Usage example:
     * <code>
     * <sse:send-event config-ref="SSE_Config" 
     *                  clientId="client-uuid-123"
     *                  eventName="notification" 
     *                  eventData='{"message": "Hello World"}' />
     * </code>
     * 
     * @param connection the SSE connection
     * @param clientId the ID of the client to send the event to
     * @param eventName the name of the event to send
     * @param eventData the data payload of the event (can be JSON, XML, or plain text)
     * @param eventId optional event ID for client-side tracking
     * @return a confirmation message
     */
    @MediaType(value = MediaType.TEXT_PLAIN, strict = false)
    @DisplayName("Send Event")
    @Summary("Sends an event to a specific SSE client")
    public String sendEvent(
            @Connection SSEConnection connection,
            @DisplayName("Client ID") @Summary("The ID of the client to send the event to")
            String clientId,
            @DisplayName("Event Name") @Summary("The name of the event to send")
            String eventName,
            @DisplayName("Event Data") @Summary("The data payload of the event")
            String eventData,
            @Optional @DisplayName("Event ID") @Summary("Optional event ID for client-side tracking")
            String eventId) {
        
        LOGGER.debug("Sending event '{}' to client: {}", eventName, clientId);
        
        try {
            // Validate inputs
            if (clientId == null || clientId.trim().isEmpty()) {
                throw new IllegalArgumentException("Client ID cannot be null or empty");
            }
            
            if (eventName == null || eventName.trim().isEmpty()) {
                throw new IllegalArgumentException("Event name cannot be null or empty");
            }
            
            if (eventData == null) {
                eventData = "";
            }

            // Format data with event ID if provided
            String formattedData = eventData;
            if (eventId != null && !eventId.isEmpty()) {
                formattedData = String.format("{\"id\":\"%s\",\"data\":%s}", eventId, eventData);
            }

            // Send event to specific client
            connection.sendEventToClient(clientId, eventName, formattedData);
            
            String message = String.format("Event '%s' sent successfully to client '%s'", 
                                         eventName, clientId);
            
            LOGGER.debug(message);
            return message;
            
        } catch (Exception e) {
            LOGGER.error("Failed to send event '{}' to client '{}'", eventName, clientId, e);
            throw new RuntimeException("Failed to send event: " + e.getMessage(), e);
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
        
        LOGGER.debug("Broadcasting message to all connected clients");
        
        try {
            if (message == null) {
                message = "";
            }

            // Broadcast with empty event name (default message event)
            connection.broadcastEvent("", message);
            
            int clientCount = connection.getConnectedClientCount();
            String response = String.format("Message broadcast successfully to %d client(s)", 
                                          clientCount);
            
            LOGGER.debug(response);
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
        
        LOGGER.debug("Disconnect operation called for client: {}", clientId);
        
        try {
            // Validate input
            if (clientId == null || clientId.trim().isEmpty()) {
                throw new IllegalArgumentException("Client ID cannot be null or empty");
            }

            // Log current client count before disconnect
            int beforeCount = connection.getConnectedClientCount();
            LOGGER.debug("Connected clients before disconnect: {}", beforeCount);
            
            // Unregister the client (this will close their connection)
            connection.unregisterClient(clientId);
            
            // Log client count after disconnect
            int afterCount = connection.getConnectedClientCount();
            LOGGER.debug("Connected clients after disconnect: {}", afterCount);
            
            String message = String.format("Client '%s' disconnected successfully", clientId);
            LOGGER.debug(message);
            return message;
            
        } catch (Exception e) {
            LOGGER.error("Failed to disconnect client '{}'", clientId, e);
            throw new RuntimeException("Failed to disconnect client: " + e.getMessage(), e);
        }
    }

    /**
     * Disconnect All Clients Operation
     * 
     * Disconnects all connected SSE clients.
     * 
     * @param connection the SSE connection
     * @return a confirmation message with the count of disconnected clients
     */
    @MediaType(value = MediaType.TEXT_PLAIN, strict = false)
    @DisplayName("Disconnect All Clients")
    @Summary("Disconnects all connected SSE clients")
    public String disconnectAllClients(@Connection SSEConnection connection) {
        
        LOGGER.info("Disconnecting all clients");
        
        try {
            int clientCount = connection.getConnectedClientCount();
            
            // Disconnect all clients
            connection.disconnectAllClients();
            
            String message = String.format("All clients disconnected successfully. Total disconnected: %d", clientCount);
            LOGGER.info(message);
            return message;
            
        } catch (Exception e) {
            LOGGER.error("Failed to disconnect all clients", e);
            throw new RuntimeException("Failed to disconnect all clients: " + e.getMessage(), e);
        }
    }
}
