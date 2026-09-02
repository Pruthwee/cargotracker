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
 * Health check endpoint for container orchestration (Kubernetes/EKS liveness and readiness probes).
 *
 * <p>Exposes GET /rest/health returning a JSON status response.
 * This endpoint is used by EKS to determine pod health during horizontal scaling.
 */
@ApplicationScoped
@Path("/health")
public class HealthCheckResource {

    /**
     * Returns the application health status.
     *
     * <p>HTTP 200 with {"status":"UP"} indicates the application is healthy.
     *
     * @return HTTP 200 with JSON health status body
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "UP");
        status.put("application", "cargo-tracker");
        status.put("timestamp", Instant.now().toString());
        return Response.ok(status).build();
    }
}
