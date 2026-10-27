package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.IOException;
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

  @Inject private Logger logger;

  @Inject private JobContext jobContext;
  @Inject private EventFileS3Helper s3Helper;

  @Override
  public void onSkipReadItem(Exception e) throws Exception {
    EventLineParseException parseException = (EventLineParseException) e;

    logger.log(Level.WARNING, "Problem parsing event file line", parseException);

    String fileName =
        "failed_"
            + jobContext.getJobName()
            + "_"
            + jobContext.getInstanceId()
            + ".csv";
    s3Helper.appendFailedLine(fileName, parseException.getLine());
  }
}
