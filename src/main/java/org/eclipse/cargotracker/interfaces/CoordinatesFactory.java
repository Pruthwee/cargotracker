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
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * At the moment, coordinates are produced by a simple factory. It may be converted to a repository
 * if coordinates become a domain layer concern.
 */
public class CoordinatesFactory {

  private static final String REDIS_HOST = System.getenv("REDIS_HOST") != null ? System.getenv("REDIS_HOST") : "localhost";
  private static final RedisClient redisClient = RedisClient.create("redis://" + REDIS_HOST);
  private static final StatefulRedisConnection<String, String> connection = redisClient.connect();
  private static final RedisCommands<String, String> syncCommands = connection.sync();

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
    String coords = syncCommands.get("coords:" + unLocode);
    if (coords != null) {
      String[] parts = coords.split(",");
      return new Coordinates(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
    
    // Fallback to initial load if not in cache (simulating the original map)
    Coordinates coord = getInitialCoordinate(unLocode);
    if (coord != null) {
      syncCommands.setex("coords:" + unLocode, 3600, coord.getLatitude() + "," + coord.getLongitude());
    }
    return coord;
  }

  private static Coordinates getInitialCoordinate(String unLocode) {
    // This mimics the original static map for initial population
    if (HONGKONG.getUnLocode().getIdString().equals(unLocode)) return new Coordinates(22, 114);
    if (MELBOURNE.getUnLocode().getIdString().equals(unLocode)) return new Coordinates(-38, 145);
    if (STOCKHOLM.getUnLocode().getIdString().equals(unLocode)) return new Coordinates(59, 18);
    if (HELSINKI.getUnLocode().getIdString().equals(unLocode)) return new Coordinates(60, 25);
    if (CHICAGO.getUnLocode().getIdString().equals(unLocode)) return new Coordinates(42, -88);
    if (TOKYO.getUnLocode().getIdString().equals(unLocode)) return new Coordinates(36, 140);
    if (HAMBURG.getUnLocode().getIdString().equals(unLocode)) return new Coordinates(54, 10);
    if (SHANGHAI.getUnLocode().getIdString().equals(unLocode)) return new Coordinates(31, 121);
    if (ROTTERDAM.getUnLocode().getIdString().equals(unLocode)) return new Coordinates(52, 5);
    if (GOTHENBURG.getUnLocode().getIdString().equals(unLocode)) return new Coordinates(58, 12);
    if (HANGZOU.getUnLocode().getIdString().equals(unLocode)) return new Coordinates(30, 120);
    if (NEWYORK.getUnLocode().getIdString().equals(unLocode)) return new Coordinates(41, -74);
    if (DALLAS.getUnLocode().getIdString().equals(unLocode)) return new Coordinates(33, -97);
    if (UNKNOWN.getUnLocode().getIdString().equals(unLocode)) return new Coordinates(-90, 0);
    return null;
  }
}
