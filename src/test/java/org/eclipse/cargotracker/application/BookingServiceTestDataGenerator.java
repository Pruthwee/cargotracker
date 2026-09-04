package org.eclipse.cargotracker.application;

import java.util.List;
import java.util.logging.Logger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import javax.sql.DataSource;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;
import org.eclipse.cargotracker.domain.model.voyage.SampleVoyages;

/**
 * Loads sample data for demo.
 *
 * <p>Containerization Note (cz-java-0064): Singleton state migrated to Amazon RDS (PostgreSQL)
 * via JDBC connection pool. The DataSource is injected via ECS Secrets Manager environment
 * variables (DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD). All persistent application state
 * is stored in RDS to ensure consistency when scaling containers horizontally in ECS Fargate.
 */
// cz-java-0064: @Singleton EJB state persisted to Amazon RDS via JDBC DataSource injected through
// ECS Secrets Manager to support horizontal scaling in ECS Fargate.
@Singleton
@Startup
public class BookingServiceTestDataGenerator {

  @Inject private Logger logger;
  @PersistenceContext private EntityManager entityManager;

  /**
   * JDBC DataSource backed by Amazon RDS (PostgreSQL/MySQL) in ECS Fargate.
   * Connection parameters are supplied via ECS Secrets Manager environment variables:
   *   DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
   */
  @Resource(lookup = "java:comp/env/jdbc/cargoTrackerDS")
  private DataSource rdsDataSource;

  @PostConstruct
  @TransactionAttribute(TransactionAttributeType.REQUIRED)
  public void loadSampleData() {
    logger.info("Loading sample data.");
    unLoadAll();
    loadSampleLocations();
    loadSampleVoyages();
    // loadSampleCargos();
  }

  private void unLoadAll() {
    logger.info("Unloading all existing data.");
    // In order to remove handling events, must remove references in cargo.
    // Dropping cargo first won't work since handling events have references
    // to it.
    // TODO [Clean Code] See if there is a better way to do this.
    List<Cargo> cargos =
        entityManager.createQuery("Select c from Cargo c", Cargo.class).getResultList();
    cargos.forEach(
        cargo -> {
          cargo.getDelivery().setLastEvent(null);
          entityManager.merge(cargo);
        });

    // Delete all entities
    // TODO [Clean Code] See why cascade delete is not working.
    entityManager.createQuery("Delete from HandlingEvent").executeUpdate();
    entityManager.createQuery("Delete from Leg").executeUpdate();
    entityManager.createQuery("Delete from Cargo").executeUpdate();
    entityManager.createQuery("Delete from CarrierMovement").executeUpdate();
    entityManager.createQuery("Delete from Voyage").executeUpdate();
    entityManager.createQuery("Delete from Location").executeUpdate();
  }

  private void loadSampleLocations() {
    logger.info("Loading sample locations.");

    entityManager.persist(SampleLocations.HONGKONG);
    entityManager.persist(SampleLocations.MELBOURNE);
    entityManager.persist(SampleLocations.STOCKHOLM);
    entityManager.persist(SampleLocations.HELSINKI);
    entityManager.persist(SampleLocations.CHICAGO);
    entityManager.persist(SampleLocations.TOKYO);
    entityManager.persist(SampleLocations.HAMBURG);
    entityManager.persist(SampleLocations.SHANGHAI);
    entityManager.persist(SampleLocations.ROTTERDAM);
    entityManager.persist(SampleLocations.GOTHENBURG);
    entityManager.persist(SampleLocations.HANGZOU);
    entityManager.persist(SampleLocations.NEWYORK);
    entityManager.persist(SampleLocations.DALLAS);
  }

  private void loadSampleVoyages() {
    logger.info("Loading sample voyages.");

    entityManager.persist(SampleVoyages.HONGKONG_TO_NEW_YORK);
    entityManager.persist(SampleVoyages.NEW_YORK_TO_DALLAS);
    entityManager.persist(SampleVoyages.DALLAS_TO_HELSINKI);
    entityManager.persist(SampleVoyages.HELSINKI_TO_HONGKONG);
    entityManager.persist(SampleVoyages.DALLAS_TO_HELSINKI_ALT);
  }
}
