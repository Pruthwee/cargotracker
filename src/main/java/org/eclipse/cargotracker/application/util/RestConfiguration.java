package org.eclipse.cargotracker.application.util;

import java.util.HashMap;
import java.util.Map;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.glassfish.jersey.server.ServerProperties;

/** Jakarta REST configuration. */
@ApplicationPath("rest")
public class RestConfiguration extends Application {

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("jersey.config.beanValidation.sendErrorInResponse", true);
        return properties;
    }
}
