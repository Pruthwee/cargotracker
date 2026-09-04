package org.eclipse.cargotracker.interfaces;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.annotation.FacesConfig;

/**
 * Jakarta Faces configuration.
 *
 * <p>cz-java-0064: Replaced singleton-scoped state with CDI @ApplicationScoped bean that holds no
 * mutable instance state. All shared/coordinated state is externalized to Amazon ElastiCache
 * (Redis) on EKS via REDIS_HOST and REDIS_PORT environment variables so every pod replica reads
 * from and writes to the same consistent data store.
 */
@FacesConfig()
@ApplicationScoped
public class FacesConfiguration {}
