package org.eclipse.cargotracker.interfaces.booking.sse;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * View adapter for displaying a cargo in a realtime tracking context.
 *
 * <p>Status label lookups are backed by Amazon ElastiCache (Redis) so that all
 * horizontally-scaled EKS pod replicas share a single consistent distributed cache
 * instead of per-JVM EnumMap instances. Connection details are injected via the
 * environment variables REDIS_HOST, REDIS_PORT, and REDIS_PASSWORD
 * (Kubernetes ConfigMap / Secret).
 */
public class RealtimeCargoTrackingViewAdapter {

  private static final Logger logger =
      Logger.getLogger(RealtimeCargoTrackingViewAdapter.class.getName());

  // cz-java-0070: Replaced local in-process EnumMap caches (lines 12 and 14 in original)
  // with Amazon ElastiCache (Redis) backed label lookups. All EKS pod replicas now share
  // the same distributed cache. Connection details are read from environment variables.
  private static final JedisPool JEDIS_POOL;
  private static final String ROUTING_STATUS_KEY_PREFIX  = "label:routing:";
  private static final String TRANSPORT_STATUS_KEY_PREFIX = "label:transport:";

  private final Cargo cargo;

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

    seedStatusLabels();
  }

  private static void seedStatusLabels() {
    try (Jedis jedis = JEDIS_POOL.getResource()) {
      // Routing status labels
      setIfAbsent(jedis, ROUTING_STATUS_KEY_PREFIX + RoutingStatus.NOT_ROUTED.name(), "Not routed");
      setIfAbsent(jedis, ROUTING_STATUS_KEY_PREFIX + RoutingStatus.ROUTED.name(), "Routed");
      setIfAbsent(jedis, ROUTING_STATUS_KEY_PREFIX + RoutingStatus.MISROUTED.name(), "Misrouted");

      // Transport status labels
      setIfAbsent(jedis, TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.NOT_RECEIVED.name(), "Not received");
      setIfAbsent(jedis, TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.IN_PORT.name(), "In port");
      setIfAbsent(jedis, TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.ONBOARD_CARRIER.name(), "Onboard carrier");
      setIfAbsent(jedis, TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.CLAIMED.name(), "Claimed");
      setIfAbsent(jedis, TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.UNKNOWN.name(), "Unknown");
    } catch (Exception e) {
      logger.log(Level.WARNING,
          "Could not seed status labels into Redis cache. "
              + "Ensure REDIS_HOST/REDIS_PORT/REDIS_PASSWORD env vars are set correctly.", e);
    }
  }

  private static void setIfAbsent(Jedis jedis, String key, String value) {
    if (!jedis.exists(key)) {
      jedis.set(key, value);
    }
  }

  private static String getRoutingStatusLabel(RoutingStatus status) {
    try (Jedis jedis = JEDIS_POOL.getResource()) {
      return jedis.get(ROUTING_STATUS_KEY_PREFIX + status.name());
    } catch (Exception e) {
      logger.log(Level.WARNING,
          "Could not retrieve routing status label for " + status + " from Redis cache.", e);
      return status.toString();
    }
  }

  private static String getTransportStatusLabel(TransportStatus status) {
    try (Jedis jedis = JEDIS_POOL.getResource()) {
      return jedis.get(TRANSPORT_STATUS_KEY_PREFIX + status.name());
    } catch (Exception e) {
      logger.log(Level.WARNING,
          "Could not retrieve transport status label for " + status + " from Redis cache.", e);
      return status.toString();
    }
  }

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
}
