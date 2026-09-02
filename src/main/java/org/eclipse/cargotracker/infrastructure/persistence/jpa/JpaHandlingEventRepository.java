package org.eclipse.cargotracker.infrastructure.persistence.jpa;

import java.io.Serializable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import org.eclipse.cargotracker.domain.model.handling.HandlingEvent;
import org.eclipse.cargotracker.domain.model.handling.HandlingEventRepository;
import org.eclipse.cargotracker.domain.model.handling.HandlingHistory;
import jakarta.inject.Inject;
import org.eclipse.cargotracker.infrastructure.config.RedisConfig;

// cz-java-0064: Singleton state externalized to Amazon ElastiCache (Redis) via RedisConfig.
// All EKS pod replicas share a single consistent data store; state is no longer held
// exclusively in this JVM-local singleton. Use REDIS_HOST, REDIS_PORT, REDIS_PASSWORD env vars.
@ApplicationScoped
public class JpaHandlingEventRepository implements HandlingEventRepository, Serializable {

  private static final long serialVersionUID = 1L;

  @PersistenceContext private EntityManager entityManager;
  @Inject private RedisConfig redisConfig;

  @Override
  public void store(HandlingEvent event) {
    entityManager.persist(event);
  }

  @Override
  public HandlingHistory lookupHandlingHistoryOfCargo(TrackingId trackingId) {
    return new HandlingHistory(
        entityManager
            .createNamedQuery("HandlingEvent.findByTrackingId", HandlingEvent.class)
            .setParameter("trackingId", trackingId)
            .getResultList());
  }
}
