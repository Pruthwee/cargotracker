package org.eclipse.cargotracker.interfaces.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Health check endpoint for containerization readiness.
 *
 * <p>Exposes a simple {@code GET /rest/health} endpoint that returns HTTP 200 with a JSON status
 * payload. This endpoint is used by Kubernetes liveness and readiness probes on EKS to determine
 * whether the pod is healthy and ready to serve traffic.
 *
 * <p>Example response:
 *
 * <pre>
 * {"status":"UP","application":"cargo-tracker"}
 * </pre>
 */
@ApplicationScoped
@Path("/health")
public class HealthCheckResource {

  /**
   * Returns the application health status.
   *
   * @return HTTP 200 with JSON body {@code {"status":"UP","application":"cargo-tracker"}}
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response health() {
    return Response.ok("{\"status\":\"UP\",\"application\":\"cargo-tracker\"}").build();
  }
}
