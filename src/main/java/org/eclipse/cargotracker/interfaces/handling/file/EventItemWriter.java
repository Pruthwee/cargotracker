package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.batch.api.chunk.AbstractItemWriter;
import jakarta.batch.runtime.context.JobContext;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;
import org.eclipse.cargotracker.application.ApplicationEvents;
import org.eclipse.cargotracker.application.util.DateConverter;
import org.eclipse.cargotracker.interfaces.handling.HandlingEventRegistrationAttempt;

@Dependent
@Named("EventItemWriter")
public class EventItemWriter extends AbstractItemWriter {

  @Inject private Logger logger;

  @Inject private JobContext jobContext;
  @Inject private ApplicationEvents applicationEvents;
  @Inject private EventFileS3Helper s3Helper;

  @Override
  public void open(Serializable checkpoint) throws Exception {
    // S3 buckets are created externally; no local directory creation needed.
  }

  @Override
  @Transactional
  public void writeItems(List<Object> items) throws Exception {
    StringBuilder archiveContent = new StringBuilder();

    for (Object item : items) {
      HandlingEventRegistrationAttempt attempt = (HandlingEventRegistrationAttempt) item;
      applicationEvents.receivedHandlingEventRegistrationAttempt(attempt);
      archiveContent
          .append(DateConverter.toString(attempt.getRegistrationTime()))
          .append(",")
          .append(DateConverter.toString(attempt.getCompletionTime()))
          .append(",")
          .append(attempt.getTrackingId())
          .append(",")
          .append(attempt.getVoyageNumber())
          .append(",")
          .append(attempt.getUnLocode())
          .append(",")
          .append(attempt.getType())
          .append(System.lineSeparator());
    }

    String fileName =
        "archive_"
            + jobContext.getJobName()
            + "_"
            + jobContext.getInstanceId()
            + ".csv";
    s3Helper.writeArchiveFile(fileName, archiveContent.toString());
    logger.log(Level.INFO, "Wrote {0} items to S3 archive: {1}", new Object[] {items.size(), fileName});
  }
}
