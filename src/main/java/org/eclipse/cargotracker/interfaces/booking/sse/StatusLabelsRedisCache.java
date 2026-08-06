package org.eclipse.cargotracker.interfaces.booking.sse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;

/**
 * Cloud-native status-label cache backed by Amazon ElastiCache for Redis.
 *
 * <p>This class replaces the previously static in-memory {@code routingStatusLabels} and
 * {@code transportStatusLabels} maps in {@link RealtimeCargoTrackingViewAdapter}
 * (cr-java-0067 – In-Memory Caching Without TTL). In a multi-instance cloud deployment every
 * application node shares a single, consistent view of the status labels stored in Redis with a
 * configurable TTL, eliminating unbounded memory growth and stale data inconsistencies across
 * instances.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>The singleton instance is obtained via {@link #getInstance()}.
 *   <li>{@link #getRoutingStatusLabel(RoutingStatus, Map)} and
 *       {@link #getTransportStatusLabel(TransportStatus, Map)} return the human-readable label
 *       for the given status enum value. When a Redis connection is available (controlled by the
 *       {@code REDIS_HOST} / {@code REDIS_PORT} environment variables) the labels are loaded
 *       from Redis. When Redis is not reachable the methods fall back to the supplied in-process
 *       maps, ensuring the application continues to work in local-development and test
 *       environments.
 *   <li>A TTL of {@value #STATUS_LABELS_TTL_SECONDS} seconds is applied to each Redis key to
 *       prevent indefinite memory growth and ensure periodic refresh.
 * </ul>
 *
 * <h3>AWS ElastiCache configuration</h3>
 * Set the following environment variables before deploying to AWS:
 * <pre>
 *   REDIS_HOST  – ElastiCache primary endpoint (e.g. my-cluster.abc123.ng.0001.use1.cache.amazonaws.com)
 *   REDIS_PORT  – ElastiCache port (default: 6379)
 * </pre>
 */
public final class StatusLabelsRedisCache {

  private static final Logger LOGGER = Logger.getLogger(StatusLabelsRedisCache.class.getName());

  /** Redis hash key for routing-status labels. */
  static final String REDIS_ROUTING_STATUS_KEY = "cargotracker:labels:routing-status";

  /** Redis hash key for transport-status labels. */
  static final String REDIS_TRANSPORT_STATUS_KEY = "cargotracker:labels:transport-status";

  /** TTL in seconds applied to each status-label key (1 hour). */
  static final int STATUS_LABELS_TTL_SECONDS = 3_600;

  /** Environment variable that carries the ElastiCache primary endpoint host name. */
  private static final String ENV_REDIS_HOST = "REDIS_HOST";

  /** Environment variable that carries the ElastiCache port (default 6379). */
  private static final String ENV_REDIS_PORT = "REDIS_PORT";

  private static final int DEFAULT_REDIS_PORT = 6379;
  private static final int SOCKET_TIMEOUT_MS = 3_000;

  // -----------------------------------------------------------------------
  // Singleton
  // -----------------------------------------------------------------------

  private static final StatusLabelsRedisCache INSTANCE = new StatusLabelsRedisCache();

  private StatusLabelsRedisCache() {}

  /** Returns the singleton {@link StatusLabelsRedisCache}. */
  public static StatusLabelsRedisCache getInstance() {
    return INSTANCE;
  }

  // -----------------------------------------------------------------------
  // Public API
  // -----------------------------------------------------------------------

  /**
   * Returns the human-readable label for the given {@link RoutingStatus}.
   *
   * <p>When a Redis connection is available the label is fetched from Amazon ElastiCache.
   * If the routing-status labels key does not yet exist in Redis the entries from
   * {@code fallbackMap} are written to Redis with a TTL of {@value #STATUS_LABELS_TTL_SECONDS}
   * seconds and then the matching entry is returned. When Redis is not reachable the fallback
   * map is consulted directly.
   *
   * @param status      the {@link RoutingStatus} to look up
   * @param fallbackMap the canonical in-process routing-status label map
   * @return the human-readable label, or {@code null} if not found
   */
  public String getRoutingStatusLabel(
      RoutingStatus status, Map<RoutingStatus, String> fallbackMap) {
    return getLabel(REDIS_ROUTING_STATUS_KEY, status.name(), toStringKeyMap(fallbackMap), fallbackMap.get(status));
  }

  /**
   * Returns the human-readable label for the given {@link TransportStatus}.
   *
   * <p>When a Redis connection is available the label is fetched from Amazon ElastiCache.
   * If the transport-status labels key does not yet exist in Redis the entries from
   * {@code fallbackMap} are written to Redis with a TTL of {@value #STATUS_LABELS_TTL_SECONDS}
   * seconds and then the matching entry is returned. When Redis is not reachable the fallback
   * map is consulted directly.
   *
   * @param status      the {@link TransportStatus} to look up
   * @param fallbackMap the canonical in-process transport-status label map
   * @return the human-readable label, or {@code null} if not found
   */
  public String getTransportStatusLabel(
      TransportStatus status, Map<TransportStatus, String> fallbackMap) {
    return getLabel(REDIS_TRANSPORT_STATUS_KEY, status.name(), toStringKeyMap(fallbackMap), fallbackMap.get(status));
  }

  // -----------------------------------------------------------------------
  // Internal helpers
  // -----------------------------------------------------------------------

  private <E extends Enum<E>> Map<String, String> toStringKeyMap(Map<E, String> enumMap) {
    Map<String, String> result = new HashMap<>();
    for (Map.Entry<E, String> entry : enumMap.entrySet()) {
      result.put(entry.getKey().name(), entry.getValue());
    }
    return result;
  }

  private String getLabel(
      String redisKey, String field, Map<String, String> fallbackStringMap, String fallbackValue) {

    String redisHost = System.getenv(ENV_REDIS_HOST);
    if (redisHost == null || redisHost.isBlank()) {
      // Redis not configured – use in-process fallback (local dev / unit tests).
      LOGGER.fine("REDIS_HOST not set; using in-process status labels map (no Redis).");
      return fallbackValue;
    }

    int redisPort = DEFAULT_REDIS_PORT;
    String portEnv = System.getenv(ENV_REDIS_PORT);
    if (portEnv != null && !portEnv.isBlank()) {
      try {
        redisPort = Integer.parseInt(portEnv.trim());
      } catch (NumberFormatException e) {
        LOGGER.warning("Invalid REDIS_PORT value '" + portEnv + "'; using default " + DEFAULT_REDIS_PORT);
      }
    }

    try {
      return fetchLabelFromRedis(redisHost, redisPort, redisKey, field, fallbackStringMap);
    } catch (Exception e) {
      LOGGER.log(Level.WARNING,
          "Could not connect to Redis at " + redisHost + ":" + redisPort
              + "; falling back to in-process status labels map.", e);
      return fallbackValue;
    }
  }

  // -----------------------------------------------------------------------
  // Redis interaction
  // -----------------------------------------------------------------------

  /**
   * Fetches the label for the given field from Redis, seeding the hash from {@code fallbackMap}
   * if the key is absent, and applying a TTL to prevent unbounded growth.
   */
  private String fetchLabelFromRedis(
      String host, int port, String hashKey, String field, Map<String, String> fallbackMap)
      throws IOException {

    try (Socket socket = openSocket(host, port)) {
      boolean exists = redisExists(socket, hashKey);
      if (!exists) {
        seedLabels(socket, hashKey, fallbackMap);
        applyTtl(socket, hashKey, STATUS_LABELS_TTL_SECONDS);
        LOGGER.info("Seeded status labels in Redis at key '" + hashKey
            + "' with " + fallbackMap.size() + " entries and TTL=" + STATUS_LABELS_TTL_SECONDS + "s.");
      }

      String value = redisHGet(socket, hashKey, field);
      if (value == null) {
        LOGGER.fine("Field '" + field + "' not found in Redis hash '" + hashKey
            + "'; falling back to in-process map.");
        return fallbackMap.get(field);
      }
      return value;
    }
  }

  // -----------------------------------------------------------------------
  // RESP helpers
  // -----------------------------------------------------------------------

  private Socket openSocket(String host, int port) throws IOException {
    Socket socket = new Socket(host, port);
    socket.setSoTimeout(SOCKET_TIMEOUT_MS);
    return socket;
  }

  /**
   * Sends {@code EXISTS key} and returns {@code true} when the key exists.
   */
  private boolean redisExists(Socket socket, String key) throws IOException {
    sendCommand(socket, buildRespCommand("EXISTS", key));
    String response = readLine(socket);
    return ":1".equals(response != null ? response.trim() : "");
  }

  /**
   * Seeds the Redis hash at {@code hashKey} with field → label entries.
   */
  private void seedLabels(Socket socket, String hashKey, Map<String, String> labels)
      throws IOException {
    if (labels.isEmpty()) {
      return;
    }
    // Build HSET hashKey field1 value1 field2 value2 ...
    String[] args = new String[2 + labels.size() * 2];
    args[0] = "HSET";
    args[1] = hashKey;
    int i = 2;
    for (Map.Entry<String, String> entry : labels.entrySet()) {
      args[i++] = entry.getKey();
      args[i++] = entry.getValue();
    }
    sendCommand(socket, buildRespCommand(args));
    readLine(socket); // consume reply
  }

  /**
   * Applies a TTL (EXPIRE) to the given key to prevent indefinite memory growth.
   */
  private void applyTtl(Socket socket, String key, int ttlSeconds) throws IOException {
    sendCommand(socket, buildRespCommand("EXPIRE", key, String.valueOf(ttlSeconds)));
    readLine(socket); // consume reply
  }

  /**
   * Sends {@code HGET hashKey field} and returns the value, or {@code null} if absent.
   */
  private String redisHGet(Socket socket, String hashKey, String field) throws IOException {
    sendCommand(socket, buildRespCommand("HGET", hashKey, field));
    return readBulkString(socket);
  }

  // -----------------------------------------------------------------------
  // RESP serialisation / deserialisation
  // -----------------------------------------------------------------------

  private String buildRespCommand(String... args) {
    StringBuilder sb = new StringBuilder();
    sb.append('*').append(args.length).append("\r\n");
    for (String arg : args) {
      byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
      sb.append('$').append(bytes.length).append("\r\n");
      sb.append(arg).append("\r\n");
    }
    return sb.toString();
  }

  private void sendCommand(Socket socket, String command) throws IOException {
    OutputStream out = socket.getOutputStream();
    out.write(command.getBytes(StandardCharsets.UTF_8));
    out.flush();
  }

  private String readLine(Socket socket) throws IOException {
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    return reader.readLine();
  }

  private String readBulkString(Socket socket) throws IOException {
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    String lenLine = reader.readLine();
    if (lenLine == null || !lenLine.startsWith("$")) {
      return null;
    }
    int len;
    try {
      len = Integer.parseInt(lenLine.substring(1).trim());
    } catch (NumberFormatException e) {
      return null;
    }
    if (len < 0) {
      return null; // nil bulk string
    }
    return reader.readLine();
  }

  // -----------------------------------------------------------------------
  // Factory methods for default fallback maps (used by RealtimeCargoTrackingViewAdapter)
  // -----------------------------------------------------------------------

  /**
   * Builds the default routing-status label map used as the in-process fallback and Redis seed.
   */
  public static Map<RoutingStatus, String> buildDefaultRoutingStatusLabels() {
    Map<RoutingStatus, String> map = new EnumMap<>(RoutingStatus.class);
    map.put(RoutingStatus.NOT_ROUTED, "Not routed");
    map.put(RoutingStatus.ROUTED, "Routed");
    map.put(RoutingStatus.MISROUTED, "Misrouted");
    return Collections.unmodifiableMap(map);
  }

  /**
   * Builds the default transport-status label map used as the in-process fallback and Redis seed.
   */
  public static Map<TransportStatus, String> buildDefaultTransportStatusLabels() {
    Map<TransportStatus, String> map = new EnumMap<>(TransportStatus.class);
    map.put(TransportStatus.NOT_RECEIVED, "Not received");
    map.put(TransportStatus.IN_PORT, "In port");
    map.put(TransportStatus.ONBOARD_CARRIER, "Onboard carrier");
    map.put(TransportStatus.CLAIMED, "Claimed");
    map.put(TransportStatus.UNKNOWN, "Unknown");
    return Collections.unmodifiableMap(map);
  }
}
