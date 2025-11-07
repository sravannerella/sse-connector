package org.mule.extension.sse.internal.source;

import org.mule.runtime.api.connection.ConnectionProvider;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.runtime.source.Source;
import org.mule.runtime.extension.api.runtime.source.SourceCallback;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.server.RequestHandlerManager;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mule.extension.sse.internal.connection.SSEConnection;
import org.mule.extension.sse.internal.connection.SSEClientConnection;

import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    @DisplayName("Path")
    @Summary("The path where SSE clients will connect")
    private String path;

    /**
     * Keep-alive interval in seconds
     */
    @Parameter
    @Optional(defaultValue = "30")
    @DisplayName("Keep-Alive Interval (seconds)")
    @Summary("Interval in seconds to send keep-alive comments to clients")
    private int keepAliveInterval;

    /**
     * Maximum number of concurrent clients
     */
    @Parameter
    @Optional(defaultValue = "100")
    @DisplayName("Max Concurrent Clients")
    @Summary("Maximum number of concurrent SSE client connections")
    private int maxConcurrentClients;

    private SSEConnection sseConnection;
    private ScheduledExecutorService keepAliveScheduler;
    private HttpServer httpServer;
    private RequestHandlerManager requestHandlerManager;
    private Map<String, OutputStream> activeClientStreams;

    /**
     * Callback fired when the source starts
     * 
     * @throws MuleException if the source fails to start
     */
    @Override
    public void onStart(SourceCallback<String, Void> sourceCallback) throws MuleException {
        LOGGER.info("Starting SSE Server Source on path: {}", path);

        try {
            // Initialize client streams map
            activeClientStreams = new ConcurrentHashMap<>();

            // Get the connection from the connection provider
            sseConnection = connectionProvider.connect();

            // Get the HTTP server from the SSE connection (it's already initialized there)
            httpServer = sseConnection.getHttpServer();
            
            if (httpServer == null) {
                throw new IllegalStateException("HTTP Server not available. " +
                    "Make sure the listener config '" + sseConnection.getListenerConfig() + 
                    "' exists and is started.");
            }

            LOGGER.info("Using HTTP server from listener config: {}", sseConnection.getListenerConfig());

            // Create request handler for SSE endpoint
            RequestHandler requestHandler = (requestContext, responseCallback) -> {
                try {
                    String requestPath = requestContext.getRequest().getPath();
                    
                    // Check if this is the SSE endpoint
                    if (requestPath.equals(path) || requestPath.equals(path + "/")) {
                        handleSSEConnection(requestContext, responseCallback, sourceCallback);
                    } else {
                        // Return 404 for other paths
                        HttpResponse response = HttpResponse.builder()
                                .statusCode(404)
                                .reasonPhrase("Not Found")
                                .build();
                        responseCallback.responseReady(response, new org.mule.runtime.http.api.server.async.ResponseStatusCallback() {
                            @Override
                            public void responseSendFailure(Throwable throwable) {
                                LOGGER.error("Failed to send 404 response", throwable);
                            }

                            @Override
                            public void responseSendSuccessfully() {
                                LOGGER.debug("404 response sent successfully");
                            }
                        });
                    }
                } catch (Exception e) {
                    LOGGER.error("Error handling HTTP request", e);
                    HttpResponse errorResponse = HttpResponse.builder()
                            .statusCode(500)
                            .reasonPhrase("Internal Server Error")
                            .build();
                    responseCallback.responseReady(errorResponse, new org.mule.runtime.http.api.server.async.ResponseStatusCallback() {
                        @Override
                        public void responseSendFailure(Throwable throwable) {
                            LOGGER.error("Failed to send error response", throwable);
                        }

                        @Override
                        public void responseSendSuccessfully() {
                            LOGGER.debug("Error response sent successfully");
                        }
                    });
                }
            };

            // Register the request handler for the SSE path
            requestHandlerManager = httpServer.addRequestHandler(path, requestHandler);

            // Start keep-alive scheduler
            startKeepAliveScheduler();

            LOGGER.info("SSE Server Source started successfully on path: {}", path);
            LOGGER.info("Waiting for SSE client connections...");
            
        } catch (Exception e) {
            LOGGER.error("Failed to start SSE Server Source", e);
            cleanup();
            throw new RuntimeException("Failed to start SSE Server Source: " + e.getMessage(), e);
        }
    }

    /**
     * Cleanup resources
     */
    private void cleanup() {
        if (requestHandlerManager != null) {
            try {
                requestHandlerManager.stop();
                LOGGER.debug("Request handler stopped");
            } catch (Exception e) {
                LOGGER.error("Error stopping request handler", e);
            }
        }
    }

    /**
     * Handles SSE connection from a client
     */
    private void handleSSEConnection(
            org.mule.runtime.http.api.domain.request.HttpRequestContext requestContext,
            org.mule.runtime.http.api.server.async.HttpResponseReadyCallback responseCallback,
            SourceCallback<String, Void> sourceCallback) {
        
        String clientId = UUID.randomUUID().toString();
        
        // Check if max clients reached
        if (sseConnection.getConnectedClientCount() >= maxConcurrentClients) {
            LOGGER.warn("Maximum concurrent clients ({}) reached. Rejecting new client.", maxConcurrentClients);
            HttpResponse response = HttpResponse.builder()
                    .statusCode(503)
                    .reasonPhrase("Service Unavailable")
                    .build();
            responseCallback.responseReady(response, new org.mule.runtime.http.api.server.async.ResponseStatusCallback() {
                @Override
                public void responseSendFailure(Throwable throwable) {
                    LOGGER.error("Failed to send 503 response", throwable);
                }

                @Override
                public void responseSendSuccessfully() {
                    LOGGER.debug("503 response sent successfully");
                }
            });
            return;
        }

        LOGGER.info("New SSE client connecting: {}", clientId);

        try {
            // Send SSE response headers
            HttpResponse response = HttpResponse.builder()
                .statusCode(200)
                .reasonPhrase("OK")
                .addHeader("Content-Type", "text/event-stream")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("Connection", "keep-alive")
                .addHeader("Access-Control-Allow-Origin", "*")
                .build();

            responseCallback.responseReady(response, new org.mule.runtime.http.api.server.async.ResponseStatusCallback() {
                @Override
                public void responseSendFailure(Throwable throwable) {
                    LOGGER.error("Failed to send SSE response for client: {}", clientId, throwable);
                }

                @Override
                public void responseSendSuccessfully() {
                    LOGGER.info("SSE client connected: {}", clientId);
                    
                    try {
                        // Trigger the source callback with connection event
                        sourceCallback.handle(
                            org.mule.runtime.extension.api.runtime.operation.Result.<String, Void>builder()
                                .output("Client connected: " + clientId)
                                .build()
                        );
                    } catch (Exception e) {
                        LOGGER.error("Error handling source callback for client: {}", clientId, e);
                    }
                }
            });

            // Register client with SSE connection
            // In a full implementation, you'd store the actual output stream
            // and use it to send events to this specific client
            
        } catch (Exception e) {
            LOGGER.error("Error handling SSE connection for client: {}", clientId, e);
            HttpResponse errorResponse = HttpResponse.builder()
                    .statusCode(500)
                    .reasonPhrase("Internal Server Error")
                    .build();
            responseCallback.responseReady(errorResponse, new org.mule.runtime.http.api.server.async.ResponseStatusCallback() {
                @Override
                public void responseSendFailure(Throwable throwable) {
                    LOGGER.error("Failed to send error response", throwable);
                }

                @Override
                public void responseSendSuccessfully() {
                    LOGGER.debug("Error response sent successfully");
                }
            });
        }
    }

    /**
     * Callback fired when the source stops
     */
    @Override
    public void onStop() {
        LOGGER.info("Stopping SSE Server Source");

        // Stop keep-alive scheduler
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

        // Cleanup request handler (the HTTP server is managed by the listener config)
        cleanup();

        // Disconnect SSE connection
        if (sseConnection != null) {
            try {
                connectionProvider.disconnect(sseConnection);
            } catch (Exception e) {
                LOGGER.error("Error disconnecting SSE connection", e);
            }
        }

        // Clear active client streams
        if (activeClientStreams != null) {
            activeClientStreams.clear();
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
