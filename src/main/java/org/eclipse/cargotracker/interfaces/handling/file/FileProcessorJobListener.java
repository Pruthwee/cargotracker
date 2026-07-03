package org.eclipse.cargotracker.interfaces.handling.file;

import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Dependent
@Named("FileProcessorJobListener")
public class FileProcessorJobListener {

    @Inject
    private Logger logger;

    public void beforeJob() {
        logger.log(Level.INFO, "Handling event file processor batch job starting at {0}", LocalDateTime.now());
    }

    public void afterJob() {
        logger.log(Level.INFO, "Handling event file processor batch job completed at {0}", LocalDateTime.now());
    }
}
