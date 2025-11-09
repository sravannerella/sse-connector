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
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.runtime.extension.api.runtime.source.Source;
import org.mule.runtime.extension.api.runtime.source.SourceCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mule.extension.sse.internal.connection.SSEConnection;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE Client Source
 * 
 * This source connects to a remote SSE endpoint and listens for events.
 * It supports automatic reconnection and can limit the number of events to receive.
 * 
 * Usage example:
 * <code>
 * <sse:sse-client-listener config-ref="SSE_Config" url="http://example.com/events">
 *   <sse:response>
 *     <sse:body>#[payload]</sse:body>
 *   </sse:response>
 * </sse:sse-client-listener>
 * </code>
 * 
 * @author MuleSoft
 * @version 1.0.0
 */
@Alias("sse-client-listener")
@MediaType(value = MediaType.TEXT_PLAIN, strict = false)
public class SSEClientSource extends Source<String, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SSEClientSource.class);

    /**
     * SSE connection instance
     */
    @Connection
    private ConnectionProvider<SSEConnection> connectionProvider;

    /**
     * The URL of the SSE endpoint to connect to
     */
    @Parameter
    @DisplayName("SSE Endpoint URL")
    @Summary("The URL of the remote SSE endpoint to listen to")
    private String url;

    /**
     * Number of events to listen for before stopping (optional)
     */
    @Parameter
    @Optional
    @DisplayName("Event Count")
    @Summary("Number of events to receive before stopping. Leave empty for unlimited.")
    private Integer eventCount;

    /**
     * Reconnection interval in seconds
     */
    @Parameter
    @Optional(defaultValue = "5")
    @DisplayName("Reconnect Interval (seconds)")
    @Summary("Time to wait before attempting to reconnect after a disconnect")
    private int reconnectInterval;

    /**
     * Connection timeout in milliseconds
     */
    @Parameter
    @Optional(defaultValue = "30000")
    @DisplayName("Connection Timeout (ms)")
    @Summary("Connection timeout in milliseconds")
    private int connectionTimeout;

    /**
     * Enable automatic reconnection
     */
    @Parameter
    @Optional(defaultValue = "false")
    @DisplayName("Auto Reconnect")
    @Summary("Automatically reconnect when connection is lost")
    private boolean autoReconnect;

    private SSEConnection sseConnection;
    private AtomicBoolean running;
    private AtomicInteger receivedEventCount;
    private Thread listenerThread;

    /**
     * Callback fired when the source starts
     * 
     * @throws MuleException if the source fails to start
     */
    @Override
    public void onStart(SourceCallback<String, Void> sourceCallback) throws MuleException {
        LOGGER.debug("Starting SSE Client Source - URL: {}", url);

        running = new AtomicBoolean(true);
        receivedEventCount = new AtomicInteger(0);

        try {
            // Get the connection from the connection provider
            sseConnection = connectionProvider.connect();

            // Start the listener thread
            listenerThread = new Thread(() -> {
                while (running.get()) {
                    try {
                        connectAndListen(sourceCallback);
                    } catch (Exception e) {
                        LOGGER.error("Error in SSE client listener", e);
                        
                        if (running.get() && autoReconnect) {
                            LOGGER.debug("Reconnecting in {} seconds...", reconnectInterval);
                            try {
                                Thread.sleep(reconnectInterval * 1000L);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        } else {
                            LOGGER.info("Auto-reconnect disabled. Stopping listener.");
                            running.set(false);
                            break;
                        }
                    }
                }
            });

            listenerThread.setName("SSE-Client-Listener-" + url);
            listenerThread.start();

            LOGGER.debug("SSE Client Source started successfully");
            
        } catch (Exception e) {
            LOGGER.error("Failed to start SSE Client Source", e);
            throw new RuntimeException("Failed to start SSE Client Source: " + e.getMessage(), e);
        }
    }

    /**
     * Callback fired when the source stops
     */
    @Override
    public void onStop() {
        LOGGER.debug("Stopping SSE Client Source");

        running.set(false);

        if (listenerThread != null && listenerThread.isAlive()) {
            listenerThread.interrupt();
            try {
                listenerThread.join(5000);
            } catch (InterruptedException e) {
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

        LOGGER.debug("SSE Client Source stopped. Total events received: {}", receivedEventCount.get());
    }

    /**
     * Connects to the SSE endpoint and listens for events
     * 
     * @param sourceCallback the callback to handle received events
     * @throws Exception if an error occurs
     */
    private void connectAndListen(SourceCallback<String, Void> sourceCallback) throws Exception {
        LOGGER.debug("Connecting to SSE endpoint: {}", url);

        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            URL endpoint = new URL(url);
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setConnectTimeout(connectionTimeout);
            connection.setReadTimeout(0); // Infinite read timeout for SSE

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new Exception("Failed to connect to SSE endpoint. Response code: " + responseCode);
            }

            LOGGER.info("Connected to SSE endpoint successfully");

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            
            StringBuilder eventData = new StringBuilder();
            String eventName = null;
            String line;

            while (running.get() && (line = reader.readLine()) != null) {
                
                // Check if we've reached the event count limit
                if (eventCount != null && receivedEventCount.get() >= eventCount) {
                    LOGGER.debug("Reached event count limit: {}", eventCount);
                    running.set(false);
                    break;
                }

                // Empty line indicates end of event
                if (line.isEmpty()) {
                    if (eventData.length() > 0) {
                        String data = eventData.toString();
                        String event = eventName != null ? eventName : "message";
                        
                        // Process the event
                        processEvent(sourceCallback, event, data);
                        
                        receivedEventCount.incrementAndGet();
                        
                        // Reset for next event
                        eventData.setLength(0);
                        eventName = null;
                    }
                    continue;
                }

                // Parse SSE fields
                if (line.startsWith("event:")) {
                    eventName = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    if (eventData.length() > 0) {
                        eventData.append("\n");
                    }
                    eventData.append(line.substring(5).trim());
                } else if (line.startsWith(":")) {
                    // Comment line - ignore
                    LOGGER.debug("Received keep-alive comment");
                } else if (line.startsWith("id:")) {
                    // Event ID - can be used for reconnection
                    String eventId = line.substring(3).trim();
                    LOGGER.debug("Event ID: {}", eventId);
                } else if (line.startsWith("retry:")) {
                    // Retry interval - can be used to update reconnect interval
                    String retry = line.substring(6).trim();
                    LOGGER.debug("Server suggested retry interval: {}", retry);
                }
            }
            
            // If we exit the loop because readLine returned null (connection closed by server)
            // and autoReconnect is disabled, stop the listener
            if (!autoReconnect) {
                LOGGER.info("Server closed connection and auto-reconnect is disabled. Stopping listener.");
                running.set(false);
            } else {
                LOGGER.debug("Connection closed. Will attempt to reconnect.");
            }

        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    LOGGER.error("Error closing reader", e);
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Processes a received event
     * 
     * @param sourceCallback the callback to handle the event
     * @param eventName the event name
     * @param eventData the event data
     */
    private void processEvent(SourceCallback<String, Void> sourceCallback, String eventName, String eventData) {
        LOGGER.debug("Received event '{}': {}", eventName, eventData);

        try {
            // Notify the SSE connection's event listeners
            if (sseConnection != null) {
                sseConnection.notifyEventListeners(eventName, eventData);
            }

            // Create a result and pass it to the source callback
            Result<String, Void> result = Result.<String, Void>builder()
                .output(eventData)
                .build();

            sourceCallback.handle(result);
            
            LOGGER.debug("Event processed successfully");
            
        } catch (Exception e) {
            LOGGER.error("Error processing event", e);
        }
    }

    /**
     * Gets the URL
     * 
     * @return the SSE endpoint URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * Gets the event count limit
     * 
     * @return the event count limit
     */
    public Integer getEventCount() {
        return eventCount;
    }

    /**
     * Gets the received event count
     * 
     * @return the number of events received
     */
    public int getReceivedEventCount() {
        return receivedEventCount.get();
    }
}
