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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * At the moment, coordinates are produced by a simple factory. It may be converted to a repository
 * if coordinates become a domain layer concern.
 *
 * <p>Replaced the static in-memory cache (without TTL) with Redis-backed storage via ElastiCache
 * for Redis, enabling consistent data across instances and proper TTL management. Falls back to
 * in-memory data when Redis is unavailable.
 */
public class CoordinatesFactory {

  private static final String REDIS_KEY_PREFIX = "coordinates:";
  private static final int TTL_SECONDS = 3600; // 1 hour

  private static RedisClient redisClient;
  private static StatefulRedisConnection<String, String> redisConnection;
  private static RedisCommands<String, String> redisCommands;
  private static boolean redisAvailable = false;
  private static final Object initLock = new Object();
  private static final Logger logger = Logger.getLogger(CoordinatesFactory.class.getName());

  private CoordinatesFactory() {
    /* Prevent instantiation. */
  }

  /** Lazily initializes the Redis client from environment variables on first use. */
  private static void ensureRedisInitialized() {
    if (redisCommands != null) {
      return;
    }
    synchronized (initLock) {
      if (redisCommands != null) {
        return;
      }
      String redisEndpoint = System.getenv("ELASTICACHE_ENDPOINT");
      if (redisEndpoint == null || redisEndpoint.isEmpty()) {
        logger.log(Level.WARNING, "ELASTICACHE_ENDPOINT not set, using in-memory coordinates cache");
        return;
      }
      try {
        redisClient = RedisClient.create(RedisURI.create(redisEndpoint, 6379));
        redisConnection = redisClient.connect();
        redisCommands = redisConnection.sync();
        redisCommands.ping();
        redisAvailable = true;
        logger.log(Level.INFO, "Connected to Redis at {0} for coordinates cache", redisEndpoint);
        populateRedis();
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to connect to Redis, using in-memory coordinates cache", e);
      }
    }
  }

  /** Populates Redis with coordinate data. */
  private static void populateRedis() {
    Map<String, Coordinates> map = new HashMap<>();
    map.put(HONGKONG.getUnLocode().getIdString(), new Coordinates(22, 114));
    map.put(MELBOURNE.getUnLocode().getIdString(), new Coordinates(-38, 145));
    map.put(STOCKHOLM.getUnLocode().getIdString(), new Coordinates(59, 18));
    map.put(HELSINKI.getUnLocode().getIdString(), new Coordinates(60, 25));
    map.put(CHICAGO.getUnLocode().getIdString(), new Coordinates(42, -88));
    map.put(TOKYO.getUnLocode().getIdString(), new Coordinates(36, 140));
    map.put(HAMBURG.getUnLocode().getIdString(), new Coordinates(54, 10));
    map.put(SHANGHAI.getUnLocode().getIdString(), new Coordinates(31, 121));
    map.put(ROTTERDAM.getUnLocode().getIdString(), new Coordinates(52, 5));
    map.put(GOTHENBURG.getUnLocode().getIdString(), new Coordinates(58, 12));
    map.put(HANGZOU.getUnLocode().getIdString(), new Coordinates(30, 120));
    map.put(NEWYORK.getUnLocode().getIdString(), new Coordinates(41, -74));
    map.put(DALLAS.getUnLocode().getIdString(), new Coordinates(33, -97));
    map.put(UNKNOWN.getUnLocode().getIdString(), new Coordinates(-90, 0)); // The South Pole.

    for (Map.Entry<String, Coordinates> entry : map.entrySet()) {
      String key = REDIS_KEY_PREFIX + entry.getKey();
      String value = entry.getValue().getLatitude() + "," + entry.getValue().getLongitude();
      try {
        redisCommands.setex(key, TTL_SECONDS, value);
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to store coordinates in Redis: " + entry.getKey(), e);
      }
    }
  }

  public static Coordinates find(Location location) {
    return find(location.getUnLocode());
  }

  public static Coordinates find(UnLocode unLocode) {
    return find(unLocode.getIdString());
  }

  public static Coordinates find(String unLocode) {
    ensureRedisInitialized();
    if (redisAvailable) {
      try {
        String value = redisCommands.get(REDIS_KEY_PREFIX + unLocode);
        if (value != null) {
          String[] parts = value.split(",");
          return new Coordinates(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
        }
        return null;
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to read from Redis, using in-memory cache", e);
      }
    }
    return getInMemoryCoordinates(unLocode);
  }

  /** In-memory fallback data (same as the original static map). */
  private static Coordinates getInMemoryCoordinates(String unLocode) {
    Map<String, Coordinates> map = new HashMap<>();
    map.put(HONGKONG.getUnLocode().getIdString(), new Coordinates(22, 114));
    map.put(MELBOURNE.getUnLocode().getIdString(), new Coordinates(-38, 145));
    map.put(STOCKHOLM.getUnLocode().getIdString(), new Coordinates(59, 18));
    map.put(HELSINKI.getUnLocode().getIdString(), new Coordinates(60, 25));
    map.put(CHICAGO.getUnLocode().getIdString(), new Coordinates(42, -88));
    map.put(TOKYO.getUnLocode().getIdString(), new Coordinates(36, 140));
    map.put(HAMBURG.getUnLocode().getIdString(), new Coordinates(54, 10));
    map.put(SHANGHAI.getUnLocode().getIdString(), new Coordinates(31, 121));
    map.put(ROTTERDAM.getUnLocode().getIdString(), new Coordinates(52, 5));
    map.put(GOTHENBURG.getUnLocode().getIdString(), new Coordinates(58, 12));
    map.put(HANGZOU.getUnLocode().getIdString(), new Coordinates(30, 120));
    map.put(NEWYORK.getUnLocode().getIdString(), new Coordinates(41, -74));
    map.put(DALLAS.getUnLocode().getIdString(), new Coordinates(33, -97));
    map.put(UNKNOWN.getUnLocode().getIdString(), new Coordinates(-90, 0)); // The South Pole.
    return map.get(unLocode);
  }
}
