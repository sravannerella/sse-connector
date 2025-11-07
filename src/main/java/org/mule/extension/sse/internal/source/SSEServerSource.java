package org.mule.extension.sse.internal.source;

import org.mule.runtime.api.connection.ConnectionProvider;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.runtime.source.Source;
import org.mule.runtime.extension.api.runtime.source.SourceCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mule.extension.sse.internal.connection.SSEConnection;
import org.mule.extension.sse.internal.connection.SSEClientConnection;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSE Server Source
 * 
 * This source acts as an SSE server that allows clients to connect and receive events.
 * It handles multiple client connections efficiently and provides keep-alive functionality.
 * 
 * Usage example:
 * <code>
 * <sse:sse-server-listener config-ref="SSE_Config" path="/events">
 *   <sse:response>
 *     <sse:body>#[payload]</sse:body>
 *   </sse:response>
 * </sse:sse-server-listener>
 * </code>
 * 
 * @author MuleSoft
 * @version 1.0.0
 */
@Alias("sse-server-listener")
@MediaType(value = MediaType.TEXT_PLAIN, strict = false)
public class SSEServerSource extends Source<String, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SSEServerSource.class);

    /**
     * SSE connection instance
     */
    @Connection
    private ConnectionProvider<SSEConnection> connectionProvider;

    /**
     * The endpoint path for SSE connections
     */
    @Parameter
    @Optional(defaultValue = "/events")
    @DisplayName("Endpoint Path")
    private String path;

    /**
     * Keep-alive interval in seconds
     */
    @Parameter
    @Optional(defaultValue = "30")
    @DisplayName("Keep-Alive Interval (seconds)")
    private int keepAliveInterval;

    /**
     * Maximum number of concurrent clients
     */
    @Parameter
    @Optional(defaultValue = "100")
    @DisplayName("Max Concurrent Clients")
    private int maxConcurrentClients;

    private SSEConnection sseConnection;
    private ScheduledExecutorService keepAliveScheduler;

    /**
     * Callback fired when the source starts
     * 
     * @throws MuleException if the source fails to start
     */
    @Override
    public void onStart(SourceCallback<String, Void> sourceCallback) throws MuleException {
        LOGGER.info("Starting SSE Server Source on path: {}", path);

        try {
            // Get the connection from the connection provider
            sseConnection = connectionProvider.connect();

            // Start keep-alive scheduler
            startKeepAliveScheduler();

            LOGGER.info("SSE Server Source started successfully. Waiting for client connections...");
            
            // In a real implementation, you would start an HTTP server here
            // that handles SSE connections on the specified path
            
        } catch (Exception e) {
            LOGGER.error("Failed to start SSE Server Source", e);
            throw new RuntimeException("Failed to start SSE Server Source: " + e.getMessage(), e);
        }
    }

    /**
     * Callback fired when the source stops
     */
    @Override
    public void onStop() {
        LOGGER.info("Stopping SSE Server Source");

        if (keepAliveScheduler != null && !keepAliveScheduler.isShutdown()) {
            keepAliveScheduler.shutdown();
            try {
                if (!keepAliveScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    keepAliveScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                keepAliveScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (sseConnection != null) {
            try {
                connectionProvider.disconnect(sseConnection);
            } catch (Exception e) {
                LOGGER.error("Error disconnecting SSE connection", e);
            }
        }

        LOGGER.info("SSE Server Source stopped");
    }

    /**
     * Starts the keep-alive scheduler
     */
    private void startKeepAliveScheduler() {
        keepAliveScheduler = Executors.newSingleThreadScheduledExecutor();
        keepAliveScheduler.scheduleAtFixedRate(() -> {
            try {
                sendKeepAliveToAllClients();
            } catch (Exception e) {
                LOGGER.error("Error sending keep-alive", e);
            }
        }, keepAliveInterval, keepAliveInterval, TimeUnit.SECONDS);

        LOGGER.debug("Keep-alive scheduler started with interval: {} seconds", keepAliveInterval);
    }

    /**
     * Sends keep-alive to all connected clients
     */
    private void sendKeepAliveToAllClients() {
        LOGGER.debug("Sending keep-alive to all clients");
        // Implementation would iterate through all connected clients
        // and send keep-alive messages
    }

    /**
     * Handles a new client connection
     * 
     * @param outputStream the client's output stream
     * @return the client ID
     */
    public String handleNewClient(java.io.OutputStream outputStream) {
        String clientId = UUID.randomUUID().toString();
        
        if (sseConnection.getConnectedClientCount() >= maxConcurrentClients) {
            LOGGER.warn("Maximum concurrent clients reached. Rejecting new client.");
            return null;
        }

        SSEClientConnection clientConnection = new SSEClientConnection(clientId, outputStream);
        sseConnection.registerClient(clientId, clientConnection);
        
        LOGGER.info("New client connected: {}", clientId);
        return clientId;
    }

    /**
     * Handles client disconnection
     * 
     * @param clientId the client ID
     */
    public void handleClientDisconnect(String clientId) {
        if (clientId != null) {
            sseConnection.unregisterClient(clientId);
            LOGGER.info("Client disconnected: {}", clientId);
        }
    }

    /**
     * Broadcasts an event to all connected clients
     * 
     * @param eventName the event name
     * @param eventData the event data
     */
    public void broadcastEvent(String eventName, String eventData) {
        if (sseConnection != null) {
            sseConnection.broadcastEvent(eventName, eventData);
        }
    }

    /**
     * Gets the connection provider
     * 
     * @return the connection provider
     */
    public ConnectionProvider<SSEConnection> getConnectionProvider() {
        return connectionProvider;
    }

    /**
     * Gets the SSE connection
     * 
     * @return the SSE connection
     */
    public SSEConnection getSseConnection() {
        return sseConnection;
    }
}
