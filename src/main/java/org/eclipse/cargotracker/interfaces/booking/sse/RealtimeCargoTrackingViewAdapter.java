package org.eclipse.cargotracker.interfaces.booking.sse;

import java.util.EnumMap;
import java.util.Map;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/** View adapter for displaying a cargo in a realtime tracking context. */
public class RealtimeCargoTrackingViewAdapter {

  private static final String REDIS_HOST = System.getenv("REDIS_HOST") != null ? System.getenv("REDIS_HOST") : "localhost";
  private static final RedisClient redisClient = RedisClient.create("redis://" + REDIS_HOST);
  private static final StatefulRedisConnection<String, String> connection = redisClient.connect();
  private static final RedisCommands<String, String> syncCommands = connection.sync();

  private final Cargo cargo;

  public RealtimeCargoTrackingViewAdapter(Cargo cargo) {
    this.cargo = cargo;
  }

  public String getTrackingId() {
    return cargo.getTrackingId().getIdString();
  }

  public String getRoutingStatus() {
    String label = syncCommands.get("label:routing:" + cargo.getDelivery().getRoutingStatus());
    if (label == null) {
      label = getRoutingStatusLabel(cargo.getDelivery().getRoutingStatus());
      syncCommands.setex("label:routing:" + cargo.getDelivery().getRoutingStatus(), 3600, label);
    }
    return label;
  }

  public boolean isMisdirected() {
    return cargo.getDelivery().isMisdirected();
  }

  public String getTransportStatus() {
    String label = syncCommands.get("label:transport:" + cargo.getDelivery().getTransportStatus());
    if (label == null) {
      label = getTransportStatusLabel(cargo.getDelivery().getTransportStatus());
      syncCommands.setex("label:transport:" + cargo.getDelivery().getTransportStatus(), 3600, label);
    }
    return label;
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

  private String getRoutingStatusLabel(RoutingStatus status) {
    switch (status) {
      case NOT_ROUTED: return "Not routed";
      case ROUTED: return "Routed";
      case MISROUTED: return "Misrouted";
      default: return status.toString();
    }
  }

  private String getTransportStatusLabel(TransportStatus status) {
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
