package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.batch.api.chunk.listener.SkipReadListener;
import jakarta.batch.runtime.context.JobContext;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.cargotracker.infrastructure.storage.S3ObjectStore;

@Dependent
@Named("LineParseExceptionListener")
public class LineParseExceptionListener implements SkipReadListener {

  private static final String FAILED_DIRECTORY = "failed_directory";

  @Inject private Logger logger;

  @Inject private JobContext jobContext;

  @Inject private S3ObjectStore objectStore;

  @Override
  public void onSkipReadItem(Exception e) throws Exception {
    String failedPrefix =
        S3ObjectStore.prefixOf(jobContext.getProperties().getProperty(FAILED_DIRECTORY));

    EventLineParseException parseException = (EventLineParseException) e;

    logger.log(Level.WARNING, "Problem parsing event file line", parseException);

    StringWriter buffer = new StringWriter();

    try (PrintWriter failed = new PrintWriter(buffer)) {
      failed.println(parseException.getLine());
    }

    objectStore.append(
        failedPrefix
            + "failed_"
            + jobContext.getJobName()
            + "_"
            + jobContext.getInstanceId()
            + ".csv",
        buffer.toString());
  }
}
