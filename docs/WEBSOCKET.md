# WebSocket Infrastructure Design

> **Prerequisite document** for [DRIVER.md](./DRIVER.md) (browser automation).
> This covers WebSocket infrastructure only. For the full driver implementation, see DRIVER.md.

## Progress Tracking

| Component | Description | Status |
|-----------|-------------|--------|
| WsFrame | Immutable text/binary frame wrapper | ✅ Complete |
| WsException | Single exception type with cause | ✅ Complete |
| WsClientOptions | Client configuration builder | ✅ Complete |
| WsClient | Netty WebSocket client | ✅ Complete |
| WsClientHandler | Netty handler (public) | ✅ Complete |
| WsServerOptions | Server configuration builder | ⬜ Not started |
| WsServer | Netty WebSocket server | ⬜ Not started |
| WsServerHandler | Netty handler (public) | ⬜ Not started |
| Unit tests | Echo server tests | 🟡 In progress |

**Legend:** ⬜ Not started | 🟡 In progress | ✅ Complete

---

This document defines the design for WebSocket client and server infrastructure in karate-v2.
The implementation will be in the `io.karatelabs.http` package alongside the existing HTTP classes.

## Design Decisions Summary

| Decision | Choice |
|----------|--------|
| Concurrency | Multiple concurrent requests with CompletableFutures |
| Protocol awareness | Protocol-agnostic base (raw frames only) |
| Event handling | Callback listeners (on* methods) |
| Wait logic | CDP implementation handles its own (not in base) |
| Server sessions | Raw callbacks only (subclass manages state) |
| Threading | Dedicated callback executor (not Netty threads) |
| Backpressure | Unbounded queue (document risk) |
| Message ordering | Parallel processing (user handles ordering) |
| Executor scope | Shared global executor with override option |
| Reconnection | No auto-reconnect |
| Exceptions | Single WsException with cause |
| Binary support | Unified WsFrame with type flag (immutable) |
| CDP message API | Fluent message builder |
| Compression | Configurable option (default off) |
| CDP ownership | Exclusive (owns WsClient internally) |
| Global registry | Yes, like HttpServer |
| Close behavior | Complete pending futures exceptionally |
| Constants | Reuse Http class |
| Close frame handling | Server close = error completion |
| Options | Builder/Options class pattern |
| Ping/pong | Automatic handling (configurable interval) |
| Subprotocol | Post-handshake validation |
| CDP scope | Full feature parity with v1 |
| SSL default | Insecure (trust all) |
| Handler visibility | Public classes for extension |
| Listener errors | Log and continue |
| Doc scope | Java implementation only |
| CDP timeout | Per-request with default |
| Server mode | Standalone only |
| Callback executor | Shared worker pool |
| Server path | Configurable (default "/") |
| Listener API | Simple on* methods |

---

## Class Overview

```
io.karatelabs.http
├── WsClient              # WebSocket client
├── WsClientHandler       # Netty handler for client (public)
├── WsServer              # WebSocket server
├── WsServerHandler       # Netty handler for server (public)
├── WsFrame               # Immutable text/binary frame wrapper
├── WsClientOptions       # Client configuration builder
├── WsServerOptions       # Server configuration builder
├── WsException           # Single exception type with cause
└── WsListener            # Functional interface for message callbacks

io.karatelabs.driver (future)
├── CdpClient             # Chrome DevTools Protocol client
├── CdpMessage            # Fluent message builder
└── CdpDriver             # Browser driver implementation
```

---

## WsClientOptions

Configuration for WsClient using builder pattern.

```java
public class WsClientOptions {

    public static WsClientOptions.Builder builder(String uri) {
        return new Builder(uri);
    }

    public static WsClientOptions.Builder builder(URI uri) {
        return new Builder(uri);
    }

    public static class Builder {
        private final URI uri;
        private Map<String, String> headers;
        private boolean compression = false;
        private int maxPayloadSize = Http.MEGABYTE;
        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration pingInterval = Duration.ofSeconds(30);
        private boolean trustAllCerts = true; // insecure default for dev
        private SslContext sslContext;

        public Builder headers(Map<String, String> headers);
        public Builder compression(boolean enabled);
        public Builder maxPayloadSize(int size);
        public Builder connectTimeout(Duration timeout);
        public Builder pingInterval(Duration interval);
        public Builder disablePing();
        public Builder sslContext(SslContext context); // overrides trustAllCerts
        public Builder trustAllCerts(boolean trust);
        public Builder callbackExecutor(ExecutorService executor); // override shared executor

        public WsClientOptions build();
    }

    // Accessors
    public URI getUri();
    public String getHost();
    public int getPort();
    public boolean isSsl();
    public Map<String, String> getHeaders();
    public boolean isCompression();
    public int getMaxPayloadSize();
    public Duration getConnectTimeout();
    public Duration getPingInterval();
    public SslContext getSslContext();
}
```

---

## WsFrame

Immutable wrapper for text and binary WebSocket frames.

```java
public class WsFrame {

    public enum Type { TEXT, BINARY, PING, PONG, CLOSE }

    public static WsFrame text(String content);
    public static WsFrame binary(byte[] content);
    public static WsFrame ping();
    public static WsFrame pong();
    public static WsFrame close();
    public static WsFrame close(int code, String reason);

    public Type getType();
    public boolean isText();
    public boolean isBinary();
    public boolean isPing();
    public boolean isPong();
    public boolean isClose();

    public String getText();           // returns null for non-text
    public byte[] getBytes();          // returns null for non-binary
    public int getCloseCode();         // for close frames
    public String getCloseReason();    // for close frames

    @Override
    public String toString();          // for logging
}
```

---

## WsException

Single exception type with cause differentiation.

```java
public class WsException extends RuntimeException {

    public enum Type {
        CONNECT_FAILED,
        CONNECTION_CLOSED,
        TIMEOUT,
        PROTOCOL_ERROR,
        SEND_FAILED
    }

    private final Type type;

    public WsException(Type type, String message);
    public WsException(Type type, String message, Throwable cause);

    public Type getType();

    public boolean isTimeout();
    public boolean isConnectionClosed();
}
```

---

## WsClient

Protocol-agnostic WebSocket client.

```java
public class WsClient {

    // Static registry for global shutdown
    private static final Set<WsClient> ACTIVE_CLIENTS = ConcurrentHashMap.newKeySet();

    public static void closeAll() {
        // Close all active clients
    }

    // Factory methods
    public static WsClient connect(String uri);
    public static WsClient connect(URI uri);
    public static WsClient connect(WsClientOptions options);

    // Connection state
    public boolean isOpen();
    public URI getUri();
    public String getNegotiatedSubprotocol(); // post-handshake validation

    // Sending frames (fire and forget)
    public void send(String text);
    public void send(byte[] binary);
    public void send(WsFrame frame);

    // Sending with response tracking (for protocols with correlation)
    // Returns future that caller can use for correlation
    public CompletableFuture<Void> sendAsync(WsFrame frame);

    // Listener registration (simple on* methods)
    public void onMessage(Consumer<WsFrame> listener);
    public void onClose(Runnable listener);
    public void onError(Consumer<Throwable> listener);

    // Lifecycle
    public void close();                    // graceful close
    public void closeNow();                 // immediate close
    public void waitSync();                 // block until closed

    // Ping (automatic by default, but manual available)
    public void ping();
}
```

### Threading Model

```
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│   Netty I/O     │      │  Callback Pool  │      │   User Code     │
│   Event Loop    │──────│  (Dedicated)    │──────│   (Callbacks)   │
│                 │      │                 │      │                 │
│ - Read frames   │      │ - onMessage()   │      │ - Process msgs  │
│ - Write frames  │      │ - onClose()     │      │ - MAY block     │
│ - Ping/pong     │      │ - onError()     │      │                 │
└─────────────────┘      └─────────────────┘      └─────────────────┘
```

**Important threading details:**

1. **Dedicated callback executor**: A shared static `ExecutorService` (cached thread pool) handles all callbacks. This isolates user code from Netty I/O threads, so callbacks can safely block.

2. **Unbounded queue**: Messages queue up without limit. If callbacks are slow, memory usage grows. Document this risk - users should process quickly or implement their own throttling.

3. **Parallel processing**: Multiple callback threads may process messages concurrently. Messages may be processed out of order. Users must handle ordering if required.

4. **Override option**: `WsClientOptions.callbackExecutor(executor)` allows custom executor.

```java
// Shared callback executor (static)
private static final ExecutorService CALLBACK_EXECUTOR =
    Executors.newCachedThreadPool(daemonThreadFactory("ws-callback-"));

// In WsClient
public static void shutdownCallbackExecutor() {
    CALLBACK_EXECUTOR.shutdown();
}
```

### Error Handling

- Listener exceptions are caught, logged, and execution continues
- Connection errors fail pending futures with WsException
- Server-initiated close completes pending futures with WsException(CONNECTION_CLOSED)

---

## WsClientHandler

Public Netty handler for extension.

```java
public class WsClientHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    protected final WsClient client;

    public WsClientHandler(WsClient client, WebSocketClientHandshaker handshaker);

    // For subclass extension
    protected void onTextFrame(ChannelHandlerContext ctx, TextWebSocketFrame frame);
    protected void onBinaryFrame(ChannelHandlerContext ctx, BinaryWebSocketFrame frame);
    protected void onPongFrame(ChannelHandlerContext ctx, PongWebSocketFrame frame);
    protected void onCloseFrame(ChannelHandlerContext ctx, CloseWebSocketFrame frame);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame);

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause);
}
```

---

## WsServerOptions

Configuration for WsServer.

```java
public class WsServerOptions {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int port = 0;                    // 0 = ephemeral
        private String path = "/";               // configurable, default root
        private boolean compression = false;
        private int maxPayloadSize = Http.MEGABYTE;
        private SslContext sslContext;

        public Builder port(int port);
        public Builder path(String path);
        public Builder compression(boolean enabled);
        public Builder maxPayloadSize(int size);
        public Builder sslContext(SslContext context);

        public WsServerOptions build();
    }
}
```

---

## WsServer

Standalone WebSocket server with raw callbacks.

```java
public class WsServer {

    // Static registry for global shutdown
    private static final Set<WsServer> ACTIVE_SERVERS = ConcurrentHashMap.newKeySet();

    public static void shutdownAll() {
        // Shutdown all active servers
    }

    // Factory methods
    public static WsServer start(int port, Consumer<WsServerConnection> onConnect);
    public static WsServer start(WsServerOptions options, Consumer<WsServerConnection> onConnect);

    // Server info
    public int getPort();
    public boolean isSsl();

    // Lifecycle (matches HttpServer pattern)
    public void waitSync();
    public void stopAndWait();
    public void stopAsync();
}

// Represents a connected client session
public interface WsServerConnection {
    String getId();                      // channel ID
    void send(String text);
    void send(byte[] binary);
    void send(WsFrame frame);
    void close();

    void onMessage(Consumer<WsFrame> listener);
    void onClose(Runnable listener);
}
```

---

## WsServerHandler

Public Netty handler for extension.

```java
public class WsServerHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    protected final WsServer server;

    public WsServerHandler(WsServer server);

    // For subclass extension
    protected void onConnect(ChannelHandlerContext ctx);
    protected void onTextFrame(ChannelHandlerContext ctx, TextWebSocketFrame frame);
    protected void onBinaryFrame(ChannelHandlerContext ctx, BinaryWebSocketFrame frame);
    protected void onDisconnect(ChannelHandlerContext ctx);

    @Override
    public void handlerAdded(ChannelHandlerContext ctx);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame);

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx);
}
```

---

## Thread Pool Pattern (from HttpServer)

Both WsClient and WsServer will use the same daemon thread factory pattern as HttpServer:

```java
private static ThreadFactory daemonThreadFactory(String prefix) {
    AtomicInteger counter = new AtomicInteger();
    return r -> {
        Thread t = new Thread(r, prefix + counter.incrementAndGet());
        t.setDaemon(true);
        return t;
    };
}

// In WsClient
bossGroup = new MultiThreadIoEventLoopGroup(1,
    daemonThreadFactory("ws-client-"), NioIoHandler.newFactory());

// In WsServer
bossGroup = new MultiThreadIoEventLoopGroup(1,
    daemonThreadFactory("ws-boss-"), NioIoHandler.newFactory());
workerGroup = new MultiThreadIoEventLoopGroup(
    daemonThreadFactory("ws-worker-"), NioIoHandler.newFactory());
```

This ensures:
- Threads are daemon (JVM can exit)
- Sync and async stop methods work correctly
- Global shutdown works via static registry

---

## Ping/Pong Handling

Automatic keepalive is enabled by default:

```java
// Client sends ping at configured interval
ScheduledFuture<?> pingTask = ctx.executor().scheduleAtFixedRate(
    () -> ctx.writeAndFlush(new PingWebSocketFrame()),
    pingInterval.toMillis(),
    pingInterval.toMillis(),
    TimeUnit.MILLISECONDS
);

// Auto-respond to pings (required by WebSocket spec)
if (frame instanceof PingWebSocketFrame) {
    ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
}
```

---

## Usage Examples

### Simple Client

```java
WsClient client = WsClient.connect("ws://localhost:8080/ws");

client.onMessage(frame -> {
    if (frame.isText()) {
        System.out.println("Received: " + frame.getText());
    }
});

client.onClose(() -> System.out.println("Disconnected"));

client.send("Hello, server!");

// Later
client.close();
```

### Client with Options

```java
WsClientOptions options = WsClientOptions.builder("wss://secure.example.com/api")
    .headers(Map.of("Authorization", "Bearer token"))
    .compression(true)
    .connectTimeout(Duration.ofSeconds(10))
    .pingInterval(Duration.ofSeconds(15))
    .build();

WsClient client = WsClient.connect(options);
```

### Simple Server

```java
WsServer server = WsServer.start(8080, connection -> {
    System.out.println("Client connected: " + connection.getId());

    connection.onMessage(frame -> {
        // Echo back
        connection.send(frame);
    });

    connection.onClose(() -> {
        System.out.println("Client disconnected: " + connection.getId());
    });
});

System.out.println("Server started on port: " + server.getPort());

// Block until shutdown
server.waitSync();
```

### Server with Options

```java
WsServerOptions options = WsServerOptions.builder()
    .port(0)  // ephemeral port
    .path("/ws")
    .compression(true)
    .build();

WsServer server = WsServer.start(options, connection -> {
    // handle connection
});
```

---

## CDP Client Design (Future: driver package)

The Chrome DevTools Protocol client will be built on top of WsClient.

### CdpMessage

Fluent builder pattern similar to v1 DevToolsMessage:

```java
public class CdpMessage {

    private final CdpClient client;
    private final int id;
    private final String method;
    private Map<String, Object> params;
    private Duration timeout;

    CdpMessage(CdpClient client, String method) {
        this.client = client;
        this.id = client.nextId();
        this.method = method;
    }

    public CdpMessage param(String key, Object value);
    public CdpMessage params(Map<String, Object> params);
    public CdpMessage timeout(Duration timeout);

    // Blocking send with response
    public CdpResponse send();

    // Async send
    public CompletableFuture<CdpResponse> sendAsync();

    // Fire and forget
    public void sendWithoutWaiting();

    // For request/response correlation
    public int getId();
    public String getMethod();
    public Map<String, Object> toMap();
}
```

### CdpClient

```java
public class CdpClient {

    private final WsClient ws;
    private final AtomicInteger idGenerator = new AtomicInteger();
    private final ConcurrentHashMap<Integer, CompletableFuture<CdpResponse>> pending;
    private final Duration defaultTimeout;

    public static CdpClient connect(String webSocketUrl);
    public static CdpClient connect(String webSocketUrl, Duration defaultTimeout);

    public int nextId();

    // Create message builder
    public CdpMessage method(String method);

    // Event subscription
    public void on(String eventName, Consumer<CdpEvent> handler);
    public void off(String eventName, Consumer<CdpEvent> handler);

    // Lifecycle
    public void close();
    public boolean isOpen();
}
```

### CdpResponse

```java
public class CdpResponse {

    private final int id;
    private final Map<String, Object> result;
    private final Map<String, Object> error;

    public int getId();
    public boolean isError();
    public Map<String, Object> getError();

    // Value extraction with JSONPath
    public <T> T get(String path);
    public <T> T getResult(String path);
    public String getResultAsString(String path);
    public Integer getResultAsInt(String path);
}
```

### CDP Usage Example

```java
CdpClient cdp = CdpClient.connect(webSocketUrl, Duration.ofSeconds(30));

// Subscribe to events
cdp.on("Page.loadEventFired", event -> {
    System.out.println("Page loaded");
});

// Enable domains
cdp.method("Page.enable").send();
cdp.method("Runtime.enable").send();

// Navigate
CdpResponse response = cdp.method("Page.navigate")
    .param("url", "https://example.com")
    .timeout(Duration.ofSeconds(60))
    .send();

String frameId = response.getResult("frameId");

// Execute script
CdpResponse evalResult = cdp.method("Runtime.evaluate")
    .param("expression", "document.title")
    .param("returnByValue", true)
    .send();

String title = evalResult.getResult("result.value");

cdp.close();
```

### CDP Wait Conditions

Complex wait logic (like waiting for all frames to load) is implemented in the CDP/driver layer, not the base WsClient. This keeps the base protocol-agnostic while allowing domain-specific logic:

```java
// In CdpDriver or similar
public void waitForAllFramesLoaded(Duration timeout) {
    CompletableFuture<Void> future = new CompletableFuture<>();

    Consumer<CdpEvent> handler = event -> {
        if (domContentEventFired && framesStillLoading.isEmpty()) {
            future.complete(null);
        }
    };

    cdp.on("Page.domContentEventFired", handler);
    cdp.on("Page.frameStoppedLoading", handler);

    try {
        future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } finally {
        cdp.off("Page.domContentEventFired", handler);
        cdp.off("Page.frameStoppedLoading", handler);
    }
}
```

---

## Implementation Phases

### Phase 1: Core WebSocket Infrastructure
1. WsFrame, WsException
2. WsOptions, WsServerOptions
3. WsClient with handlers
4. WsServer with handlers
5. Unit tests with echo server

### Phase 2: CDP Client
1. CdpMessage, CdpResponse, CdpEvent
2. CdpClient with correlation and event routing
3. Integration tests with Chrome

### Phase 3: CDP Driver (v1 feature parity)
1. CdpDriver implementing Driver interface
2. Navigation, scripting, screenshots
3. Frame handling, dialogs
4. Request interception and mocking
5. V1 compatibility tests

---

## Files to Create

```
karate-core/src/main/java/io/karatelabs/http/
├── WsClient.java
├── WsClientHandler.java
├── WsServer.java
├── WsServerHandler.java
├── WsServerConnection.java
├── WsFrame.java
├── WsClientOptions.java
├── WsServerOptions.java
├── WsException.java
└── WsListener.java (optional, may use Consumer<WsFrame> directly)

karate-core/src/test/java/io/karatelabs/http/
├── WsClientTest.java
├── WsServerTest.java
└── WsEchoTest.java (integration test)

karate-core/src/main/java/io/karatelabs/driver/ (Phase 2-3)
├── CdpClient.java
├── CdpMessage.java
├── CdpResponse.java
├── CdpEvent.java
└── CdpDriver.java
```

---

## Reference Implementations

- `HttpServer.java` - Thread pool pattern, shutdown registry
- `XpServer.java` - WebSocket server with Netty
- `XpClient.java` - WebSocket client with Netty
- `WsClient.java` (karate-websocket) - Client options, frame handling
- `DevToolsDriver.java` - CDP message patterns (what to improve)
- `DevToolsWait.java` - Wait pattern (to be redesigned with CompletableFuture)
