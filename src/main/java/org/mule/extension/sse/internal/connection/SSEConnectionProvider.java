package org.mule.extension.sse.internal.connection;

import org.mule.runtime.api.connection.CachedConnectionProvider;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.connection.ConnectionValidationResult;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SSE Connection Provider
 * 
 * This class provides connections for the SSE connector.
 * It manages the lifecycle of SSE connections and validates their state.
 * 
 * @author MuleSoft
 * @version 1.0.0
 */
public class SSEConnectionProvider implements CachedConnectionProvider<SSEConnection> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SSEConnectionProvider.class);

    /**
     * Server host for SSE server mode
     */
    @Parameter
    @Optional(defaultValue = "localhost")
    @DisplayName("Server Host")
    @Placement(order = 1)
    private String serverHost;

    /**
     * Server port for SSE server mode
     */
    @Parameter
    @Optional(defaultValue = "8080")
    @DisplayName("Server Port")
    @Placement(order = 2)
    private int serverPort;

    /**
     * Base path for SSE endpoint
     */
    @Parameter
    @Optional(defaultValue = "/events")
    @DisplayName("Base Path")
    @Placement(order = 3)
    private String basePath;

    /**
     * Creates a new SSE connection
     * 
     * @return a new SSEConnection instance
     * @throws ConnectionException if the connection cannot be established
     */
    @Override
    public SSEConnection connect() throws ConnectionException {
        LOGGER.info("Creating SSE connection - Host: {}, Port: {}, Path: {}", 
                    serverHost, serverPort, basePath);
        
        try {
            SSEConnection connection = new SSEConnection(serverHost, serverPort, basePath);
            connection.initialize();
            LOGGER.info("SSE connection created successfully");
            return connection;
        } catch (Exception e) {
            LOGGER.error("Failed to create SSE connection", e);
            throw new ConnectionException("Failed to establish SSE connection: " + e.getMessage(), e);
        }
    }

    /**
     * Disconnects and cleans up the SSE connection
     * 
     * @param connection the connection to disconnect
     */
    @Override
    public void disconnect(SSEConnection connection) {
        LOGGER.info("Disconnecting SSE connection");
        
        if (connection != null) {
            try {
                connection.close();
                LOGGER.info("SSE connection disconnected successfully");
            } catch (Exception e) {
                LOGGER.error("Error while disconnecting SSE connection", e);
            }
        }
    }

    /**
     * Validates the SSE connection
     * 
     * @param connection the connection to validate
     * @return the validation result
     */
    @Override
    public ConnectionValidationResult validate(SSEConnection connection) {
        LOGGER.debug("Validating SSE connection");
        
        if (connection == null) {
            return ConnectionValidationResult.failure("Connection is null", null);
        }

        if (!connection.isValid()) {
            return ConnectionValidationResult.failure("Connection is not valid", null);
        }

        LOGGER.debug("SSE connection is valid");
        return ConnectionValidationResult.success();
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
