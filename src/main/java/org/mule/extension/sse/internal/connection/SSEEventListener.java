package org.mule.extension.sse.internal.connection;

/**
 * SSE Event Listener Interface
 * 
 * Defines the contract for objects that want to listen to SSE events.
 * 
 * @author MuleSoft
 * @version 1.0.0
 */
public interface SSEEventListener {

    /**
     * Called when a new SSE event is received
     * 
     * @param eventName the name of the event
     * @param eventData the event data
     */
    void onEvent(String eventName, String eventData);

    /**
     * Called when an error occurs
     * 
     * @param error the error that occurred
     */
    default void onError(Throwable error) {
        // Default implementation does nothing
    }

    /**
     * Called when the connection is established
     */
    default void onConnect() {
        // Default implementation does nothing
    }

    /**
     * Called when the connection is closed
     */
    default void onDisconnect() {
        // Default implementation does nothing
    }
}
