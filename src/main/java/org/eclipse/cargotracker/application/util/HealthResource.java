package org.eclipse.cargotracker.application.util;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;

/** Health check endpoint for container orchestration readiness and liveness probes. */
@Path("/health")
public class HealthResource {

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response health() {
    String json =
        "{\"status\":\"UP\",\"timestamp\":\"" + LocalDateTime.now() + "\"}";
    return Response.ok(json).build();
  }
}
