package org.eclipse.cargotracker.interfaces.booking.sse;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/** View adapter for displaying a cargo in a realtime tracking context. */
public class RealtimeCargoTrackingViewAdapter {

  private static final String REDIS_KEY_PREFIX = "labels:";
  private static final int TTL_SECONDS = 3600; // 1 hour

  private static RedisClient redisClient;
  private static StatefulRedisConnection<String, String> redisConnection;
  private static RedisCommands<String, String> redisCommands;
  private static boolean redisAvailable = false;
  private static final Object initLock = new Object();
  private static final Logger logger = Logger.getLogger(RealtimeCargoTrackingViewAdapter.class.getName());

  private final Cargo cargo;

  public RealtimeCargoTrackingViewAdapter(Cargo cargo) {
    this.cargo = cargo;
  }

  public String getTrackingId() {
    return cargo.getTrackingId().getIdString();
  }

  public String getRoutingStatus() {
    return getRoutingStatusLabel(cargo.getDelivery().getRoutingStatus());
  }

  public boolean isMisdirected() {
    return cargo.getDelivery().isMisdirected();
  }

  public String getTransportStatus() {
    return getTransportStatusLabel(cargo.getDelivery().getTransportStatus());
  }

  public boolean isAtDestination() {
    return cargo.getDelivery().isUnloadedAtDestination();
  }

  public LocationViewAdapter getOrigin() {
    return new LocationViewAdapter(cargo.getOrigin());
  }

  public LocationViewAdapter getLastKnownLocation() {
    return new LocationViewAdapter(cargo.getDelivery().getLastKnownLocation());
  }

  public LocationViewAdapter getLocation() {
    return cargo.getDelivery().getTransportStatus() == TransportStatus.NOT_RECEIVED
        ? getOrigin()
        : getLastKnownLocation();
  }

  public String getStatusCode() {
    RoutingStatus routingStatus = cargo.getDelivery().getRoutingStatus();

    if (routingStatus == RoutingStatus.NOT_ROUTED || routingStatus == RoutingStatus.MISROUTED) {
      return routingStatus.toString();
    }

    if (cargo.getDelivery().isMisdirected()) {
      return "MISDIRECTED";
    }

    if (cargo.getDelivery().isUnloadedAtDestination()) {
      return "AT_DESTINATION";
    }

    return cargo.getDelivery().getTransportStatus().toString();
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
        logger.log(Level.WARNING, "ELASTICACHE_ENDPOINT not set, using in-memory labels");
        return;
      }
      try {
        redisClient = RedisClient.create(RedisURI.create(redisEndpoint, 6379));
        redisConnection = redisClient.connect();
        redisCommands = redisConnection.sync();
        redisCommands.ping();
        redisAvailable = true;
        logger.log(Level.INFO, "Connected to Redis at {0} for status labels", redisEndpoint);
        populateRedis();
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to connect to Redis, using in-memory labels", e);
      }
    }
  }

  /** Populates Redis with label data. */
  private static void populateRedis() {
    Map<RoutingStatus, String> routingLabels = new EnumMap<>(RoutingStatus.class);
    routingLabels.put(RoutingStatus.NOT_ROUTED, "Not routed");
    routingLabels.put(RoutingStatus.ROUTED, "Routed");
    routingLabels.put(RoutingStatus.MISROUTED, "Misrouted");

    Map<TransportStatus, String> transportLabels = new EnumMap<>(TransportStatus.class);
    transportLabels.put(TransportStatus.NOT_RECEIVED, "Not received");
    transportLabels.put(TransportStatus.IN_PORT, "In port");
    transportLabels.put(TransportStatus.ONBOARD_CARRIER, "Onboard carrier");
    transportLabels.put(TransportStatus.CLAIMED, "Claimed");
    transportLabels.put(TransportStatus.UNKNOWN, "Unknown");

    for (Map.Entry<RoutingStatus, String> entry : routingLabels.entrySet()) {
      String key = REDIS_KEY_PREFIX + "routing:" + entry.getKey().name();
      try {
        redisCommands.setex(key, TTL_SECONDS, entry.getValue());
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to store routing label in Redis: " + entry.getKey(), e);
      }
    }

    for (Map.Entry<TransportStatus, String> entry : transportLabels.entrySet()) {
      String key = REDIS_KEY_PREFIX + "transport:" + entry.getKey().name();
      try {
        redisCommands.setex(key, TTL_SECONDS, entry.getValue());
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to store transport label in Redis: " + entry.getKey(), e);
      }
    }
  }

  private static String getRoutingStatusLabel(RoutingStatus status) {
    ensureRedisInitialized();
    if (redisAvailable) {
      try {
        String value = redisCommands.get(REDIS_KEY_PREFIX + "routing:" + status.name());
        if (value != null) {
          return value;
        }
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to read routing label from Redis, using in-memory", e);
      }
    }
    return getInMemoryRoutingLabel(status);
  }

  private static String getTransportStatusLabel(TransportStatus status) {
    ensureRedisInitialized();
    if (redisAvailable) {
      try {
        String value = redisCommands.get(REDIS_KEY_PREFIX + "transport:" + status.name());
        if (value != null) {
          return value;
        }
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to read transport label from Redis, using in-memory", e);
      }
    }
    return getInMemoryTransportLabel(status);
  }

  /** In-memory fallback data (same as the original static maps). */
  private static String getInMemoryRoutingLabel(RoutingStatus status) {
    switch (status) {
      case NOT_ROUTED: return "Not routed";
      case ROUTED: return "Routed";
      case MISROUTED: return "Misrouted";
      default: return status.toString();
    }
  }

  /** In-memory fallback data (same as the original static maps). */
  private static String getInMemoryTransportLabel(TransportStatus status) {
    switch (status) {
      case NOT_RECEIVED: return "Not received";
      case IN_PORT: return "In port";
      case ONBOARD_CARRIER: return "Onboard carrier";
      case CLAIMED: return "Claimed";
      case UNKNOWN: return "Unknown";
      default: return status.toString();
    }
  }
}
