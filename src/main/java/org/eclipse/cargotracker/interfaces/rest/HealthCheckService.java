package org.eclipse.cargotracker.interfaces.rest;

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
 * <p>Provides a simple liveness probe at GET /rest/health returning HTTP 200 with a JSON status
 * body. This endpoint is used by Kubernetes/EKS liveness and readiness probes to determine whether
 * the pod is healthy and ready to serve traffic.
 *
 * <p>Example response:
 *
 * <pre>
 * {
 *   "status": "UP",
 *   "timestamp": "2024-01-01T00:00:00Z"
 * }
 * </pre>
 */
@ApplicationScoped
@Path("/health")
public class HealthCheckService {

  /**
   * Returns the health status of the application.
   *
   * @return HTTP 200 with JSON body {"status":"UP","timestamp":"..."} when healthy
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response health() {
    Map<String, String> status = new LinkedHashMap<>();
    status.put("status", "UP");
    status.put("timestamp", Instant.now().toString());
    return Response.ok(status).build();
  }
}
