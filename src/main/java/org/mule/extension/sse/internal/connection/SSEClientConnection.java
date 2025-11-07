package org.mule.extension.sse.internal.connection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * SSE Client Connection
 * 
 * Represents a single client connection to the SSE server.
 * Handles sending SSE-formatted events to the client.
 * 
 * @author MuleSoft
 * @version 1.0.0
 */
public class SSEClientConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(SSEClientConnection.class);
    private static final String SSE_EVENT_FORMAT = "event: %s\ndata: %s\n\n";
    private static final String SSE_DATA_FORMAT = "data: %s\n\n";

    private final String clientId;
    private final OutputStream outputStream;
    private volatile boolean connected;

    /**
     * Creates a new SSE client connection
     * 
     * @param clientId the unique client identifier
     * @param outputStream the output stream to write events to
     */
    public SSEClientConnection(String clientId, OutputStream outputStream) {
        this.clientId = clientId;
        this.outputStream = outputStream;
        this.connected = true;
    }

    /**
     * Sends an event to the client
     * 
     * @param eventName the event name
     * @param eventData the event data
     * @throws IOException if an I/O error occurs
     */
    public void sendEvent(String eventName, String eventData) throws IOException {
        if (!connected) {
            throw new IOException("Client connection is closed");
        }

        try {
            String message;
            if (eventName != null && !eventName.isEmpty()) {
                message = String.format(SSE_EVENT_FORMAT, eventName, eventData);
            } else {
                message = String.format(SSE_DATA_FORMAT, eventData);
            }

            outputStream.write(message.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            
            LOGGER.debug("Event sent to client {}: {}", clientId, eventName);
        } catch (IOException e) {
            LOGGER.error("Failed to send event to client {}", clientId, e);
            connected = false;
            throw e;
        }
    }

    /**
     * Sends a keep-alive comment to the client
     * 
     * @throws IOException if an I/O error occurs
     */
    public void sendKeepAlive() throws IOException {
        if (!connected) {
            throw new IOException("Client connection is closed");
        }

        try {
            outputStream.write(": keep-alive\n\n".getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            LOGGER.debug("Keep-alive sent to client {}", clientId);
        } catch (IOException e) {
            LOGGER.error("Failed to send keep-alive to client {}", clientId, e);
            connected = false;
            throw e;
        }
    }

    /**
     * Checks if the client is still connected
     * 
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Gets the client ID
     * 
     * @return the client ID
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Closes the client connection
     */
    public void close() {
        if (!connected) {
            LOGGER.debug("Client connection already closed: {}", clientId);
            return;
        }
        
        connected = false;
        try {
            if (outputStream != null) {
                LOGGER.info("Closing output stream for client: {}", clientId);
                
                // Send a final message before closing
                try {
                    String closeMessage = "event: connectionClosed\ndata: Connection closed by server\n\n";
                    outputStream.write(closeMessage.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                } catch (IOException e) {
                    LOGGER.debug("Could not send close message to client: {}", clientId);
                }
                
                outputStream.close();
                LOGGER.info("Client connection closed successfully: {}", clientId);
            }
        } catch (IOException e) {
            LOGGER.error("Error closing client connection: {}", clientId, e);
        }
    }
}
