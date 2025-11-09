package org.mule.extension.sse.internal.connection;

import org.mule.runtime.api.connection.CachedConnectionProvider;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.connection.ConnectionValidationResult;
import org.mule.runtime.api.meta.ExpressionSupport;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.annotation.param.reference.ConfigReference;
import org.mule.runtime.http.api.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

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
     * Injected HTTP Service to create HTTP server
     */
    @Inject
    private HttpService httpService;

    @Parameter
    @Expression(ExpressionSupport.NOT_SUPPORTED)
    @ConfigReference(
        name = "LISTENER_CONFIG",
        namespace = "HTTP"
    )
    @Summary("Reference to the <http:listener-config> used to expose the SSE endpoint")
    @DisplayName("Listener Config")
    @Placement(order = 1)
    private String listenerConfig;

    /**
     * Creates a new SSE connection
     * 
     * @return a new SSEConnection instance
     * @throws ConnectionException if the connection cannot be established
     */
    @Override
    public SSEConnection connect() throws ConnectionException {
        LOGGER.debug("Creating SSE connection - Listener Config: {}", listenerConfig);
        
        try {
            SSEConnection connection = new SSEConnection(httpService, listenerConfig);
            connection.initialize();
            LOGGER.info("SSE connection created successfully for Listener Config: {}", listenerConfig);
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
        LOGGER.debug("Disconnecting SSE connection");
        
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
}
