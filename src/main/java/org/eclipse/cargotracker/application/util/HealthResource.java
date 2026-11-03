package org.eclipse.cargotracker.application.util;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;

/** Simple health check endpoint for container orchestration readiness and liveness probes. */
@Path("/health")
public class HealthResource {

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response getHealth() {
    return Response.ok(
            "{\"status\":\"UP\",\"timestamp\":\"" + LocalDateTime.now() + "\"}")
        .build();
  }
}
