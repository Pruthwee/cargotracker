package org.eclipse.cargotracker.interfaces.handling.file;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.batch.api.listener.JobListener;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Job listener that uses java.time API with UTC standardization instead of java.util.Date
 * to ensure consistent time handling across distributed cloud environments.
 */
@Dependent
@Named("FileProcessorJobListener")
public class FileProcessorJobListener implements JobListener {

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

  @Inject private Logger logger;

  @Override
  public void beforeJob() throws Exception {
    // Use Instant with UTC for cloud-native time handling across distributed instances
    String timestamp = FORMATTER.format(Instant.now().atZone(ZoneOffset.UTC));
    logger.log(Level.INFO, "Handling event file processor batch job starting at {0}", timestamp);
  }

  @Override
  public void afterJob() throws Exception {
    // Use Instant with UTC for cloud-native time handling across distributed instances
    String timestamp = FORMATTER.format(Instant.now().atZone(ZoneOffset.UTC));
    logger.log(Level.INFO, "Handling event file processor batch job completed at {0}", timestamp);
  }
}
