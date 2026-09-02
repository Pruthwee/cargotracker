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

import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * At the moment, coordinates are produced by a simple factory. It may be converted to a repository
 * if coordinates become a domain layer concern.
 *
 * <p>Coordinates are stored in Amazon ElastiCache (Redis) so that all horizontally-scaled
 * container replicas share a single consistent cache. Connection details are injected via
 * environment variables REDIS_HOST, REDIS_PORT, and REDIS_PASSWORD (Kubernetes ConfigMap/Secret).
 */
public class CoordinatesFactory {

  // cz-java-0070: Replaced local in-process HashMap cache with Amazon ElastiCache (Redis)
  // backed storage. Connection details are read from environment variables so that all
  // EKS pod replicas share the same distributed cache instead of maintaining per-JVM state.
  private static final JedisPool JEDIS_POOL;
  private static final String COORDINATES_KEY_PREFIX = "coordinates:";

  private static final Logger logger = Logger.getLogger(CoordinatesFactory.class.getName());

  private CoordinatesFactory() {
    /* Prevent instantiation. */
  }

  static {
    String redisHost = System.getenv("REDIS_HOST") != null
        && !System.getenv("REDIS_HOST").isEmpty()
        ? System.getenv("REDIS_HOST") : "localhost";
    int redisPort;
    try {
      String portEnv = System.getenv("REDIS_PORT");
      redisPort = (portEnv != null && !portEnv.isEmpty()) ? Integer.parseInt(portEnv) : 6379;
    } catch (NumberFormatException e) {
      redisPort = 6379;
    }
    String redisPassword = System.getenv("REDIS_PASSWORD");

    JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(10);
    poolConfig.setMaxIdle(5);
    poolConfig.setMinIdle(1);

    if (redisPassword != null && !redisPassword.isEmpty()) {
      JEDIS_POOL = new JedisPool(poolConfig, redisHost, redisPort, 2000, redisPassword);
    } else {
      JEDIS_POOL = new JedisPool(poolConfig, redisHost, redisPort);
    }

    // Seed the Redis cache with the known coordinates on first initialisation.
    // TODO [Clean Code] See if there is a service to get the latitude/longitude data from.
    seedCoordinates();
  }

  private static void seedCoordinates() {
    try (Jedis jedis = JEDIS_POOL.getResource()) {
      putIfAbsent(jedis, HONGKONG.getUnLocode().getIdString(), 22, 114);
      putIfAbsent(jedis, MELBOURNE.getUnLocode().getIdString(), -38, 145);
      putIfAbsent(jedis, STOCKHOLM.getUnLocode().getIdString(), 59, 18);
      putIfAbsent(jedis, HELSINKI.getUnLocode().getIdString(), 60, 25);
      putIfAbsent(jedis, CHICAGO.getUnLocode().getIdString(), 42, -88);
      putIfAbsent(jedis, TOKYO.getUnLocode().getIdString(), 36, 140);
      putIfAbsent(jedis, HAMBURG.getUnLocode().getIdString(), 54, 10);
      putIfAbsent(jedis, SHANGHAI.getUnLocode().getIdString(), 31, 121);
      putIfAbsent(jedis, ROTTERDAM.getUnLocode().getIdString(), 52, 5);
      putIfAbsent(jedis, GOTHENBURG.getUnLocode().getIdString(), 58, 12);
      putIfAbsent(jedis, HANGZOU.getUnLocode().getIdString(), 30, 120);
      putIfAbsent(jedis, NEWYORK.getUnLocode().getIdString(), 41, -74);
      putIfAbsent(jedis, DALLAS.getUnLocode().getIdString(), 33, -97);
      putIfAbsent(jedis, UNKNOWN.getUnLocode().getIdString(), -90, 0); // The South Pole.
    } catch (Exception e) {
      logger.log(Level.WARNING,
          "Could not seed coordinates into Redis cache. "
              + "Ensure REDIS_HOST/REDIS_PORT/REDIS_PASSWORD env vars are set correctly.", e);
    }
  }

  private static void putIfAbsent(Jedis jedis, String unLocode, double lat, double lon) {
    String key = COORDINATES_KEY_PREFIX + unLocode;
    if (!jedis.exists(key)) {
      jedis.hset(key, "lat", String.valueOf(lat));
      jedis.hset(key, "lon", String.valueOf(lon));
    }
  }

  public static Coordinates find(Location location) {
    return find(location.getUnLocode());
  }

  public static Coordinates find(UnLocode unLocode) {
    return find(unLocode.getIdString());
  }

  public static Coordinates find(String unLocode) {
    try (Jedis jedis = JEDIS_POOL.getResource()) {
      String key = COORDINATES_KEY_PREFIX + unLocode;
      String lat = jedis.hget(key, "lat");
      String lon = jedis.hget(key, "lon");
      if (lat != null && lon != null) {
        return new Coordinates(Double.parseDouble(lat), Double.parseDouble(lon));
      }
    } catch (Exception e) {
      logger.log(Level.WARNING,
          "Could not retrieve coordinates for " + unLocode + " from Redis cache.", e);
    }
    return null;
  }
}
