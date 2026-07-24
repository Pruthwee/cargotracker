package org.eclipse.cargotracker.interfaces.handling.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Health check endpoint for containerization requirements.
 */
@Path("/health")
public class HealthCheckResource {

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response healthCheck() {
    return Response.ok("{\"status\":\"UP\"}").build();
  }
}
