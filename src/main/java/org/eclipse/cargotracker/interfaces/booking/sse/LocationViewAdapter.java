package org.eclipse.cargotracker.interfaces.booking.sse;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.interfaces.Coordinates;
import org.eclipse.cargotracker.interfaces.CoordinatesFactory;

/** View adapter for displaying a location in a real-time tracking context. */
@Dependent
public class LocationViewAdapter {

  private final Location location;

  @Inject private CoordinatesFactory coordinatesFactory;

  public LocationViewAdapter() {
    this.location = null;
  }

  public LocationViewAdapter(Location location) {
    this.location = location;
  }

  public String getUnLocode() {
    return location.getUnLocode().getIdString();
  }

  public String getName() {
    return location.getName();
  }

  public Coordinates getCoordinates() {
    return coordinatesFactory.find(location);
  }
}
