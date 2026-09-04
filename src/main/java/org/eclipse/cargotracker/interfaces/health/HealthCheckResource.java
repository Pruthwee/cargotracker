package org.eclipse.cargotracker.interfaces.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check endpoint for containerization readiness.
 *
 * <p>Provides a simple HTTP GET /rest/health endpoint that returns the application status.
 * This endpoint is used by container orchestration platforms (ECS Fargate, Kubernetes, etc.)
 * to determine if the application instance is healthy and ready to serve traffic.
 *
 * <p>Returns HTTP 200 with JSON body: {"status":"UP","timestamp":"..."} when healthy.
 */
@ApplicationScoped
@Path("/health")
public class HealthCheckResource {

  /**
   * Liveness and readiness health check endpoint.
   *
   * @return HTTP 200 with JSON status body when the application is healthy.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response health() {
    Map<String, Object> healthStatus = new LinkedHashMap<>();
    healthStatus.put("status", "UP");
    healthStatus.put("application", "cargo-tracker");
    healthStatus.put("timestamp", Instant.now().toString());
    return Response.ok(healthStatus).build();
  }
}
