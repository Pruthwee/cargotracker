package org.eclipse.cargotracker.application;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.cargotracker.domain.model.cargo.Itinerary;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * Test state holder that uses Redis (ElastiCache) for sharing state between ordered test methods,
 * replacing static mutable variables. Falls back to in-memory storage when Redis is unavailable.
 */
@ApplicationScoped
@Named("TestStateHolder")
public class TestStateHolder {

  private static final String KEY_PREFIX = "test_state:";
  private static final int TTL_SECONDS = 3600;
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

  @Inject private Logger logger;

  private RedisClient redisClient;
  private StatefulRedisConnection<String, String> redisConnection;
  private RedisCommands<String, String> redisCommands;
  private boolean redisAvailable = false;
  private final Object initLock = new Object();

  private String trackingId;
  private List<Itinerary> candidates;
  private LocalDate deadline;
  private Itinerary assigned;

  private void ensureRedisInitialized() {
    if (redisCommands != null) {
      return;
    }
    synchronized (initLock) {
      if (redisCommands != null) {
        return;
      }
      String redisEndpoint = System.getenv("ELASTICACHE_ENDPOINT");
      if (redisEndpoint == null || redisEndpoint.isEmpty()) {
        logger.log(Level.WARNING, "ELASTICACHE_ENDPOINT not set, using in-memory test state");
        return;
      }
      try {
        redisClient = RedisClient.create(RedisURI.create(redisEndpoint, 6379));
        redisConnection = redisClient.connect();
        redisCommands = redisConnection.sync();
        redisCommands.ping();
        redisAvailable = true;
        logger.log(Level.INFO, "Connected to Redis at {0} for test state", redisEndpoint);
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to connect to Redis, using in-memory test state", e);
      }
    }
  }

  public void setTrackingId(TrackingId trackingId) {
    ensureRedisInitialized();
    if (redisAvailable) {
      try {
        redisCommands.setex(KEY_PREFIX + "trackingId", TTL_SECONDS, trackingId.getIdString());
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to store trackingId in Redis", e);
      }
    }
    this.trackingId = trackingId.getIdString();
  }

  public TrackingId getTrackingId() {
    ensureRedisInitialized();
    if (redisAvailable) {
      try {
        String value = redisCommands.get(KEY_PREFIX + "trackingId");
        if (value != null) {
          return new TrackingId(value);
        }
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to read trackingId from Redis", e);
      }
    }
    return trackingId != null ? new TrackingId(trackingId) : null;
  }

  public void setCandidates(List<Itinerary> candidates) {
    ensureRedisInitialized();
    if (redisAvailable) {
      try {
        redisCommands.setex(KEY_PREFIX + "candidates", TTL_SECONDS, serialize(candidates));
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to store candidates in Redis", e);
      }
    }
    this.candidates = candidates;
  }

  public List<Itinerary> getCandidates() {
    ensureRedisInitialized();
    if (redisAvailable) {
      try {
        String value = redisCommands.get(KEY_PREFIX + "candidates");
        if (value != null) {
          return deserialize(value);
        }
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to read candidates from Redis", e);
      }
    }
    return candidates;
  }

  public void setDeadline(LocalDate deadline) {
    ensureRedisInitialized();
    if (redisAvailable) {
      try {
        redisCommands.setex(KEY_PREFIX + "deadline", TTL_SECONDS, deadline.format(DATE_FORMATTER));
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to store deadline in Redis", e);
      }
    }
    this.deadline = deadline;
  }

  public LocalDate getDeadline() {
    ensureRedisInitialized();
    if (redisAvailable) {
      try {
        String value = redisCommands.get(KEY_PREFIX + "deadline");
        if (value != null) {
          return LocalDate.parse(value, DATE_FORMATTER);
        }
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to read deadline from Redis", e);
      }
    }
    return deadline;
  }

  public void setAssigned(Itinerary assigned) {
    ensureRedisInitialized();
    if (redisAvailable) {
      try {
        redisCommands.setex(KEY_PREFIX + "assigned", TTL_SECONDS, serialize(assigned));
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to store assigned in Redis", e);
      }
    }
    this.assigned = assigned;
  }

  public Itinerary getAssigned() {
    ensureRedisInitialized();
    if (redisAvailable) {
      try {
        String value = redisCommands.get(KEY_PREFIX + "assigned");
        if (value != null) {
          return deserializeItinerary(value);
        }
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to read assigned from Redis", e);
      }
    }
    return assigned;
  }

  private static String serialize(Object obj) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos)) {
      oos.writeObject(obj);
      return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
    } catch (IOException e) {
      throw new RuntimeException("Failed to serialize object", e);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Itinerary> deserialize(String data) {
    try (ByteArrayInputStream bais = new ByteArrayInputStream(java.util.Base64.getDecoder().decode(data));
        ObjectInputStream ois = new ObjectInputStream(bais)) {
      return (List<Itinerary>) ois.readObject();
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeException("Failed to deserialize object", e);
    }
  }

  private static Itinerary deserializeItinerary(String data) {
    try (ByteArrayInputStream bais = new ByteArrayInputStream(java.util.Base64.getDecoder().decode(data));
        ObjectInputStream ois = new ObjectInputStream(bais)) {
      return (Itinerary) ois.readObject();
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeException("Failed to deserialize itinerary", e);
    }
  }
}
