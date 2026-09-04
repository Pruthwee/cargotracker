package org.eclipse.cargotracker.interfaces.booking.sse;

import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * View adapter for displaying a cargo in a realtime tracking context.
 *
 * <p>cz-java-0070: Replaced local in-process EnumMap caches (routingStatusLabels,
 * transportStatusLabels) with Amazon ElastiCache (Redis) to ensure label data is shared across
 * all horizontally-scaled container replicas on EKS. Connection details are injected via
 * REDIS_HOST and REDIS_PORT environment variables (Kubernetes ConfigMap/Secret with
 * IRSA-secured access).
 */
public class RealtimeCargoTrackingViewAdapter {

  // cz-java-0070: Replaced local EnumMap cache with Amazon ElastiCache (Redis) JedisPool.
  // REDIS_HOST and REDIS_PORT are injected via Kubernetes ConfigMap/Secret environment variables.
  private static final JedisPool JEDIS_POOL;

  private static final String ROUTING_STATUS_KEY_PREFIX = "routing_status_label:";
  private static final String TRANSPORT_STATUS_KEY_PREFIX = "transport_status_label:";

  private final Cargo cargo;

  public RealtimeCargoTrackingViewAdapter(Cargo cargo) {
    this.cargo = cargo;
  }

  public String getTrackingId() {
    return cargo.getTrackingId().getIdString();
  }

  public String getRoutingStatus() {
    try (Jedis jedis = JEDIS_POOL.getResource()) {
      return jedis.get(ROUTING_STATUS_KEY_PREFIX + cargo.getDelivery().getRoutingStatus().name());
    }
  }

  public boolean isMisdirected() {
    return cargo.getDelivery().isMisdirected();
  }

  public String getTransportStatus() {
    try (Jedis jedis = JEDIS_POOL.getResource()) {
      return jedis.get(TRANSPORT_STATUS_KEY_PREFIX + cargo.getDelivery().getTransportStatus().name());
    }
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

  static {
    String redisHost = System.getenv("REDIS_HOST") != null ? System.getenv("REDIS_HOST") : "localhost";
    int redisPort = System.getenv("REDIS_PORT") != null ? Integer.parseInt(System.getenv("REDIS_PORT")) : 6379;

    JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(10);
    poolConfig.setMaxIdle(5);
    poolConfig.setMinIdle(1);
    JEDIS_POOL = new JedisPool(poolConfig, redisHost, redisPort);

    // Seed ElastiCache with routing and transport status label data on startup.
    try (Jedis jedis = JEDIS_POOL.getResource()) {
      // cz-java-0070: Routing status labels seeded into Amazon ElastiCache (Redis).
      jedis.set(ROUTING_STATUS_KEY_PREFIX + RoutingStatus.NOT_ROUTED.name(), "Not routed");
      jedis.set(ROUTING_STATUS_KEY_PREFIX + RoutingStatus.ROUTED.name(), "Routed");
      jedis.set(ROUTING_STATUS_KEY_PREFIX + RoutingStatus.MISROUTED.name(), "Misrouted");

      // cz-java-0070: Transport status labels seeded into Amazon ElastiCache (Redis).
      jedis.set(TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.NOT_RECEIVED.name(), "Not received");
      jedis.set(TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.IN_PORT.name(), "In port");
      jedis.set(TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.ONBOARD_CARRIER.name(), "Onboard carrier");
      jedis.set(TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.CLAIMED.name(), "Claimed");
      jedis.set(TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.UNKNOWN.name(), "Unknown");
    }
  }
}
