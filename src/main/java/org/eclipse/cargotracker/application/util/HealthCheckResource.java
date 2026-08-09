package org.eclipse.cargotracker.application.util;

import java.util.Collections;
import java.util.Map;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Simple container health check endpoint. */
@Path("/health")
public class HealthCheckResource {

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, String> health() {
    return Collections.singletonMap("status", "UP");
  }
}
