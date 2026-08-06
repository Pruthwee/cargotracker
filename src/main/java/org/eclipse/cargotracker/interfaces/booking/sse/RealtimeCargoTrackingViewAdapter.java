package org.eclipse.cargotracker.interfaces.booking.sse;

import java.util.Map;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;

/**
 * View adapter for displaying a cargo in a realtime tracking context.
 *
 * <p>Cloud-readiness fix (cr-java-0067 – In-Memory Caching Without TTL): The previously static
 * in-memory {@code routingStatusLabels} and {@code transportStatusLabels} maps have been replaced
 * with a Redis-backed cache via {@link StatusLabelsRedisCache}. In a multi-instance cloud
 * deployment every application node shares a single, consistent view of the status labels stored
 * in Amazon ElastiCache for Redis with a configurable TTL, eliminating unbounded memory growth
 * and stale data inconsistencies across instances. When Redis is not configured (e.g., local
 * development) the adapter falls back to the in-process maps transparently.
 */
public class RealtimeCargoTrackingViewAdapter {

  /**
   * Fallback in-process routing-status label map used when Redis is not available.
   * This map is also used to seed Redis on first access.
   */
  private static final Map<RoutingStatus, String> ROUTING_STATUS_LABELS =
      StatusLabelsRedisCache.buildDefaultRoutingStatusLabels();

  /**
   * Fallback in-process transport-status label map used when Redis is not available.
   * This map is also used to seed Redis on first access.
   */
  private static final Map<TransportStatus, String> TRANSPORT_STATUS_LABELS =
      StatusLabelsRedisCache.buildDefaultTransportStatusLabels();

  private final Cargo cargo;

  public RealtimeCargoTrackingViewAdapter(Cargo cargo) {
    this.cargo = cargo;
  }

  public String getTrackingId() {
    return cargo.getTrackingId().getIdString();
  }

  /**
   * Returns the human-readable routing status label.
   *
   * <p>Delegates to {@link StatusLabelsRedisCache} when Amazon ElastiCache is configured
   * (via the {@code REDIS_HOST} environment variable). Falls back to the in-process
   * {@code ROUTING_STATUS_LABELS} map when Redis is not available.
   */
  public String getRoutingStatus() {
    return StatusLabelsRedisCache.getInstance()
        .getRoutingStatusLabel(cargo.getDelivery().getRoutingStatus(), ROUTING_STATUS_LABELS);
  }

  public boolean isMisdirected() {
    return cargo.getDelivery().isMisdirected();
  }

  /**
   * Returns the human-readable transport status label.
   *
   * <p>Delegates to {@link StatusLabelsRedisCache} when Amazon ElastiCache is configured
   * (via the {@code REDIS_HOST} environment variable). Falls back to the in-process
   * {@code TRANSPORT_STATUS_LABELS} map when Redis is not available.
   */
  public String getTransportStatus() {
    return StatusLabelsRedisCache.getInstance()
        .getTransportStatusLabel(cargo.getDelivery().getTransportStatus(), TRANSPORT_STATUS_LABELS);
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
