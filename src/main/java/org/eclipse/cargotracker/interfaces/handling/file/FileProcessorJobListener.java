package org.eclipse.cargotracker.interfaces.handling.file;

import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.batch.api.listener.AbstractJobListener;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Batch job listener that logs the start and completion times of the file processor job.
 * Uses LocalDateTime (Java 8+) instead of deprecated java.util.Date.
 */
@Dependent
@Named("FileProcessorJobListener")
public class FileProcessorJobListener extends AbstractJobListener {

  @Inject private Logger logger;

  @Override
  public void beforeJob() throws Exception {
    logger.log(
        Level.INFO,
        "Handling event file processor batch job starting at {0}",
        LocalDateTime.now());
  }

  @Override
  public void afterJob() throws Exception {
    logger.log(
        Level.INFO,
        "Handling event file processor batch job completed at {0}",
        LocalDateTime.now());
  }
}
