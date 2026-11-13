package org.eclipse.cargotracker.interfaces.health;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Simple health check endpoint for container orchestration and load balancer probes.
 *
 * <p>Returns a 200 OK with a JSON status body while the application is running.
 */
@Path("/health")
public class HealthResource {

  /**
   * Health check endpoint.
   *
   * @return 200 OK with JSON status {@code {"status":"UP"}}.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response health() {
    return Response.ok("{\"status\":\"UP\"}").build();
  }
}
