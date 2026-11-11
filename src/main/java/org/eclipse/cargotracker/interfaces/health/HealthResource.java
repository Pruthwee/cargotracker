package org.eclipse.cargotracker.interfaces.health;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Simple health check endpoint for container readiness and liveness probes.
 */
@Path("/health")
public class HealthResource {

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response health() {
    JsonObject json =
        Json.createObjectBuilder()
            .add("status", "UP")
            .add("application", "cargo-tracker")
            .build();
    return Response.ok(json).build();
  }
}
