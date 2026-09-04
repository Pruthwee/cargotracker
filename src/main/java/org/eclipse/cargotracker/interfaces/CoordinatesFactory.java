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
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.AddrUtil;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.UnLocode;

/**
 * At the moment, coordinates are produced by a simple factory. It may be converted to a repository
 * if coordinates become a domain layer concern.
 *
 * <p>cz-java-0070: The local in-memory COORDINATES_MAP cache has been replaced with Amazon
 * ElastiCache for Memcached to support horizontal container scaling on ECS Fargate. The Memcached
 * endpoint is injected via the MEMCACHED_ENDPOINT environment variable, which is populated from
 * AWS SSM Parameter Store in the ECS Fargate task definition. A local fallback map is retained
 * for resilience when the distributed cache is unavailable.
 */
public class CoordinatesFactory {

  // cz-java-0070: Memcached endpoint injected from AWS SSM Parameter Store via
  // ECS Fargate task definition environment variable MEMCACHED_ENDPOINT.
  private static final String MEMCACHED_ENDPOINT =
      System.getenv("MEMCACHED_ENDPOINT") != null
          ? System.getenv("MEMCACHED_ENDPOINT")
          : "localhost:11211";

  // cz-java-0070: Cache key prefix for coordinates stored in ElastiCache Memcached.
  private static final String CACHE_KEY_PREFIX = "coordinates:";

  // cz-java-0070: TTL (seconds) for coordinate entries in Memcached; configurable via
  // COORDINATES_CACHE_TTL_SECONDS environment variable populated from SSM Parameter Store.
  private static final int CACHE_TTL_SECONDS;

  // cz-java-0070: Local fallback map used when Memcached is unavailable, ensuring
  // resilience during cache outages without blocking application startup.
  private static final Map<String, Coordinates> LOCAL_FALLBACK_MAP;

  private CoordinatesFactory() {
    /* Prevent instantiation. */
  }

  static {
    int ttl = 3600; // default 1 hour
    String ttlEnv = System.getenv("COORDINATES_CACHE_TTL_SECONDS");
    if (ttlEnv != null && !ttlEnv.isEmpty()) {
      try {
        ttl = Integer.parseInt(ttlEnv);
      } catch (NumberFormatException ignored) {
        // use default
      }
    }
    CACHE_TTL_SECONDS = ttl;

    // cz-java-0070: Populate local fallback map; this is only used when Memcached
    // is unreachable. In normal operation, ElastiCache Memcached serves all lookups.
    Map<String, Coordinates> map = new HashMap<>();
    map.put(HONGKONG.getUnLocode().getIdString(), new Coordinates(22, 114));
    map.put(MELBOURNE.getUnLocode().getIdString(), new Coordinates(-38, 145));
    map.put(STOCKHOLM.getUnLocode().getIdString(), new Coordinates(59, 18));
    map.put(HELSINKI.getUnLocode().getIdString(), new Coordinates(60, 25));
    map.put(CHICAGO.getUnLocode().getIdString(), new Coordinates(42, -88));
    map.put(TOKYO.getUnLocode().getIdString(), new Coordinates(36, 140));
    map.put(HAMBURG.getUnLocode().getIdString(), new Coordinates(54, 10));
    map.put(SHANGHAI.getUnLocode().getIdString(), new Coordinates(31, 121));
    map.put(ROTTERDAM.getUnLocode().getIdString(), new Coordinates(52, 5));
    map.put(GOTHENBURG.getUnLocode().getIdString(), new Coordinates(58, 12));
    map.put(HANGZOU.getUnLocode().getIdString(), new Coordinates(30, 120));
    map.put(NEWYORK.getUnLocode().getIdString(), new Coordinates(41, -74));
    map.put(DALLAS.getUnLocode().getIdString(), new Coordinates(33, -97));
    map.put(UNKNOWN.getUnLocode().getIdString(), new Coordinates(-90, 0)); // The South Pole.
    LOCAL_FALLBACK_MAP = Collections.unmodifiableMap(map);

    // cz-java-0070: Pre-warm ElastiCache Memcached with coordinate entries on startup.
    // The Memcached endpoint is resolved from the MEMCACHED_ENDPOINT environment variable
    // which is injected by the ECS Fargate task definition from AWS SSM Parameter Store.
    try {
      MemcachedClient memcachedClient = new MemcachedClient(AddrUtil.getAddresses(MEMCACHED_ENDPOINT));
      for (Map.Entry<String, Coordinates> entry : LOCAL_FALLBACK_MAP.entrySet()) {
        memcachedClient.set(CACHE_KEY_PREFIX + entry.getKey(), CACHE_TTL_SECONDS, entry.getValue());
      }
      memcachedClient.shutdown();
    } catch (Exception e) {
      // Log and continue; fallback map will be used if Memcached is unavailable.
      System.err.println("[CoordinatesFactory] Warning: Could not connect to Memcached at "
          + MEMCACHED_ENDPOINT + ". Using local fallback map. Error: " + e.getMessage());
    }
  }

  public static Coordinates find(Location location) {
    return find(location.getUnLocode());
  }

  public static Coordinates find(UnLocode unLocode) {
    return find(unLocode.getIdString());
  }

  /**
   * cz-java-0070: Looks up coordinates from Amazon ElastiCache for Memcached first.
   * Falls back to the local map if Memcached is unavailable, ensuring resilience.
   * The Memcached endpoint is configured via the MEMCACHED_ENDPOINT environment variable
   * injected from AWS SSM Parameter Store in the ECS Fargate task definition.
   */
  public static Coordinates find(String unLocode) {
    try {
      MemcachedClient memcachedClient = new MemcachedClient(AddrUtil.getAddresses(MEMCACHED_ENDPOINT));
      Object cached = memcachedClient.get(CACHE_KEY_PREFIX + unLocode);
      memcachedClient.shutdown();
      if (cached instanceof Coordinates) {
        return (Coordinates) cached;
      }
    } catch (Exception e) {
      // Memcached unavailable; fall through to local fallback map.
      System.err.println("[CoordinatesFactory] Warning: Memcached lookup failed for key '"
          + unLocode + "'. Using local fallback. Error: " + e.getMessage());
    }
    return LOCAL_FALLBACK_MAP.get(unLocode);
  }
}
