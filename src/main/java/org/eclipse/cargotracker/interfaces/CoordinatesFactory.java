package org.eclipse.cargotracker.interfaces;

import static org.eclipse.cargotracker.domain.model.location.Location.UNKNOWN;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.CHICAGO;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.DALLAS;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.GOTHENBURG;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.HAMBURG;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.HANGZOU;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.HELSINKI;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.HONGKONG;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.MELBOURNE;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.NEWYORK;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.ROTTERDAM;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.SHANGHAI;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.STOCKHOLM;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.TOKYO;

import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * At the moment, coordinates are produced by a simple factory backed by Amazon ElastiCache (Redis)
 * for horizontal scalability on EKS. Connection details are injected via environment variables
 * REDIS_HOST and REDIS_PORT (Kubernetes ConfigMap/Secret with IRSA-secured access).
 *
 * <p>cz-java-0070: Replaced local in-process HashMap cache with Amazon ElastiCache (Redis) to
 * ensure coordinates data is shared across all horizontally-scaled container replicas.
 */
public class CoordinatesFactory {

  // cz-java-0070: Replaced local HashMap cache with Amazon ElastiCache (Redis) JedisPool.
  // Connection details are injected via REDIS_HOST and REDIS_PORT environment variables
  // (Kubernetes ConfigMap/Secret with IRSA-secured access on EKS).
  private static final JedisPool JEDIS_POOL;

  private static final String REDIS_KEY_PREFIX = "coordinates:";

  private CoordinatesFactory() {
    /* Prevent instantiation. */
  }

  public static Coordinates find(Location location) {
    return find(location.getUnLocode());
  }

  public static Coordinates find(UnLocode unLocode) {
    return find(unLocode.getIdString());
  }

  public static Coordinates find(String unLocode) {
    try (Jedis jedis = JEDIS_POOL.getResource()) {
      String value = jedis.get(REDIS_KEY_PREFIX + unLocode);
      if (value == null) {
        return null;
      }
      String[] parts = value.split(",");
      return new Coordinates(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
    }
  }

  static {
    String redisHost = System.getenv("REDIS_HOST") != null ? System.getenv("REDIS_HOST") : "localhost";
    int redisPort = System.getenv("REDIS_PORT") != null ? Integer.parseInt(System.getenv("REDIS_PORT")) : 6379;

    JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(10);
    poolConfig.setMaxIdle(5);
    poolConfig.setMinIdle(1);
    JEDIS_POOL = new JedisPool(poolConfig, redisHost, redisPort);

    // Seed ElastiCache with coordinates data on startup.
    // TODO [Clean Code] See if there is a service to get the latitude/longitude data from.
    try (Jedis jedis = JEDIS_POOL.getResource()) {
      jedis.set(REDIS_KEY_PREFIX + HONGKONG.getUnLocode().getIdString(), "22.0,114.0");
      jedis.set(REDIS_KEY_PREFIX + MELBOURNE.getUnLocode().getIdString(), "-38.0,145.0");
      jedis.set(REDIS_KEY_PREFIX + STOCKHOLM.getUnLocode().getIdString(), "59.0,18.0");
      jedis.set(REDIS_KEY_PREFIX + HELSINKI.getUnLocode().getIdString(), "60.0,25.0");
      jedis.set(REDIS_KEY_PREFIX + CHICAGO.getUnLocode().getIdString(), "42.0,-88.0");
      jedis.set(REDIS_KEY_PREFIX + TOKYO.getUnLocode().getIdString(), "36.0,140.0");
      jedis.set(REDIS_KEY_PREFIX + HAMBURG.getUnLocode().getIdString(), "54.0,10.0");
      jedis.set(REDIS_KEY_PREFIX + SHANGHAI.getUnLocode().getIdString(), "31.0,121.0");
      jedis.set(REDIS_KEY_PREFIX + ROTTERDAM.getUnLocode().getIdString(), "52.0,5.0");
      jedis.set(REDIS_KEY_PREFIX + GOTHENBURG.getUnLocode().getIdString(), "58.0,12.0");
      jedis.set(REDIS_KEY_PREFIX + HANGZOU.getUnLocode().getIdString(), "30.0,120.0");
      jedis.set(REDIS_KEY_PREFIX + NEWYORK.getUnLocode().getIdString(), "41.0,-74.0");
      jedis.set(REDIS_KEY_PREFIX + DALLAS.getUnLocode().getIdString(), "33.0,-97.0");
      jedis.set(REDIS_KEY_PREFIX + UNKNOWN.getUnLocode().getIdString(), "-90.0,0.0");
    }
  }
}
