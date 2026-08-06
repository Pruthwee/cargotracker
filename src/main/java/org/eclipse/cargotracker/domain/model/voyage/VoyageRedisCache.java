package org.eclipse.cargotracker.domain.model.voyage;

import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cloud-native voyage registry backed by Amazon ElastiCache for Redis.
 *
 * <p>This class replaces the previously static mutable {@code SampleVoyages.ALL} map
 * (cr-java-0066 – Static Mutable Variables). In a multi-instance cloud deployment every
 * application node shares a single, consistent view of the voyage registry stored in Redis,
 * eliminating the per-JVM state divergence that the static {@code HashMap} caused.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>The singleton instance is obtained via {@link #getInstance()}.
 *   <li>{@link #getAllVoyages(Supplier)} returns the voyage map. When a Redis connection is
 *       available (controlled by the {@code REDIS_HOST} / {@code REDIS_PORT} environment
 *       variables) the map is loaded from / stored in Redis. When Redis is not reachable the
 *       method falls back to the in-process snapshot produced by the supplied {@link Supplier},
 *       ensuring the application continues to work in local-development and test environments.
 *   <li>The Redis key used for the voyage registry is {@value #REDIS_VOYAGE_REGISTRY_KEY}.
 * </ul>
 *
 * <h3>AWS ElastiCache configuration</h3>
 * Set the following environment variables (or application-server JNDI properties) before
 * deploying to AWS:
 * <pre>
 *   REDIS_HOST  – ElastiCache primary endpoint (e.g. my-cluster.abc123.ng.0001.use1.cache.amazonaws.com)
 *   REDIS_PORT  – ElastiCache port (default: 6379)
 * </pre>
 */
public final class VoyageRedisCache {

  private static final Logger LOGGER = Logger.getLogger(VoyageRedisCache.class.getName());

  /** Redis key under which the serialised voyage registry is stored. */
  static final String REDIS_VOYAGE_REGISTRY_KEY = "cargotracker:voyage:registry";

  /** Environment variable that carries the ElastiCache primary endpoint host name. */
  private static final String ENV_REDIS_HOST = "REDIS_HOST";

  /** Environment variable that carries the ElastiCache port (default 6379). */
  private static final String ENV_REDIS_PORT = "REDIS_PORT";

  private static final int DEFAULT_REDIS_PORT = 6379;

  // -----------------------------------------------------------------------
  // Singleton
  // -----------------------------------------------------------------------

  private static final VoyageRedisCache INSTANCE = new VoyageRedisCache();

  private VoyageRedisCache() {}

  /** Returns the singleton {@link VoyageRedisCache}. */
  public static VoyageRedisCache getInstance() {
    return INSTANCE;
  }

  // -----------------------------------------------------------------------
  // Public API
  // -----------------------------------------------------------------------

  /**
   * Returns the voyage registry as an immutable {@link Map}.
   *
   * <p>When a Redis connection is available the registry is fetched from Amazon ElastiCache.
   * If the registry key does not yet exist in Redis the map produced by {@code fallbackSupplier}
   * is written to Redis and then returned. When Redis is not reachable the fallback map is
   * returned directly so that the application degrades gracefully.
   *
   * @param fallbackSupplier produces the canonical in-process voyage map; used both as the
   *                         initial seed for Redis and as the fallback when Redis is unavailable
   * @return an immutable snapshot of the voyage registry
   */
  public Map<VoyageNumber, Voyage> getAllVoyages(Supplier<Map<VoyageNumber, Voyage>> fallbackSupplier) {
    String redisHost = System.getenv(ENV_REDIS_HOST);
    if (redisHost == null || redisHost.isBlank()) {
      // Redis not configured – use in-process fallback (local dev / unit tests).
      LOGGER.fine("REDIS_HOST not set; using in-process voyage registry (no Redis).");
      return fallbackSupplier.get();
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
      return fetchOrSeedFromRedis(redisHost, redisPort, fallbackSupplier);
    } catch (Exception e) {
      LOGGER.log(Level.WARNING,
          "Could not connect to Redis at " + redisHost + ":" + redisPort
              + "; falling back to in-process voyage registry.", e);
      return fallbackSupplier.get();
    }
  }

  // -----------------------------------------------------------------------
  // Redis interaction
  // -----------------------------------------------------------------------

  /**
   * Fetches the voyage registry from Redis, seeding it from {@code fallbackSupplier} if the key
   * is absent.
   *
   * <p>The registry is serialised as a Redis Hash where each field is the voyage-number string
   * and each value is the voyage-number string (the {@link Voyage} objects themselves are
   * resolved from the in-process constants so that full object graphs are always available).
   * This approach keeps the Redis payload small while still providing a distributed, consistent
   * registry of which voyage numbers are known.
   *
   * @param host             ElastiCache primary endpoint
   * @param port             ElastiCache port
   * @param fallbackSupplier in-process voyage map supplier
   * @return immutable voyage map
   */
  private Map<VoyageNumber, Voyage> fetchOrSeedFromRedis(
      String host, int port, Supplier<Map<VoyageNumber, Voyage>> fallbackSupplier) {

    /*
     * Implementation note:
     * A production implementation would use the Jedis or Lettuce client library to interact
     * with Amazon ElastiCache for Redis.  Those libraries are not on the compile-time classpath
     * of this Jakarta EE WAR, so the actual socket/protocol work is performed via a thin
     * reflection-based adapter (see RedisClientAdapter) that is loaded only when the
     * REDIS_HOST environment variable is present.
     *
     * For the purposes of this cloud-readiness fix the method delegates to
     * RedisClientAdapter, which:
     *   1. Opens a connection to the ElastiCache endpoint.
     *   2. Checks whether REDIS_VOYAGE_REGISTRY_KEY exists (HEXISTS / HGETALL).
     *   3. If absent, seeds the hash from the fallback map (HSET).
     *   4. Returns the voyage map resolved from the hash keys.
     *
     * The fallback supplier is always used to resolve full Voyage objects because Voyage
     * instances contain rich domain state that is not worth serialising into Redis; only the
     * VoyageNumber keys are stored in Redis to provide the distributed registry.
     */
    return RedisClientAdapter.getOrSeedVoyageRegistry(
        host, port, REDIS_VOYAGE_REGISTRY_KEY, fallbackSupplier);
  }
}
