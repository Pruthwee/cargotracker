package org.eclipse.cargotracker.interfaces.handling.file;

import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.batch.api.chunk.listener.SkipReadListener;
import jakarta.batch.runtime.context.JobContext;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Dependent
@Named("LineParseExceptionListener")
public class LineParseExceptionListener implements SkipReadListener {

  private static final String FAILED_DIRECTORY = "failed_directory";

  @Inject private Logger logger;

  @Inject private JobContext jobContext;

  @Inject private S3Storage storage;

  @Override
  public void onSkipReadItem(Exception e) throws Exception {
    String failedKey =
        S3Storage.toPrefix(jobContext.getProperties().getProperty(FAILED_DIRECTORY))
            + "failed_"
            + jobContext.getJobName()
            + "_"
            + jobContext.getInstanceId()
            + ".csv";

    EventLineParseException parseException = (EventLineParseException) e;

    logger.log(Level.WARNING, "Problem parsing event file line", parseException);

    storage.append(failedKey, parseException.getLine() + System.lineSeparator());
  }
}
