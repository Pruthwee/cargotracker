package org.eclipse.cargotracker.interfaces;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cloud-native coordinates cache backed by Amazon ElastiCache for Redis.
 *
 * <p>This class replaces the previously static in-memory {@code COORDINATES_MAP} in
 * {@link CoordinatesFactory} (cr-java-0067 – In-Memory Caching Without TTL). In a multi-instance
 * cloud deployment every application node shares a single, consistent view of the coordinates
 * stored in Redis with a configurable TTL, eliminating unbounded memory growth and stale data
 * inconsistencies across instances.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>The singleton instance is obtained via {@link #getInstance()}.
 *   <li>{@link #getCoordinates(String, Map)} returns the coordinates for a given UN/LOCODE.
 *       When a Redis connection is available (controlled by the {@code REDIS_HOST} /
 *       {@code REDIS_PORT} environment variables) the coordinates are loaded from Redis.
 *       When Redis is not reachable the method falls back to the supplied in-process map,
 *       ensuring the application continues to work in local-development and test environments.
 *   <li>The Redis key used for the coordinates hash is {@value #REDIS_COORDINATES_KEY}.
 *   <li>A TTL of {@value #COORDINATES_TTL_SECONDS} seconds is applied to the Redis key to
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
public final class CoordinatesRedisCache {

  private static final Logger LOGGER = Logger.getLogger(CoordinatesRedisCache.class.getName());

  /** Redis hash key under which coordinates are stored. */
  static final String REDIS_COORDINATES_KEY = "cargotracker:coordinates";

  /** TTL in seconds applied to the coordinates key (24 hours). */
  static final int COORDINATES_TTL_SECONDS = 86_400;

  /** Environment variable that carries the ElastiCache primary endpoint host name. */
  private static final String ENV_REDIS_HOST = "REDIS_HOST";

  /** Environment variable that carries the ElastiCache port (default 6379). */
  private static final String ENV_REDIS_PORT = "REDIS_PORT";

  private static final int DEFAULT_REDIS_PORT = 6379;
  private static final int SOCKET_TIMEOUT_MS = 3_000;

  // -----------------------------------------------------------------------
  // Singleton
  // -----------------------------------------------------------------------

  private static final CoordinatesRedisCache INSTANCE = new CoordinatesRedisCache();

  private CoordinatesRedisCache() {}

  /** Returns the singleton {@link CoordinatesRedisCache}. */
  public static CoordinatesRedisCache getInstance() {
    return INSTANCE;
  }

  // -----------------------------------------------------------------------
  // Public API
  // -----------------------------------------------------------------------

  /**
   * Returns the {@link Coordinates} for the given UN/LOCODE string.
   *
   * <p>When a Redis connection is available the coordinates are fetched from Amazon ElastiCache.
   * If the coordinates key does not yet exist in Redis the entries from {@code fallbackMap} are
   * written to Redis with a TTL of {@value #COORDINATES_TTL_SECONDS} seconds and then the
   * matching entry is returned. When Redis is not reachable the fallback map is consulted
   * directly so that the application degrades gracefully.
   *
   * @param unLocode    the UN/LOCODE string to look up
   * @param fallbackMap the canonical in-process coordinates map used as seed and fallback
   * @return the {@link Coordinates} for the given UN/LOCODE, or {@code null} if not found
   */
  public Coordinates getCoordinates(String unLocode, Map<String, Coordinates> fallbackMap) {
    String redisHost = System.getenv(ENV_REDIS_HOST);
    if (redisHost == null || redisHost.isBlank()) {
      // Redis not configured – use in-process fallback (local dev / unit tests).
      LOGGER.fine("REDIS_HOST not set; using in-process coordinates map (no Redis).");
      return fallbackMap.get(unLocode);
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
      return fetchFromRedis(redisHost, redisPort, unLocode, fallbackMap);
    } catch (Exception e) {
      LOGGER.log(Level.WARNING,
          "Could not connect to Redis at " + redisHost + ":" + redisPort
              + "; falling back to in-process coordinates map.", e);
      return fallbackMap.get(unLocode);
    }
  }

  // -----------------------------------------------------------------------
  // Redis interaction
  // -----------------------------------------------------------------------

  /**
   * Fetches the coordinates for the given UN/LOCODE from Redis, seeding the hash from
   * {@code fallbackMap} if the key is absent, and applying a TTL to prevent unbounded growth.
   *
   * <p>Coordinates are serialised as a Redis Hash where each field is the UN/LOCODE string and
   * each value is {@code "lat,lon"} (e.g. {@code "22,114"}).
   *
   * @param host        ElastiCache primary endpoint
   * @param port        ElastiCache port
   * @param unLocode    the UN/LOCODE to look up
   * @param fallbackMap in-process coordinates map used as seed
   * @return the {@link Coordinates} for the given UN/LOCODE, or {@code null} if not found
   */
  private Coordinates fetchFromRedis(
      String host, int port, String unLocode, Map<String, Coordinates> fallbackMap)
      throws IOException {

    try (Socket socket = openSocket(host, port)) {
      boolean exists = redisExists(socket, REDIS_COORDINATES_KEY);
      if (!exists) {
        seedCoordinates(socket, REDIS_COORDINATES_KEY, fallbackMap);
        applyTtl(socket, REDIS_COORDINATES_KEY, COORDINATES_TTL_SECONDS);
        LOGGER.info("Seeded coordinates in Redis at key '" + REDIS_COORDINATES_KEY
            + "' with " + fallbackMap.size() + " entries and TTL=" + COORDINATES_TTL_SECONDS + "s.");
      }

      String value = redisHGet(socket, REDIS_COORDINATES_KEY, unLocode);
      if (value == null) {
        LOGGER.fine("UN/LOCODE '" + unLocode + "' not found in Redis; falling back to in-process map.");
        return fallbackMap.get(unLocode);
      }
      return parseCoordinates(value);
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
   * Seeds the Redis hash at {@code hashKey} with UN/LOCODE → "lat,lon" entries.
   */
  private void seedCoordinates(
      Socket socket, String hashKey, Map<String, Coordinates> coordinates) throws IOException {
    if (coordinates.isEmpty()) {
      return;
    }
    // Build HSET hashKey field1 value1 field2 value2 ...
    String[] args = new String[2 + coordinates.size() * 2];
    args[0] = "HSET";
    args[1] = hashKey;
    int i = 2;
    for (Map.Entry<String, Coordinates> entry : coordinates.entrySet()) {
      args[i++] = entry.getKey();
      args[i++] = entry.getValue().getLatitude() + "," + entry.getValue().getLongitude();
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

  /**
   * Parses a {@code "lat,lon"} string into a {@link Coordinates} instance.
   */
  private Coordinates parseCoordinates(String value) {
    String[] parts = value.split(",", 2);
    if (parts.length != 2) {
      return null;
    }
    try {
      double lat = Double.parseDouble(parts[0].trim());
      double lon = Double.parseDouble(parts[1].trim());
      return new Coordinates(lat, lon);
    } catch (NumberFormatException e) {
      LOGGER.warning("Could not parse coordinates value '" + value + "': " + e.getMessage());
      return null;
    }
  }
}
