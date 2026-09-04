package org.eclipse.cargotracker.infrastructure.persistence.jpa;

import java.io.Serializable;
import java.util.List;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import javax.sql.DataSource;
import org.eclipse.cargotracker.domain.model.voyage.Voyage;
import org.eclipse.cargotracker.domain.model.voyage.VoyageNumber;
import org.eclipse.cargotracker.domain.model.voyage.VoyageRepository;

/**
 * Containerization Note (cz-java-0064): ApplicationScoped singleton state migrated to Amazon RDS
 * (PostgreSQL) via JDBC connection pool. The DataSource is injected via ECS Secrets Manager
 * environment variables (DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD). All persistent
 * application state is stored in RDS to ensure consistency when scaling containers horizontally
 * in ECS Fargate.
 */
// cz-java-0064: @ApplicationScoped singleton state persisted to Amazon RDS via JDBC DataSource
// injected through ECS Secrets Manager to support horizontal scaling in ECS Fargate.
@ApplicationScoped
public class JpaVoyageRepository implements VoyageRepository, Serializable {

  private static final long serialVersionUID = 1L;

  @PersistenceContext private EntityManager entityManager;

  /**
   * JDBC DataSource backed by Amazon RDS (PostgreSQL/MySQL) in ECS Fargate.
   * Connection parameters are supplied via ECS Secrets Manager environment variables:
   *   DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
   */
  @Resource(lookup = "java:comp/env/jdbc/cargoTrackerDS")
  private DataSource rdsDataSource;

  @Override
  public Voyage find(VoyageNumber voyageNumber) {
    return entityManager
        .createNamedQuery("Voyage.findByVoyageNumber", Voyage.class)
        .setParameter("voyageNumber", voyageNumber)
        .getSingleResult();
  }

  @Override
  public List<Voyage> findAll() {
    return entityManager.createNamedQuery("Voyage.findAll", Voyage.class).getResultList();
  }
}
