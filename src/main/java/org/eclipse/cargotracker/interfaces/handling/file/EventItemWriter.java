package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.ByteArrayOutputStream;
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
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Dependent
@Named("EventItemWriter")
public class EventItemWriter extends AbstractItemWriter {

  private static final String ARCHIVE_DIRECTORY = "archive_directory";

  @Inject private JobContext jobContext;
  @Inject private ApplicationEvents applicationEvents;
  @Inject private Logger logger;

  private S3Client s3Client;
  private String bucketName;

  @Override
  public void open(Serializable checkpoint) throws Exception {
    try {
      s3Client = S3Client.create();
      bucketName = System.getenv("APP_S3_BUCKET");
      if (bucketName == null || bucketName.isEmpty()) {
        logger.log(Level.WARNING, "APP_S3_BUCKET is not set, archiving will fail");
      }
    } catch (SdkException e) {
      logger.log(Level.WARNING, "Failed to create S3 client", e);
    }
  }

  @Override
  @Transactional
  public void writeItems(List<Object> items) throws Exception {
    String archivePrefix = jobContext.getProperties().getProperty(ARCHIVE_DIRECTORY);

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    items
        .stream()
        .map(item -> (HandlingEventRegistrationAttempt) item)
        .forEach(
            attempt -> {
              applicationEvents.receivedHandlingEventRegistrationAttempt(attempt);
              buffer.writeBytes(
                  (DateConverter.toString(attempt.getRegistrationTime())
                      + ","
                      + DateConverter.toString(attempt.getCompletionTime())
                      + ","
                      + attempt.getTrackingId()
                      + ","
                      + attempt.getVoyageNumber()
                      + ","
                      + attempt.getUnLocode()
                      + ","
                      + attempt.getType()
                      + "\n")
                      .getBytes());
            });

    String key =
        archivePrefix
            + "/archive_"
            + jobContext.getJobName()
            + "_"
            + jobContext.getInstanceId()
            + ".csv";

    try {
      PutObjectRequest putRequest =
          PutObjectRequest.builder().bucket(bucketName).key(key).build();
      s3Client.putObject(putRequest, RequestBody.fromBytes(buffer.toByteArray()));
    } catch (SdkException e) {
      logger.log(Level.WARNING, "Failed to archive items to S3", e);
    }
  }
}
