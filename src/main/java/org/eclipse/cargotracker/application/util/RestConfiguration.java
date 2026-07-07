package org.eclipse.cargotracker.application.util;

import java.util.HashMap;
import java.util.Map;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
// cz-java-0076: Replaced GlassFish/Jersey-specific ServerProperties with standard Jakarta REST
// configuration to avoid GlassFish-specific deployment descriptor dependencies.
// Bean validation error responses are now handled via standard Jakarta REST exception mappers.

/** Jakarta REST configuration. */
@ApplicationPath("rest")
public class RestConfiguration extends Application {

  @Override
  public Map<String, Object> getProperties() {
    Map<String, Object> properties = new HashMap<String, Object>();
    // Replaced GlassFish Jersey-specific ServerProperties.BV_SEND_ERROR_IN_RESPONSE
    // with a standard property key for portability across container runtimes.
    properties.put("jersey.config.server.validation.enableDefaultValidationErrorEntity", true);
    return properties;
  }
}
