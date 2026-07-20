package org.eclipse.cargotracker.interfaces;

import static org.eclipse.cargotracker.domain.model.location.Location.UNKNOWN;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.CHICAGO;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.DALLAS;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.GOTHENBURG;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.HAMBURG;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.HANGZOU;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.HELSINKI;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.HONGKONG;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.MELBOURNE;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.NEWYORK;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.ROTTERDAM;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.SHANGHAI;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.STOCKHOLM;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.TOKYO;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.UnLocode;

/**
 * At the moment, coordinates are produced by a simple factory. It may be converted to a repository
 * if coordinates become a domain layer concern.
 */
public class CoordinatesFactory {

  private static final Map<String, Coordinates> COORDINATES_MAP = new ConcurrentHashMap<>();

  private CoordinatesFactory() {
    /* Prevent instantiation. */
  }

  public static Coordinates find(Location location) {
    return find(location.getUnLocode());
  }

  public static Coordinates find(UnLocode unLocode) {
    return find(unLocode.getIdString());
  }

  public static Coordinates find(String unLocode) {
    return COORDINATES_MAP.get(unLocode);
  }

  static {
    // TODO [Clean Code] See if there is a service to get the latitude/longitude data from.
    COORDINATES_MAP.put(HONGKONG.getUnLocode().getIdString(), new Coordinates(22, 114));
    COORDINATES_MAP.put(MELBOURNE.getUnLocode().getIdString(), new Coordinates(-38, 145));
    COORDINATES_MAP.put(STOCKHOLM.getUnLocode().getIdString(), new Coordinates(59, 18));
    COORDINATES_MAP.put(HELSINKI.getUnLocode().getIdString(), new Coordinates(60, 25));
    COORDINATES_MAP.put(CHICAGO.getUnLocode().getIdString(), new Coordinates(42, -88));
    COORDINATES_MAP.put(TOKYO.getUnLocode().getIdString(), new Coordinates(36, 140));
    COORDINATES_MAP.put(HAMBURG.getUnLocode().getIdString(), new Coordinates(54, 10));
    COORDINATES_MAP.put(SHANGHAI.getUnLocode().getIdString(), new Coordinates(31, 121));
    COORDINATES_MAP.put(ROTTERDAM.getUnLocode().getIdString(), new Coordinates(52, 5));
    COORDINATES_MAP.put(GOTHENBURG.getUnLocode().getIdString(), new Coordinates(58, 12));
    COORDINATES_MAP.put(HANGZOU.getUnLocode().getIdString(), new Coordinates(30, 120));
    COORDINATES_MAP.put(NEWYORK.getUnLocode().getIdString(), new Coordinates(41, -74));
    COORDINATES_MAP.put(DALLAS.getUnLocode().getIdString(), new Coordinates(33, -97));
    COORDINATES_MAP.put(UNKNOWN.getUnLocode().getIdString(), new Coordinates(-90, 0)); // The South Pole.
  }
}
