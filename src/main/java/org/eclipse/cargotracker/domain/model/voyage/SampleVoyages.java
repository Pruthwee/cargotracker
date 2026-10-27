package org.eclipse.cargotracker.domain.model.voyage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * Sample carrier movements, for demo/test purposes.
 *
 * <p>Replaced the static mutable {@code Map} with Redis-backed storage via ElastiCache for Redis,
 * enabling synchronized access across distributed application instances. Falls back to in-memory
 * data when Redis is unavailable.
 */
public class SampleVoyages {

  private static final String REDIS_KEY_PREFIX = "sample_voyage:";
  private static final int TTL_SECONDS = 3600; // 1 hour

  private static RedisClient redisClient;
  private static StatefulRedisConnection<String, String> redisConnection;
  private static RedisCommands<String, String> redisCommands;
  private static boolean redisAvailable = false;
  private static final Object initLock = new Object();
  private static final Logger logger = Logger.getLogger(SampleVoyages.class.getName());

  // Sample voyage definitions (same as before, used to populate Redis)
  public static final Voyage CM001 =
      createVoyage("CM001", SampleLocations.STOCKHOLM, SampleLocations.HAMBURG);
  public static final Voyage CM002 =
      createVoyage("CM002", SampleLocations.HAMBURG, SampleLocations.HONGKONG);
  public static final Voyage CM003 =
      createVoyage("CM003", SampleLocations.HONGKONG, SampleLocations.NEWYORK);
  public static final Voyage CM004 =
      createVoyage("CM004", SampleLocations.NEWYORK, SampleLocations.CHICAGO);
  public static final Voyage CM005 =
      createVoyage("CM005", SampleLocations.CHICAGO, SampleLocations.HAMBURG);
  public static final Voyage CM006 =
      createVoyage("CM006", SampleLocations.HAMBURG, SampleLocations.HANGZOU);
  public static final Voyage v100 =
      new Voyage.Builder(new VoyageNumber("V100"), SampleLocations.HONGKONG)
          .addMovement(
              SampleLocations.TOKYO,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(3).plusHours(6),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(5).plusHours(18))
          .addMovement(
              SampleLocations.NEWYORK,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(6).plusHours(11),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(9).plusHours(11))
          .build();
  public static final Voyage v200 =
      new Voyage.Builder(new VoyageNumber("V200"), SampleLocations.TOKYO)
          .addMovement(
              SampleLocations.NEWYORK,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(6).plusHours(14),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(8).plusHours(7))
          .addMovement(
              SampleLocations.CHICAGO,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(10).plusHours(21),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(14).plusHours(2))
          .addMovement(
              SampleLocations.STOCKHOLM,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(14).plusHours(1),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(16).plusHours(23))
          .build();
  public static final Voyage v300 =
      new Voyage.Builder(new VoyageNumber("V300"), SampleLocations.TOKYO)
          .addMovement(
              SampleLocations.ROTTERDAM,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(8).plusHours(8),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(11).plusHours(16))
          .addMovement(
              SampleLocations.HAMBURG,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(11).plusHours(4),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(12).plusHours(17))
          .addMovement(
              SampleLocations.MELBOURNE,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(14).plusHours(9),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(18).plusHours(10))
          .addMovement(
              SampleLocations.TOKYO,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(19).plusHours(17),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(21).plusHours(4))
          .build();
  public static final Voyage v400 =
      new Voyage.Builder(new VoyageNumber("V400"), SampleLocations.HAMBURG)
          .addMovement(
              SampleLocations.STOCKHOLM,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(14).plusHours(9),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(15).plusHours(18))
          .addMovement(
              SampleLocations.HELSINKI,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(15).plusHours(11),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(16).plusHours(16))
          .addMovement(
              SampleLocations.HAMBURG,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(20).plusHours(18),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(22).plusHours(9))
          .build();
  /**
   * Voyage number 0100S (by ship)
   *
   * <p>Hongkong - Hangzou - Tokyo - Melbourne - New York
   */
  public static final Voyage HONGKONG_TO_NEW_YORK =
      new Voyage.Builder(new VoyageNumber("0100S"), SampleLocations.HONGKONG)
          .addMovement(
              SampleLocations.HANGZOU,
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(1).plusHours(12),
              LocalDateTime.now()
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(2)
                  .plusHours(18))
          .addMovement(
              SampleLocations.TOKYO,
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(4).plusHours(12),
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(6).plusHours(18))
          .addMovement(
              SampleLocations.MELBOURNE,
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(8).plusHours(12),
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(12).plusHours(18))
          .addMovement(
              SampleLocations.NEWYORK,
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(14).plusHours(12),
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(16).plusHours(18))
          .build();

  /**
   * Voyage number 0200S (by ship)
   *
   * <p>New York - Dallas
   */
  public static final Voyage NEW_YORK_TO_DALLAS =
      new Voyage.Builder(new VoyageNumber("0200S"), SampleLocations.NEWYORK)
          .addMovement(
              SampleLocations.DALLAS,
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(18).plusHours(10),
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(19).plusHours(14))
          .build();

  /**
   * Voyage number 0300S (by ship)
   *
   * <p>Dallas - Helsinki
   */
  public static final Voyage DALLAS_TO_HELSINKI =
      new Voyage.Builder(new VoyageNumber("0300S"), SampleLocations.DALLAS)
          .addMovement(
              SampleLocations.HELSINKI,
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(20).plusHours(10),
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(22).plusHours(14))
          .build();

  /**
   * Voyage number 0400S (by ship)
   *
   * <p>Helsinki - Hongkong
   */
  public static final Voyage HELSINKI_TO_HONGKONG =
      new Voyage.Builder(new VoyageNumber("0400S"), SampleLocations.HELSINKI)
          .addMovement(
              SampleLocations.HONGKONG,
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(24).plusHours(10),
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(26).plusHours(14))
          .build();

  /**
   * Voyage number 0500S (by ship)
   *
   * <p>Dallas - Helsinki (alternative route)
   */
  public static final Voyage DALLAS_TO_HELSINKI_ALT =
      new Voyage.Builder(new VoyageNumber("0500S"), SampleLocations.DALLAS)
          .addMovement(
              SampleLocations.HELSINKI,
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(28).plusHours(10),
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(30).plusHours(14))
          .build();

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
        logger.log(Level.WARNING, "ELASTICACHE_ENDPOINT not set, falling back to in-memory sample data");
        return;
      }
      try {
        redisClient = RedisClient.create(RedisURI.create(redisEndpoint, 6379));
        redisConnection = redisClient.connect();
        redisCommands = redisConnection.sync();
        redisCommands.ping();
        redisAvailable = true;
        logger.log(Level.INFO, "Connected to Redis at {0}", redisEndpoint);
        populateRedis();
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to connect to Redis, falling back to in-memory sample data", e);
      }
    }
  }

  /** Populates Redis with sample voyage data. */
  private static void populateRedis() {
    Map<VoyageNumber, Voyage> all = getInMemoryAll();
    for (Map.Entry<VoyageNumber, Voyage> entry : all.entrySet()) {
      String key = REDIS_KEY_PREFIX + entry.getKey().toString();
      try {
        String serialized = serializeVoyage(entry.getValue());
        redisCommands.setex(key, TTL_SECONDS, serialized);
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to store voyage in Redis: " + entry.getKey(), e);
      }
    }
  }

  /**
   * Returns all sample voyages. Uses Redis as the backing store when available, falling back to
   * in-memory data when Redis is unreachable.
   */
  public static List<Voyage> getAll() {
    ensureRedisInitialized();
    if (redisAvailable) {
      return getAllFromRedis();
    }
    return new ArrayList<>(getInMemoryAll().values());
  }

  /**
   * Looks up a sample voyage by voyage number. Uses Redis as the backing store when available,
   * falling back to in-memory data when Redis is unreachable.
   */
  public static Voyage lookup(VoyageNumber voyageNumber) {
    ensureRedisInitialized();
    if (redisAvailable) {
      return lookupFromRedis(voyageNumber);
    }
    return getInMemoryAll().get(voyageNumber);
  }

  private static List<Voyage> getAllFromRedis() {
    try {
      List<Voyage> voyages = new ArrayList<>();
      List<String> keys = redisCommands.keys(REDIS_KEY_PREFIX + "*");
      for (String key : keys) {
        String serialized = redisCommands.get(key);
        if (serialized != null) {
          voyages.add(deserializeVoyage(serialized));
        }
      }
      return voyages;
    } catch (Exception e) {
      logger.log(Level.WARNING, "Failed to read from Redis, falling back to in-memory", e);
      return new ArrayList<>(getInMemoryAll().values());
    }
  }

  private static Voyage lookupFromRedis(VoyageNumber voyageNumber) {
    try {
      String serialized = redisCommands.get(REDIS_KEY_PREFIX + voyageNumber.toString());
      if (serialized != null) {
        return deserializeVoyage(serialized);
      }
      return null;
    } catch (Exception e) {
      logger.log(Level.WARNING, "Failed to read from Redis, falling back to in-memory", e);
      return getInMemoryAll().get(voyageNumber);
    }
  }

  /** In-memory fallback data (same as the original static map). */
  private static Map<VoyageNumber, Voyage> getInMemoryAll() {
    Map<VoyageNumber, Voyage> all = new HashMap<>();
    for (java.lang.reflect.Field field : SampleVoyages.class.getDeclaredFields()) {
      if (field.getType().equals(Voyage.class)) {
        try {
          Voyage voyage = (Voyage) field.get(null);
          all.put(voyage.getVoyageNumber(), voyage);
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      }
    }
    return all;
  }

  /** Serializes a Voyage using Java serialization for Redis storage. */
  private static String serializeVoyage(Voyage voyage) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos)) {
      oos.writeObject(voyage);
      return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
    } catch (IOException e) {
      throw new RuntimeException("Failed to serialize voyage", e);
    }
  }

  /** Deserializes a Voyage from a Base64-encoded Java serialization string. */
  private static Voyage deserializeVoyage(String data) {
    try (ByteArrayInputStream bais = new ByteArrayInputStream(java.util.Base64.getDecoder().decode(data));
        ObjectInputStream ois = new ObjectInputStream(bais)) {
      return (Voyage) ois.readObject();
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeException("Failed to deserialize voyage", e);
    }
  }

  private static Voyage createVoyage(String id, Location from, Location to) {
    return new Voyage(
        new VoyageNumber(id),
        new Schedule(
            Collections.singletonList(
                new CarrierMovement(from, to, LocalDateTime.now(), LocalDateTime.now()))));
  }
}
