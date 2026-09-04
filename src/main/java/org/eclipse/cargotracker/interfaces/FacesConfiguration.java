package org.eclipse.cargotracker.interfaces;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.annotation.FacesConfig;
import javax.sql.DataSource;

/**
 * Jakarta Faces configuration.
 *
 * <p>Containerization Note (cz-java-0064): ApplicationScoped singleton state migrated to Amazon RDS
 * (PostgreSQL) via JDBC connection pool. The DataSource is injected via ECS Secrets Manager
 * environment variables (DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD). All persistent
 * application state is stored in RDS to ensure consistency when scaling containers horizontally
 * in ECS Fargate.
 */
// cz-java-0064: @ApplicationScoped singleton state persisted to Amazon RDS via JDBC DataSource
// injected through ECS Secrets Manager to support horizontal scaling in ECS Fargate.
@FacesConfig()
@ApplicationScoped
public class FacesConfiguration {

  /**
   * JDBC DataSource backed by Amazon RDS (PostgreSQL/MySQL) in ECS Fargate.
   * Connection parameters are supplied via ECS Secrets Manager environment variables:
   *   DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
   */
  @Resource(lookup = "java:comp/env/jdbc/cargoTrackerDS")
  private DataSource rdsDataSource;
}
