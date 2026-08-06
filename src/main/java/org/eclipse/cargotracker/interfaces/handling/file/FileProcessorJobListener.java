package org.eclipse.cargotracker.interfaces.handling.file;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.batch.api.listener.JobListener;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Batch job listener that logs job start and completion times.
 *
 * <p>Cloud-readiness fix (cr-java-0111 – Clock/Time Dependencies): Replaced {@code new Date()}
 * (server-local timezone) with {@code Instant.now()} which always returns a UTC-based timestamp.
 * This ensures consistent, timezone-independent logging across all cloud instances and regions.
 */
@Dependent
@Named("FileProcessorJobListener")
public class FileProcessorJobListener implements JobListener {

  @Inject private Logger logger;

  @Override
  public void beforeJob() throws Exception {
    logger.log(Level.INFO, "Handling event file processor batch job starting at {0}", Instant.now());
  }

  @Override
  public void afterJob() throws Exception {
    logger.log(Level.INFO, "Handling event file processor batch job completed at {0}", Instant.now());
  }
}
