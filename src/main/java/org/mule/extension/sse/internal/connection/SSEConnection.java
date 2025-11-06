package org.mule.extension.sse.internal.connection;

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

    private final String serverHost;
    private final int serverPort;
    private final String basePath;
    private boolean initialized;
    
    // Thread-safe collection to store connected clients
    private final Map<String, SSEClientConnection> connectedClients;
    
    // List to store event listeners
    private final List<SSEEventListener> eventListeners;

    /**
     * Creates a new SSE connection
     * 
     * @param serverHost the server host
     * @param serverPort the server port
     * @param basePath the base path for the SSE endpoint
     */
    public SSEConnection(String serverHost, int serverPort, String basePath) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.basePath = basePath;
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

        LOGGER.info("Initializing SSE connection on {}:{}{}", serverHost, serverPort, basePath);
        
        // Initialize connection resources
        this.initialized = true;
        
        LOGGER.info("SSE connection initialized successfully");
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
            LOGGER.info("Client unregistered: {} (Total clients: {})", clientId, connectedClients.size());
        }
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
     * Gets the server host
     * 
     * @return the server host
     */
    public String getServerHost() {
        return serverHost;
    }

    /**
     * Gets the server port
     * 
     * @return the server port
     */
    public int getServerPort() {
        return serverPort;
    }

    /**
     * Gets the base path
     * 
     * @return the base path
     */
    public String getBasePath() {
        return basePath;
    }
}
