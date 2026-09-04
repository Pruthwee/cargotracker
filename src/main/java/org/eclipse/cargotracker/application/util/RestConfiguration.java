package org.eclipse.cargotracker.application.util;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Jakarta REST configuration.
 *
 * <p>Configured as a stateless, container-agnostic JAX-RS application suitable for deployment
 * on Amazon EKS with an embedded Tomcat (Spring Boot) runtime. GlassFish/Jersey-specific
 * server properties have been removed to ensure compatibility across container runtimes.
 */
@ApplicationPath("rest")
public class RestConfiguration extends Application {
  // No GlassFish/Jersey-specific configuration needed.
  // Bean Validation error responses are handled by the standard Jakarta EE
  // exception mapper, which is compatible with any compliant container runtime
  // (e.g., embedded Tomcat via Spring Boot deployed on Amazon EKS).
}
