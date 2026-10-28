package org.eclipse.cargotracker.interfaces.handling.file;

import java.util.List;
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

  private static final String ARCHIVE_DIRECTORY = "archive_directory";

  @Inject private JobContext jobContext;
  @Inject private ApplicationEvents applicationEvents;
  @Inject private S3Storage storage;

  @Override
  @Transactional
  public void writeItems(List<Object> items) throws Exception {
    String archiveKey =
        S3Storage.toPrefix(jobContext.getProperties().getProperty(ARCHIVE_DIRECTORY))
            + "archive_"
            + jobContext.getJobName()
            + "_"
            + jobContext.getInstanceId()
            + ".csv";
    StringBuilder archive = new StringBuilder();

    try {
      items
          .stream()
          .map(item -> (HandlingEventRegistrationAttempt) item)
          .forEach(
              attempt -> {
                applicationEvents.receivedHandlingEventRegistrationAttempt(attempt);
                archive
                    .append(
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
                            + attempt.getType())
                    .append(System.lineSeparator());
              });
    } finally {
      if (archive.length() > 0) {
        storage.append(archiveKey, archive.toString());
      }
    }
  }
}
