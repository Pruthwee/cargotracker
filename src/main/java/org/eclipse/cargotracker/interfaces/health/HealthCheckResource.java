package org.eclipse.cargotracker.interfaces.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Health check endpoint for container orchestration platforms (Kubernetes, ECS, etc.).
 * Provides liveness and readiness probes at /rest/health.
 *
 * This is a mandatory containerization requirement to support:
 * - Kubernetes liveness/readiness probes
 * - AWS ECS health checks
 * - Load balancer health monitoring
 */
@ApplicationScoped
@Path("/health")
public class HealthCheckResource {

  /**
   * Health check endpoint returning application status.
   * GET /rest/health
   *
   * @return HTTP 200 with JSON status when application is healthy
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response health() {
    return Response.ok("{\"status\":\"UP\",\"application\":\"cargo-tracker\"}").build();
  }
}
