package org.eclipse.cargotracker.interfaces;

// Blocker-9: cz-java-0064 - CDI @ApplicationScoped used (not EJB @Singleton) for distributed container compatibility
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.annotation.FacesConfig;

/** Jakarta Faces configuration. * */
@FacesConfig()
@ApplicationScoped
public class FacesConfiguration {}
