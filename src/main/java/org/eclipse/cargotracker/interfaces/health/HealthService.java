package org.eclipse.cargotracker.interfaces.health;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Health check end-point used by the container platform's liveness and readiness probes. It
 * answers as soon as the application is deployed, for as long as the application runs.
 */
@Path("/health")
public class HealthService {

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public String health() {
    return "{\"status\":\"UP\"}";
  }
}
