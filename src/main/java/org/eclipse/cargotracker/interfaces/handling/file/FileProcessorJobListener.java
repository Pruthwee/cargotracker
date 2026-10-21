package org.eclipse.cargotracker.interfaces.handling.file;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.batch.api.listener.JobListener;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Dependent
@Named("FileProcessorJobListener")
public class FileProcessorJobListener implements JobListener {

  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("M/d/yyyy h:m a");

  @Inject private Logger logger;

  @Override
  public void beforeJob() throws Exception {
    logger.log(
        Level.INFO,
        "Handling event file processor batch job starting at {0}",
        LocalDateTime.now().format(DATE_TIME_FORMATTER));
  }

  @Override
  public void afterJob() throws Exception {
    logger.log(
        Level.INFO,
        "Handling event file processor batch job completed at {0}",
        LocalDateTime.now().format(DATE_TIME_FORMATTER));
  }
}
