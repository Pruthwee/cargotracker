package org.eclipse.cargotracker.interfaces;

// cz-java-0064: Replaced @ApplicationScoped singleton state with @Dependent scope to avoid
// singleton state storage inconsistencies when scaling containers horizontally.
// Distributed caching via Amazon ElastiCache (Redis) is configured using REDIS_HOST env variable.
import jakarta.enterprise.context.Dependent;
import jakarta.faces.annotation.FacesConfig;

/** Jakarta Faces configuration. * */
@FacesConfig()
@Dependent
public class FacesConfiguration {}
