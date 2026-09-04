package org.eclipse.cargotracker.interfaces.booking.sse;

import java.util.Map;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.AddrUtil;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;

/**
 * View adapter for displaying a cargo in a realtime tracking context.
 *
 * <p>cz-java-0070: The local static in-memory maps {@code routingStatusLabels} and
 * {@code transportStatusLabels} have been replaced with Amazon ElastiCache for Memcached
 * to support horizontal container scaling on ECS Fargate. Label lookups are served from
 * the distributed cache, with a local fallback for resilience. The Memcached endpoint is
 * injected via the MEMCACHED_ENDPOINT environment variable, populated from AWS SSM
 * Parameter Store in the ECS Fargate task definition.
 */
public class RealtimeCargoTrackingViewAdapter {

  // cz-java-0070: Memcached endpoint injected from AWS SSM Parameter Store via
  // ECS Fargate task definition environment variable MEMCACHED_ENDPOINT.
  private static final String MEMCACHED_ENDPOINT =
      System.getenv("MEMCACHED_ENDPOINT") != null
          ? System.getenv("MEMCACHED_ENDPOINT")
          : "localhost:11211";

  // cz-java-0070: Cache key prefixes for routing and transport status labels in ElastiCache.
  private static final String ROUTING_STATUS_CACHE_PREFIX = "routingStatusLabel:";
  private static final String TRANSPORT_STATUS_CACHE_PREFIX = "transportStatusLabel:";

  // cz-java-0070: TTL (seconds) for status label entries in Memcached; configurable via
  // STATUS_LABELS_CACHE_TTL_SECONDS environment variable populated from SSM Parameter Store.
  private static final int CACHE_TTL_SECONDS;

  // cz-java-0070: Local fallback maps used when Memcached is unavailable, ensuring
  // resilience during cache outages without blocking application startup.
  private static final Map<RoutingStatus, String> ROUTING_STATUS_FALLBACK;
  private static final Map<TransportStatus, String> TRANSPORT_STATUS_FALLBACK;

  private final Cargo cargo;

  static {
    int ttl = 3600; // default 1 hour
    String ttlEnv = System.getenv("STATUS_LABELS_CACHE_TTL_SECONDS");
    if (ttlEnv != null && !ttlEnv.isEmpty()) {
      try {
        ttl = Integer.parseInt(ttlEnv);
      } catch (NumberFormatException ignored) {
        // use default
      }
    }
    CACHE_TTL_SECONDS = ttl;

    // cz-java-0070: Build local fallback maps for routing and transport status labels.
    // These are only used when Memcached is unreachable.
    java.util.EnumMap<RoutingStatus, String> routingFallback =
        new java.util.EnumMap<>(RoutingStatus.class);
    routingFallback.put(RoutingStatus.NOT_ROUTED, "Not routed");
    routingFallback.put(RoutingStatus.ROUTED, "Routed");
    routingFallback.put(RoutingStatus.MISROUTED, "Misrouted");
    ROUTING_STATUS_FALLBACK = java.util.Collections.unmodifiableMap(routingFallback);

    java.util.EnumMap<TransportStatus, String> transportFallback =
        new java.util.EnumMap<>(TransportStatus.class);
    transportFallback.put(TransportStatus.NOT_RECEIVED, "Not received");
    transportFallback.put(TransportStatus.IN_PORT, "In port");
    transportFallback.put(TransportStatus.ONBOARD_CARRIER, "Onboard carrier");
    transportFallback.put(TransportStatus.CLAIMED, "Claimed");
    transportFallback.put(TransportStatus.UNKNOWN, "Unknown");
    TRANSPORT_STATUS_FALLBACK = java.util.Collections.unmodifiableMap(transportFallback);

    // cz-java-0070: Pre-warm ElastiCache Memcached with status label entries on startup.
    // The Memcached endpoint is resolved from the MEMCACHED_ENDPOINT environment variable
    // which is injected by the ECS Fargate task definition from AWS SSM Parameter Store.
    try {
      MemcachedClient memcachedClient = new MemcachedClient(AddrUtil.getAddresses(MEMCACHED_ENDPOINT));
      for (Map.Entry<RoutingStatus, String> entry : ROUTING_STATUS_FALLBACK.entrySet()) {
        memcachedClient.set(ROUTING_STATUS_CACHE_PREFIX + entry.getKey().name(), CACHE_TTL_SECONDS, entry.getValue());
      }
      for (Map.Entry<TransportStatus, String> entry : TRANSPORT_STATUS_FALLBACK.entrySet()) {
        memcachedClient.set(TRANSPORT_STATUS_CACHE_PREFIX + entry.getKey().name(), CACHE_TTL_SECONDS, entry.getValue());
      }
      memcachedClient.shutdown();
    } catch (Exception e) {
      System.err.println("[RealtimeCargoTrackingViewAdapter] Warning: Could not connect to Memcached at "
          + MEMCACHED_ENDPOINT + ". Using local fallback maps. Error: " + e.getMessage());
    }
  }

  public RealtimeCargoTrackingViewAdapter(Cargo cargo) {
    this.cargo = cargo;
  }

  public String getTrackingId() {
    return cargo.getTrackingId().getIdString();
  }

  /**
   * cz-java-0070: Retrieves routing status label from ElastiCache Memcached.
   * Falls back to local map if Memcached is unavailable.
   */
  public String getRoutingStatus() {
    RoutingStatus status = cargo.getDelivery().getRoutingStatus();
    return getRoutingStatusLabel(status);
  }

  public boolean isMisdirected() {
    return cargo.getDelivery().isMisdirected();
  }

  /**
   * cz-java-0070: Retrieves transport status label from ElastiCache Memcached.
   * Falls back to local map if Memcached is unavailable.
   */
  public String getTransportStatus() {
    TransportStatus status = cargo.getDelivery().getTransportStatus();
    return getTransportStatusLabel(status);
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

  /**
   * cz-java-0070: Looks up routing status label from Amazon ElastiCache for Memcached.
   * Falls back to the local map if Memcached is unavailable.
   */
  private static String getRoutingStatusLabel(RoutingStatus status) {
    try {
      MemcachedClient memcachedClient = new MemcachedClient(AddrUtil.getAddresses(MEMCACHED_ENDPOINT));
      Object cached = memcachedClient.get(ROUTING_STATUS_CACHE_PREFIX + status.name());
      memcachedClient.shutdown();
      if (cached instanceof String) {
        return (String) cached;
      }
    } catch (Exception e) {
      System.err.println("[RealtimeCargoTrackingViewAdapter] Warning: Memcached lookup failed for "
          + "routing status '" + status + "'. Using local fallback. Error: " + e.getMessage());
    }
    return ROUTING_STATUS_FALLBACK.get(status);
  }

  /**
   * cz-java-0070: Looks up transport status label from Amazon ElastiCache for Memcached.
   * Falls back to the local map if Memcached is unavailable.
   */
  private static String getTransportStatusLabel(TransportStatus status) {
    try {
      MemcachedClient memcachedClient = new MemcachedClient(AddrUtil.getAddresses(MEMCACHED_ENDPOINT));
      Object cached = memcachedClient.get(TRANSPORT_STATUS_CACHE_PREFIX + status.name());
      memcachedClient.shutdown();
      if (cached instanceof String) {
        return (String) cached;
      }
    } catch (Exception e) {
      System.err.println("[RealtimeCargoTrackingViewAdapter] Warning: Memcached lookup failed for "
          + "transport status '" + status + "'. Using local fallback. Error: " + e.getMessage());
    }
    return TRANSPORT_STATUS_FALLBACK.get(status);
  }
}
