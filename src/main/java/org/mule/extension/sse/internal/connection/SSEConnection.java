package org.mule.extension.sse.internal.connection;

import org.mule.runtime.http.api.HttpService;
import org.mule.runtime.http.api.server.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.io.IOException;

/**
 * SSE Connection
 * 
 * Represents an active SSE connection that can manage multiple client connections
 * and broadcast events to all connected clients.
 * 
 * @author MuleSoft
 * @version 1.0.0
 */
public class SSEConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(SSEConnection.class);

    private final HttpService httpService;
    private final String listenerConfig;
    private boolean initialized;
    private HttpServer httpServer;
    
    // Thread-safe collection to store connected clients
    private final Map<String, SSEClientConnection> connectedClients;
    
    // List to store event listeners
    private final List<SSEEventListener> eventListeners;

    /**
     * Creates a new SSE connection
     * 
     * @param httpService the HTTP service for looking up the HTTP server
     * @param listenerConfig the HTTP listener config name
     */
    public SSEConnection(HttpService httpService, String listenerConfig) {
        this.httpService = httpService;
        this.listenerConfig = listenerConfig;
        this.initialized = false;
        this.connectedClients = new ConcurrentHashMap<>();
        this.eventListeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Initializes the SSE connection
     * 
     * @throws IOException if initialization fails
     */
    public void initialize() throws IOException {
        if (initialized) {
            LOGGER.warn("SSE connection already initialized");
            return;
        }

        LOGGER.info("Initializing SSE connection - Listener Config: {}", listenerConfig);
        
        try {
            // Get the HTTP server from the referenced listener config
            if (listenerConfig != null && !listenerConfig.isEmpty()) {
                httpServer = httpService.getServerFactory().lookup(listenerConfig);
                
                if (httpServer == null) {
                    throw new IOException("HTTP Listener config '" + listenerConfig + "' not found. " +
                        "Make sure the http:listener-config exists and is started before this SSE connection.");
                }
                
                LOGGER.info("HTTP server obtained from listener config: {}", listenerConfig);
            } else {
                LOGGER.warn("No listener config specified, HTTP server will be null");
            }
            
            // Initialize connection resources
            this.initialized = true;
            
            LOGGER.info("SSE connection initialized successfully");
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to initialize SSE connection: " + e.getMessage(), e);
        }
    }

    /**
     * Registers a new client connection
     * 
     * @param clientId the unique client identifier
     * @param clientConnection the client connection
     */
    public void registerClient(String clientId, SSEClientConnection clientConnection) {
        connectedClients.put(clientId, clientConnection);
        LOGGER.info("Client registered: {} (Total clients: {})", clientId, connectedClients.size());
    }

    /**
     * Unregisters a client connection
     * 
     * @param clientId the unique client identifier
     */
    public void unregisterClient(String clientId) {
        SSEClientConnection removed = connectedClients.remove(clientId);
        if (removed != null) {
            try {
                removed.close();
            } catch (Exception e) {
                LOGGER.error("Error closing client connection: {}", clientId, e);
            }
            LOGGER.info("Client unregistered and closed: {} (Total clients: {})", clientId, connectedClients.size());
        } else {
            LOGGER.warn("Attempted to unregister unknown client: {}", clientId);
        }
    }

    /**
     * Disconnects all connected clients
     */
    public void disconnectAllClients() {
        LOGGER.info("Disconnecting all clients (Total: {})", connectedClients.size());
        
        // Close all client connections
        connectedClients.forEach((clientId, connection) -> {
            try {
                connection.close();
                LOGGER.debug("Client {} disconnected", clientId);
            } catch (Exception e) {
                LOGGER.error("Error closing client connection: {}", clientId, e);
            }
        });
        
        connectedClients.clear();
        LOGGER.info("All clients disconnected");
    }

    /**
     * Broadcasts an event to all connected clients
     * 
     * @param eventName the event name
     * @param eventData the event data
     */
    public void broadcastEvent(String eventName, String eventData) {
        LOGGER.debug("Broadcasting event '{}' to {} clients", eventName, connectedClients.size());
        
        connectedClients.forEach((clientId, connection) -> {
            try {
                connection.sendEvent(eventName, eventData);
            } catch (Exception e) {
                LOGGER.error("Failed to send event to client: {}", clientId, e);
                // Remove failed client
                unregisterClient(clientId);
            }
        });
    }

    /**
     * Adds an event listener
     * 
     * @param listener the event listener
     */
    public void addEventListener(SSEEventListener listener) {
        eventListeners.add(listener);
        LOGGER.debug("Event listener added (Total: {})", eventListeners.size());
    }

    /**
     * Removes an event listener
     * 
     * @param listener the event listener
     */
    public void removeEventListener(SSEEventListener listener) {
        eventListeners.remove(listener);
        LOGGER.debug("Event listener removed (Total: {})", eventListeners.size());
    }

    /**
     * Notifies all event listeners of a new event
     * 
     * @param eventName the event name
     * @param eventData the event data
     */
    public void notifyEventListeners(String eventName, String eventData) {
        for (SSEEventListener listener : eventListeners) {
            try {
                listener.onEvent(eventName, eventData);
            } catch (Exception e) {
                LOGGER.error("Error notifying event listener", e);
            }
        }
    }

    /**
     * Checks if the connection is valid
     * 
     * @return true if the connection is valid, false otherwise
     */
    public boolean isValid() {
        return initialized;
    }

    /**
     * Gets the number of connected clients
     * 
     * @return the number of connected clients
     */
    public int getConnectedClientCount() {
        return connectedClients.size();
    }

    /**
     * Closes the SSE connection and cleans up resources
     */
    public void close() {
        LOGGER.info("Closing SSE connection");
        
        // Close all client connections
        connectedClients.forEach((clientId, connection) -> {
            try {
                connection.close();
            } catch (Exception e) {
                LOGGER.error("Error closing client connection: {}", clientId, e);
            }
        });
        
        connectedClients.clear();
        eventListeners.clear();
        initialized = false;
        
        LOGGER.info("SSE connection closed");
    }

    /**
     * Gets the HTTP server
     * 
     * @return the HTTP server
     */
    public HttpServer getHttpServer() {
        return httpServer;
    }

    /**
     * Gets the listener config name
     * 
     * @return the listener config name
     */
    public String getListenerConfig() {
        return listenerConfig;
    }
}
