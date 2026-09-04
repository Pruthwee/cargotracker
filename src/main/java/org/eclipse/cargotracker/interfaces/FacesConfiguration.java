package org.eclipse.cargotracker.interfaces;

// cz-java-0064: @ApplicationScoped retained (already CDI-scoped, not EJB @Singleton).
// Configuration bean - no mutable singleton state. Compliant with EKS horizontal scaling.
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.annotation.FacesConfig;

/** Jakarta Faces configuration. * */
@FacesConfig()
@ApplicationScoped
public class FacesConfiguration {}
