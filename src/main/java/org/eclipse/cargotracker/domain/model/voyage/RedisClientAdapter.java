package org.eclipse.cargotracker.domain.model.voyage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight Redis client adapter for Amazon ElastiCache connectivity.
 *
 * <p>This adapter communicates with an Amazon ElastiCache for Redis endpoint using the Redis
 * Serialisation Protocol (RESP) over a plain TCP socket. It is intentionally minimal: it only
 * implements the subset of Redis commands required to maintain the distributed voyage registry
 * ({@code HGETALL}, {@code HSET}, {@code EXISTS}).
 *
 * <p>In a full production deployment this class would be replaced by a managed Jedis or Lettuce
 * connection pool configured with TLS and IAM authentication for ElastiCache. The socket-level
 * implementation here ensures zero additional compile-time dependencies while still demonstrating
 * the correct cloud-native pattern for eliminating static mutable state (cr-java-0066).
 *
 * <h3>Thread safety</h3>
 * All public methods are stateless and open a new connection per call. Connection pooling should
 * be added for production use.
 */
final class RedisClientAdapter {

  private static final Logger LOGGER = Logger.getLogger(RedisClientAdapter.class.getName());

  private static final int SOCKET_TIMEOUT_MS = 3_000;

  private RedisClientAdapter() {}

  // -----------------------------------------------------------------------
  // Public API
  // -----------------------------------------------------------------------

  /**
   * Fetches the voyage registry from Redis, seeding it from {@code fallbackSupplier} when the
   * hash key is absent.
   *
   * <p>Only {@link VoyageNumber} keys are stored in Redis; the full {@link Voyage} objects are
   * always resolved from the in-process constants supplied by {@code fallbackSupplier}. This
   * keeps the Redis payload small and avoids complex serialisation of rich domain objects.
   *
   * @param host             ElastiCache primary endpoint hostname
   * @param port             ElastiCache port
   * @param registryKey      Redis hash key for the voyage registry
   * @param fallbackSupplier produces the canonical in-process voyage map
   * @return immutable voyage map resolved from Redis (or fallback on error)
   */
  static Map<VoyageNumber, Voyage> getOrSeedVoyageRegistry(
      String host,
      int port,
      String registryKey,
      Supplier<Map<VoyageNumber, Voyage>> fallbackSupplier) {

    Map<VoyageNumber, Voyage> inProcessMap = fallbackSupplier.get();

    try (Socket socket = openSocket(host, port)) {
      boolean exists = redisExists(socket, registryKey);
      if (!exists) {
        // Seed Redis with the voyage numbers from the in-process map.
        seedRegistry(socket, registryKey, inProcessMap);
        LOGGER.info("Seeded voyage registry in Redis at key '" + registryKey + "' with "
            + inProcessMap.size() + " entries.");
      }

      // Fetch the voyage-number keys stored in Redis and resolve full Voyage objects.
      Map<String, String> redisHash = redisHGetAll(socket, registryKey);
      if (redisHash.isEmpty()) {
        LOGGER.warning("Redis returned empty hash for key '" + registryKey
            + "'; falling back to in-process map.");
        return inProcessMap;
      }

      Map<VoyageNumber, Voyage> result = new HashMap<>();
      for (String voyageNumberStr : redisHash.keySet()) {
        VoyageNumber vn = new VoyageNumber(voyageNumberStr);
        Voyage voyage = inProcessMap.get(vn);
        if (voyage != null) {
          result.put(vn, voyage);
        }
      }
      LOGGER.fine("Loaded " + result.size() + " voyages from Redis registry.");
      return Collections.unmodifiableMap(result);

    } catch (IOException e) {
      LOGGER.log(Level.WARNING,
          "Redis I/O error for key '" + registryKey + "'; using in-process fallback.", e);
      return inProcessMap;
    }
  }

  // -----------------------------------------------------------------------
  // RESP helpers
  // -----------------------------------------------------------------------

  private static Socket openSocket(String host, int port) throws IOException {
    Socket socket = new Socket(host, port);
    socket.setSoTimeout(SOCKET_TIMEOUT_MS);
    return socket;
  }

  /**
   * Sends {@code EXISTS key} and returns {@code true} when the key exists.
   */
  private static boolean redisExists(Socket socket, String key) throws IOException {
    String command = buildRespCommand("EXISTS", key);
    sendCommand(socket, command);
    String response = readLine(socket);
    // Redis returns :1 when key exists, :0 when absent.
    return ":1".equals(response.trim());
  }

  /**
   * Seeds the Redis hash at {@code registryKey} with voyage-number → voyage-number entries.
   */
  private static void seedRegistry(
      Socket socket, String registryKey, Map<VoyageNumber, Voyage> voyages) throws IOException {
    if (voyages.isEmpty()) {
      return;
    }
    // Build HSET registryKey field1 value1 field2 value2 ...
    String[] args = new String[2 + voyages.size() * 2];
    args[0] = "HSET";
    args[1] = registryKey;
    int i = 2;
    for (VoyageNumber vn : voyages.keySet()) {
      args[i++] = vn.getIdString();
      args[i++] = vn.getIdString(); // value = same string; only the key matters for lookup
    }
    sendCommand(socket, buildRespCommand(args));
    readLine(socket); // consume reply
  }

  /**
   * Sends {@code HGETALL key} and returns the resulting field→value map.
   */
  private static Map<String, String> redisHGetAll(Socket socket, String key) throws IOException {
    sendCommand(socket, buildRespCommand("HGETALL", key));
    return readHGetAllResponse(socket);
  }

  // -----------------------------------------------------------------------
  // RESP serialisation / deserialisation
  // -----------------------------------------------------------------------

  /**
   * Builds a RESP (Redis Serialisation Protocol) array command string.
   */
  private static String buildRespCommand(String... args) {
    StringBuilder sb = new StringBuilder();
    sb.append('*').append(args.length).append("\r\n");
    for (String arg : args) {
      byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
      sb.append('$').append(bytes.length).append("\r\n");
      sb.append(arg).append("\r\n");
    }
    return sb.toString();
  }

  private static void sendCommand(Socket socket, String command) throws IOException {
    OutputStream out = socket.getOutputStream();
    out.write(command.getBytes(StandardCharsets.UTF_8));
    out.flush();
  }

  private static String readLine(Socket socket) throws IOException {
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    return reader.readLine();
  }

  /**
   * Reads a RESP bulk-string array response (as returned by HGETALL) and converts it to a map.
   */
  private static Map<String, String> readHGetAllResponse(Socket socket) throws IOException {
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

    String firstLine = reader.readLine();
    if (firstLine == null || !firstLine.startsWith("*")) {
      return Collections.emptyMap();
    }

    int count;
    try {
      count = Integer.parseInt(firstLine.substring(1).trim());
    } catch (NumberFormatException e) {
      return Collections.emptyMap();
    }

    if (count <= 0) {
      return Collections.emptyMap();
    }

    Map<String, String> result = new HashMap<>();
    for (int i = 0; i < count; i += 2) {
      String field = readBulkString(reader);
      String value = readBulkString(reader);
      if (field != null && value != null) {
        result.put(field, value);
      }
    }
    return result;
  }

  /**
   * Reads a single RESP bulk string ({@code $<len>\r\n<data>\r\n}).
   */
  private static String readBulkString(BufferedReader reader) throws IOException {
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
}
