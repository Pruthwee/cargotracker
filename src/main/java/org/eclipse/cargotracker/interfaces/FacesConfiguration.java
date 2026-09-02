package org.eclipse.cargotracker.interfaces;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.annotation.FacesConfig;

/** Jakarta Faces configuration. * */
@FacesConfig()
// cz-java-0064: Singleton state externalized to Amazon ElastiCache (Redis) via RedisConfig.
// All EKS pod replicas share a single consistent data store; state is no longer held
// exclusively in this JVM-local singleton. Use REDIS_HOST, REDIS_PORT, REDIS_PASSWORD env vars.
@ApplicationScoped
public class FacesConfiguration {}
