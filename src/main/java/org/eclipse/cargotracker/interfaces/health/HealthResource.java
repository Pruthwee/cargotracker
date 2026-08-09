package org.eclipse.cargotracker.interfaces.health;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Container health check endpoint. */
@Path("/health")
public class HealthResource {

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response health() {
    return Response.ok("{\"status\":\"UP\"}").build();
  }
}
