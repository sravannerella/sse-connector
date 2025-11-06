package org.mule.extension.sse.internal;

import org.mule.extension.sse.internal.connection.SSEConnectionProvider;
import org.mule.extension.sse.internal.operation.SSEOperations;
import org.mule.extension.sse.internal.source.SSEClientSource;
import org.mule.extension.sse.internal.source.SSEServerSource;
import org.mule.runtime.extension.api.annotation.Configuration;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.Sources;
import org.mule.runtime.extension.api.annotation.connectivity.ConnectionProviders;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;


/**
 * SSE Configuration
 * 
 * This class represents the configuration for the SSE connector.
 * It defines the operations, sources, and connection providers available.
 * 
 * @author MuleSoft
 * @version 1.0.0
 */
@Configuration(name = "config")
@Operations(SSEOperations.class)
@Sources({SSEServerSource.class, SSEClientSource.class})
@ConnectionProviders(SSEConnectionProvider.class)
public class SSEConfiguration {

    /**
     * Default buffer size for SSE events
     */
    @Parameter
    @Optional(defaultValue = "1024")
    @DisplayName("Event Buffer Size")
    @Summary("The buffer size for SSE event data in bytes")
    private int eventBufferSize;

    /**
     * Maximum number of retry attempts for client connections
     */
    @Parameter
    @Optional(defaultValue = "3")
    @DisplayName("Max Retry Attempts")
    @Summary("Maximum number of connection retry attempts")
    private int maxRetryAttempts;

    /**
     * Default timeout for SSE connections in milliseconds
     */
    @Parameter
    @Optional(defaultValue = "30000")
    @DisplayName("Connection Timeout")
    @Summary("Connection timeout in milliseconds")
    private int connectionTimeout;

    /**
     * Gets the event buffer size
     * 
     * @return the buffer size in bytes
     */
    public int getEventBufferSize() {
        return eventBufferSize;
    }

    /**
     * Gets the maximum retry attempts
     * 
     * @return the maximum number of retry attempts
     */
    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    /**
     * Gets the connection timeout
     * 
     * @return the timeout in milliseconds
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Sets the event buffer size
     * 
     * @param eventBufferSize the buffer size in bytes
     */
    public void setEventBufferSize(int eventBufferSize) {
        this.eventBufferSize = eventBufferSize;
    }

    /**
     * Sets the maximum retry attempts
     * 
     * @param maxRetryAttempts the maximum number of retry attempts
     */
    public void setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }

    /**
     * Sets the connection timeout
     * 
     * @param connectionTimeout the timeout in milliseconds
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }
}
