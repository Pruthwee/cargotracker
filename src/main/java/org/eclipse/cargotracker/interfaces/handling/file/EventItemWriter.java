package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.List;
import jakarta.batch.api.chunk.AbstractItemWriter;
import jakarta.batch.runtime.context.JobContext;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;
import org.eclipse.cargotracker.application.ApplicationEvents;
import org.eclipse.cargotracker.application.util.DateConverter;
import org.eclipse.cargotracker.infrastructure.storage.S3ObjectStore;
import org.eclipse.cargotracker.interfaces.handling.HandlingEventRegistrationAttempt;

@Dependent
@Named("EventItemWriter")
public class EventItemWriter extends AbstractItemWriter {

  private static final String ARCHIVE_DIRECTORY = "archive_directory";

  @Inject private JobContext jobContext;
  @Inject private ApplicationEvents applicationEvents;
  @Inject private S3ObjectStore objectStore;

  @Override
  public void open(Serializable checkpoint) throws Exception {
    // The archive lives under an S3 key prefix, which needs no folder to be created.
  }

  @Override
  @Transactional
  public void writeItems(List<Object> items) throws Exception {
    StringWriter buffer = new StringWriter();

    try (PrintWriter archive = new PrintWriter(buffer)) {

      items
          .stream()
          .map(item -> (HandlingEventRegistrationAttempt) item)
          .forEach(
              attempt -> {
                applicationEvents.receivedHandlingEventRegistrationAttempt(attempt);
                archive.println(
                    DateConverter.toString(attempt.getRegistrationTime())
                        + ","
                        + DateConverter.toString(attempt.getCompletionTime())
                        + ","
                        + attempt.getTrackingId()
                        + ","
                        + attempt.getVoyageNumber()
                        + ","
                        + attempt.getUnLocode()
                        + ","
                        + attempt.getType());
              });
    }

    objectStore.append(
        S3ObjectStore.prefixOf(jobContext.getProperties().getProperty(ARCHIVE_DIRECTORY))
            + "archive_"
            + jobContext.getJobName()
            + "_"
            + jobContext.getInstanceId()
            + ".csv",
        buffer.toString());
  }
}
