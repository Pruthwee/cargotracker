package org.eclipse.cargotracker.interfaces.booking.sse;

import java.util.EnumMap;
import java.util.Map;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/** View adapter for displaying a cargo in a realtime tracking context. */
public class RealtimeCargoTrackingViewAdapter {

  // Redis-backed distributed cache replacing the former in-process static EnumMaps (cz-java-0070).
  // Connection details are supplied via environment variables (REDIS_HOST, REDIS_PORT) injected by
  // Kubernetes ConfigMaps and Secrets, enabling safe horizontal scaling on EKS with
  // IRSA-secured Amazon ElastiCache (Redis) access.
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

    JEDIS_POOL = new JedisPool(new JedisPoolConfig(), redisHost, redisPort);

    // Seed routing status labels into Redis (only if not already present)
    Map<RoutingStatus, String> routingStatusLabels = new EnumMap<>(RoutingStatus.class);
    routingStatusLabels.put(RoutingStatus.NOT_ROUTED, "Not routed");
    routingStatusLabels.put(RoutingStatus.ROUTED, "Routed");
    routingStatusLabels.put(RoutingStatus.MISROUTED, "Misrouted");

    // Seed transport status labels into Redis (only if not already present)
    Map<TransportStatus, String> transportStatusLabels = new EnumMap<>(TransportStatus.class);
    transportStatusLabels.put(TransportStatus.NOT_RECEIVED, "Not received");
    transportStatusLabels.put(TransportStatus.IN_PORT, "In port");
    transportStatusLabels.put(TransportStatus.ONBOARD_CARRIER, "Onboard carrier");
    transportStatusLabels.put(TransportStatus.CLAIMED, "Claimed");
    transportStatusLabels.put(TransportStatus.UNKNOWN, "Unknown");

    try (Jedis jedis = JEDIS_POOL.getResource()) {
      for (Map.Entry<RoutingStatus, String> entry : routingStatusLabels.entrySet()) {
        String key = ROUTING_STATUS_KEY_PREFIX + entry.getKey().name();
        if (jedis.get(key) == null) {
          jedis.set(key, entry.getValue());
        }
      }
      for (Map.Entry<TransportStatus, String> entry : transportStatusLabels.entrySet()) {
        String key = TRANSPORT_STATUS_KEY_PREFIX + entry.getKey().name();
        if (jedis.get(key) == null) {
          jedis.set(key, entry.getValue());
        }
      }
    }
  }
}
