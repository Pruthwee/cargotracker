package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.StringJoiner;
import jakarta.batch.api.chunk.AbstractItemWriter;
import jakarta.batch.runtime.context.JobContext;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;
import org.eclipse.cargotracker.application.ApplicationEvents;
import org.eclipse.cargotracker.application.util.DateConverter;
import org.eclipse.cargotracker.interfaces.handling.HandlingEventRegistrationAttempt;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Writes processed handling event records to an Amazon S3 archive bucket.
 *
 * <p>The job property {@code s3_bucket_name} specifies the S3 bucket, and
 * {@code archive_prefix} specifies the S3 key prefix (folder) for archive objects.
 * Local file-system dependencies have been replaced with Amazon S3 object storage
 * (AWS SDK for Java v2) to ensure cloud-readiness.
 */
@Dependent
@Named("EventItemWriter")
public class EventItemWriter extends AbstractItemWriter {

  /** Job property: name of the S3 bucket used for archiving. */
  private static final String S3_BUCKET_NAME = "s3_bucket_name";

  /** Job property: S3 key prefix (folder) for archived CSV objects. */
  private static final String ARCHIVE_PREFIX = "archive_prefix";

  @Inject private JobContext jobContext;
  @Inject private ApplicationEvents applicationEvents;

  private S3Client s3Client;
  private String bucketName;
  private String archivePrefix;

  @Override
  public void open(Serializable checkpoint) throws Exception {
    bucketName = jobContext.getProperties().getProperty(S3_BUCKET_NAME);
    archivePrefix = jobContext.getProperties().getProperty(ARCHIVE_PREFIX);

    // Build a region-aware S3 client; region is resolved from the environment
    // (AWS_REGION env var, ~/.aws/config, or EC2/ECS instance metadata).
    s3Client = S3Client.builder().build();
  }

  @Override
  @Transactional
  public void writeItems(List<Object> items) throws Exception {
    StringJoiner csvContent = new StringJoiner(System.lineSeparator());

    items.stream()
        .map(item -> (HandlingEventRegistrationAttempt) item)
        .forEach(attempt -> {
          applicationEvents.receivedHandlingEventRegistrationAttempt(attempt);
          csvContent.add(
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

    String prefix = (archivePrefix != null && !archivePrefix.isEmpty())
        ? (archivePrefix.endsWith("/") ? archivePrefix : archivePrefix + "/")
        : "";

    String s3Key = prefix
        + "archive_"
        + jobContext.getJobName()
        + "_"
        + jobContext.getInstanceId()
        + ".csv";

    byte[] contentBytes = csvContent.toString().getBytes(StandardCharsets.UTF_8);

    PutObjectRequest putRequest = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(s3Key)
        .contentType("text/csv")
        .contentLength((long) contentBytes.length)
        .build();

    s3Client.putObject(putRequest, RequestBody.fromBytes(contentBytes));
  }

  @Override
  public void close() throws Exception {
    if (s3Client != null) {
      s3Client.close();
    }
  }
}
