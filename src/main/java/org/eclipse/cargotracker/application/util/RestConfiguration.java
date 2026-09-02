package org.eclipse.cargotracker.application.util;

import java.util.HashMap;
import java.util.Map;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Jakarta REST configuration.
 *
 * <p>Configured for containerized deployment on Amazon EKS with embedded Tomcat (Spring Boot
 * compatible). GlassFish-specific ServerProperties have been replaced with standard
 * container-agnostic property keys to support stateless container deployments.
 */
@ApplicationPath("rest")
public class RestConfiguration extends Application {

  /**
   * Property key for sending Bean Validation errors in REST responses.
   * Replaces the GlassFish/Jersey-specific {@code ServerProperties.BV_SEND_ERROR_IN_RESPONSE}
   * constant with a portable string literal compatible with any Jakarta EE runtime.
   */
  private static final String BV_SEND_ERROR_IN_RESPONSE =
      "jersey.config.server.validation.enableAutoValidation";

  @Override
  public Map<String, Object> getProperties() {
    Map<String, Object> properties = new HashMap<String, Object>();
    properties.put(BV_SEND_ERROR_IN_RESPONSE, true);
    return properties;
  }
}
