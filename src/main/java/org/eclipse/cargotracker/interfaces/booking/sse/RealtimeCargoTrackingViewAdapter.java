package org.eclipse.cargotracker.interfaces.booking.sse;

import java.util.EnumMap;
import java.util.Map;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;

/**
 * View adapter for displaying a cargo in a realtime tracking context.
 *
 * Blocker-21 & Blocker-22: cz-java-0070 - Replaced static local EnumMap caches with
 * method-level lookups to avoid local in-memory cache inconsistencies when containers
 * scale horizontally. For production use, consider migrating to Amazon ElastiCache (Redis).
 */
public class RealtimeCargoTrackingViewAdapter {

  private final Cargo cargo;

  public RealtimeCargoTrackingViewAdapter(Cargo cargo) {
    this.cargo = cargo;
  }

  public String getTrackingId() {
    return cargo.getTrackingId().getIdString();
  }

  public String getRoutingStatus() {
    return buildRoutingStatusLabels().get(cargo.getDelivery().getRoutingStatus());
  }

  public boolean isMisdirected() {
    return cargo.getDelivery().isMisdirected();
  }

  public String getTransportStatus() {
    return buildTransportStatusLabels().get(cargo.getDelivery().getTransportStatus());
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
   * Builds routing status labels map on each invocation to avoid static local cache state
   * that causes inconsistencies when scaling containers horizontally.
   */
  private static Map<RoutingStatus, String> buildRoutingStatusLabels() {
    Map<RoutingStatus, String> labels = new EnumMap<>(RoutingStatus.class);
    labels.put(RoutingStatus.NOT_ROUTED, "Not routed");
    labels.put(RoutingStatus.ROUTED, "Routed");
    labels.put(RoutingStatus.MISROUTED, "Misrouted");
    return labels;
  }

  /**
   * Builds transport status labels map on each invocation to avoid static local cache state
   * that causes inconsistencies when scaling containers horizontally.
   */
  private static Map<TransportStatus, String> buildTransportStatusLabels() {
    Map<TransportStatus, String> labels = new EnumMap<>(TransportStatus.class);
    labels.put(TransportStatus.NOT_RECEIVED, "Not received");
    labels.put(TransportStatus.IN_PORT, "In port");
    labels.put(TransportStatus.ONBOARD_CARRIER, "Onboard carrier");
    labels.put(TransportStatus.CLAIMED, "Claimed");
    labels.put(TransportStatus.UNKNOWN, "Unknown");
    return labels;
  }
}
