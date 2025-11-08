# SSE Connector - PlantUML Documentation

This directory contains detailed PlantUML diagrams explaining the SSE Connector's architecture and operation flows.

## Architecture Diagrams

### 1. architecture-overview.puml
**High-level architecture showing all components and their relationships**

Components covered:
- Configuration Layer (SSEConfiguration, SSEConnectionProvider)
- Connection Layer (SSEConnection, SSEClientConnection)
- Source Layer (SSEServerSource, SSEClientSource)
- Operations Layer (SSEOperations)
- HTTP Integration (HttpService, HttpServer)
- External Systems (SSE Clients, Mule Flows, HTTP Listener)

## Flow Diagrams

### 2. connection-flow.puml
**Detailed sequence diagram of SSE client connection establishment**

Shows:
- Client initiates HTTP GET request with text/event-stream accept header
- Server creates piped streams (PipedInputStream ↔ PipedOutputStream)
- Client registration in SSEConnection registry
- Initial comment sent to establish stream
- HTTP response with streaming entity
- Connection maintained for event delivery

Key concepts:
- PipedOutputStream writes data
- PipedInputStream feeds HTTP response stream
- Client receives continuous stream
- Connection stays open until explicitly closed

### 3. complete-data-flow.puml
**End-to-end data flow from connection to disconnection**

Four phases:
1. **Client Connection** - Initial handshake and stream setup
2. **Sending Events** - Single event delivery mechanism
3. **Broadcasting** - Event delivery to multiple clients
4. **Disconnection** - Graceful connection termination

Shows the complete lifecycle and data path through all components.

## Operation Diagrams

### 4. send-event-operation.puml
**sendEvent operation flow**

Purpose: Send named events to all connected clients

Process:
1. Mule flow calls sendEvent(connection, eventName, eventData)
2. Operation validates input and broadcasts to SSEConnection
3. SSEConnection iterates through all registered clients
4. Each SSEClientConnection formats SSE message
5. Message written to PipedOutputStream
6. Data flows through pipe to HTTP stream
7. All connected clients receive the event

Format:
```
event: notification
data: Hello World

```

### 5. send-custom-event-operation.puml
**sendCustomEvent operation flow**

Purpose: Send events with additional metadata (event ID)

Features:
- Optional event ID parameter for client-side tracking
- Wraps data with ID in JSON format: `{"id":"event-001","data":{...}}`
- Useful for event deduplication and tracking

Process similar to sendEvent but with data transformation.

### 6. broadcast-message-operation.puml
**broadcastMessage operation flow**

Purpose: Send simple messages without event name

Difference from sendEvent:
- No event name (empty string)
- Client receives as default "message" event
- Simpler format: just `data:` line

Format:
```
data: System maintenance in 5 minutes

```

### 7. disconnect-client-operation.puml
**disconnectClient operation flow**

Purpose: Disconnect a specific client by ID

Process:
1. Mule flow provides client ID to disconnect
2. Operation validates ID and calls SSEConnection.unregisterClient()
3. SSEConnection removes client from registry
4. SSEClientConnection.close() is called
5. Close event sent: `event: connectionClosed`
6. PipedOutputStream closed
7. HTTP stream terminates
8. Client receives close event before disconnection

Includes logging at each step for debugging.

### 8. disconnect-all-operation.puml
**disconnectAllClients operation flow**

Purpose: Disconnect all connected clients at once

Process:
1. Get current client count for response message
2. Iterate through all clients in registry
3. Call close() on each SSEClientConnection
4. Send connectionClosed event to each
5. Clear entire registry
6. Return total disconnected count

Useful for:
- Server shutdown
- Maintenance mode
- Clearing all connections

### 9. get-connected-clients-operation.puml
**getConnectedClients operation flow**

Purpose: Query current number of connected clients

Simple operation:
1. Calls SSEConnection.getConnectedClientCount()
2. Returns size of connectedClients map
3. No side effects

Use cases:
- Monitoring
- Conditional logic (check if any clients connected before broadcasting)
- Metrics collection

## Key Technical Details

### Streaming Mechanism
- **PipedOutputStream**: Write end where SSE events are written
- **PipedInputStream**: Read end that feeds HTTP response
- **Buffer Size**: 8192 bytes for optimal performance
- **Thread-Safe**: ConcurrentHashMap for client registry

### SSE Message Format
Standard SSE format:
```
event: eventName
data: eventData

```
- Lines starting with `:` are comments (ignored by client)
- Blank line (`\n\n`) ends the message
- Multiple `data:` lines allowed for multi-line content

### Error Handling
- Failed clients automatically removed from registry
- Errors logged but don't stop broadcasting to other clients
- Connection failures trigger cleanup

### HTTP Headers
Required for SSE:
- `Content-Type: text/event-stream; charset=UTF-8`
- `Cache-Control: no-cache`
- `Connection: keep-alive`
- `Access-Control-Allow-Origin: *` (for CORS)
- `X-Accel-Buffering: no` (disable proxy buffering)

## Viewing Diagrams

To render these PlantUML diagrams:

1. **Online**: Use [PlantUML Online Server](http://www.plantuml.com/plantuml/uml/)
2. **VS Code**: Install "PlantUML" extension
3. **Command Line**: Install PlantUML jar and run:
   ```bash
   java -jar plantuml.jar *.puml
   ```
4. **IDE Integration**: Most IDEs have PlantUML plugins

## Diagram Legend

- **Rectangles**: Components/Classes
- **Arrows**: Data flow or method calls
- **Actors**: External entities (clients, flows)
- **Activate/Deactivate**: Component lifecycle during operation
- **Notes**: Additional context and explanations
- **Alt/Loop**: Conditional logic and iterations
