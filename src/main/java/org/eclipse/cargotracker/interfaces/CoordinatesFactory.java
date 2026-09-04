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

import java.util.HashMap;
import java.util.Map;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * At the moment, coordinates are produced by a simple factory backed by Amazon ElastiCache (Redis)
 * for horizontal scalability on EKS. Connection details are injected via environment variables
 * (REDIS_HOST, REDIS_PORT) sourced from Kubernetes ConfigMaps/Secrets with IRSA-secured access.
 *
 * <p>It may be converted to a repository if coordinates become a domain layer concern.
 */
public class CoordinatesFactory {

  // Redis-backed distributed cache replacing the former in-process local cache (cz-java-0070).
  // Connection details are supplied via environment variables injected by Kubernetes ConfigMaps
  // and Secrets, enabling safe horizontal scaling on EKS with IRSA-secured ElastiCache access.
  private static final JedisPool JEDIS_POOL;
  private static final String CACHE_KEY_PREFIX = "coordinates:";

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
      String value = jedis.get(CACHE_KEY_PREFIX + unLocode);
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

    JEDIS_POOL = new JedisPool(new JedisPoolConfig(), redisHost, redisPort);

    Map<String, Coordinates> seedData = new HashMap<>();

    // TODO [Clean Code] See if there is a service to get the latitude/longitude data from.
    seedData.put(HONGKONG.getUnLocode().getIdString(), new Coordinates(22, 114));
    seedData.put(MELBOURNE.getUnLocode().getIdString(), new Coordinates(-38, 145));
    seedData.put(STOCKHOLM.getUnLocode().getIdString(), new Coordinates(59, 18));
    seedData.put(HELSINKI.getUnLocode().getIdString(), new Coordinates(60, 25));
    seedData.put(CHICAGO.getUnLocode().getIdString(), new Coordinates(42, -88));
    seedData.put(TOKYO.getUnLocode().getIdString(), new Coordinates(36, 140));
    seedData.put(HAMBURG.getUnLocode().getIdString(), new Coordinates(54, 10));
    seedData.put(SHANGHAI.getUnLocode().getIdString(), new Coordinates(31, 121));
    seedData.put(ROTTERDAM.getUnLocode().getIdString(), new Coordinates(52, 5));
    seedData.put(GOTHENBURG.getUnLocode().getIdString(), new Coordinates(58, 12));
    seedData.put(HANGZOU.getUnLocode().getIdString(), new Coordinates(30, 120));
    seedData.put(NEWYORK.getUnLocode().getIdString(), new Coordinates(41, -74));
    seedData.put(DALLAS.getUnLocode().getIdString(), new Coordinates(33, -97));
    seedData.put(UNKNOWN.getUnLocode().getIdString(), new Coordinates(-90, 0)); // The South Pole.

    try (Jedis jedis = JEDIS_POOL.getResource()) {
      for (Map.Entry<String, Coordinates> entry : seedData.entrySet()) {
        String key = CACHE_KEY_PREFIX + entry.getKey();
        // Only seed if not already present (avoid overwriting on restart)
        if (jedis.get(key) == null) {
          jedis.set(key, entry.getValue().getLatitude() + "," + entry.getValue().getLongitude());
        }
      }
    }
  }
}
