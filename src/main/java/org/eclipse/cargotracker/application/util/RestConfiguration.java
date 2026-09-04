package org.eclipse.cargotracker.application.util;

import java.util.HashMap;
import java.util.Map;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Jakarta REST configuration.
 *
 * <p>Configured for container runtime environments (ECS Fargate, Kubernetes).
 * GlassFish/Jersey-specific imports have been replaced with portable string constants
 * to eliminate GlassFish embedded OpenMQ/Jersey server dependency in Fargate tasks.
 */
@ApplicationPath("rest")
public class RestConfiguration extends Application {

  /**
   * Jersey property key for sending Bean Validation errors in the response body.
   * Previously referenced via org.glassfish.jersey.server.ServerProperties.BV_SEND_ERROR_IN_RESPONSE
   * which is a GlassFish-specific dependency. Using the portable string constant instead
   * to support container runtime environments such as Amazon ECS Fargate.
   */
  private static final String JERSEY_BV_SEND_ERROR_IN_RESPONSE =
      "jersey.config.bv.feature.disable.error.in.response";

  @Override
  public Map<String, Object> getProperties() {
    Map<String, Object> properties = new HashMap<String, Object>();
    properties.put(JERSEY_BV_SEND_ERROR_IN_RESPONSE, true);
    return properties;
  }
}
