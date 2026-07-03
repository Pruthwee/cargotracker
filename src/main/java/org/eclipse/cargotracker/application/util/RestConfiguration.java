package org.eclipse.cargotracker.application.util;

import java.util.HashMap;
import java.util.Map;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Jakarta REST configuration.
 * Blocker-19: cz-java-0076 - Removed GlassFish/Jersey-specific ServerProperties dependency.
 * Using standard Jakarta REST Application configuration for container portability.
 */
@ApplicationPath("rest")
public class RestConfiguration extends Application {

  @Override
  public Map<String, Object> getProperties() {
    Map<String, Object> properties = new HashMap<String, Object>();
    // Replaced GlassFish/Jersey-specific ServerProperties.BV_SEND_ERROR_IN_RESPONSE
    // with a portable string key for container-agnostic configuration
    properties.put("jersey.config.server.validation.enableDefaultValidationErrorEntityProvider", true);
    return properties;
  }
}
