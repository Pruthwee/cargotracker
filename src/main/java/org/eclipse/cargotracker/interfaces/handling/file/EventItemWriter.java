package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
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
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Writes processed handling event records to Amazon S3 instead of local file system.
 * Replaces java.io.File-based write operations with S3 client calls to achieve
 * cloud-native, durable, and scalable storage without host-level file system dependencies.
 */
@Dependent
@Named("EventItemWriter")
public class EventItemWriter extends AbstractItemWriter {

  private static final String S3_BUCKET_PROPERTY = "s3_bucket";
  private static final String S3_ARCHIVE_PREFIX_PROPERTY = "archive_prefix";

  @Inject private Logger logger;
  @Inject private JobContext jobContext;
  @Inject private ApplicationEvents applicationEvents;

  private S3Client s3Client;
  private String bucketName;
  private String archivePrefix;

  @Override
  public void open(Serializable checkpoint) throws Exception {
    s3Client = S3Client.builder().build();
    bucketName = System.getenv().getOrDefault("S3_BUCKET_NAME",
        jobContext.getProperties().getProperty(S3_BUCKET_PROPERTY, "cargo-tracker-uploads"));
    archivePrefix = System.getenv().getOrDefault("S3_ARCHIVE_PREFIX",
        jobContext.getProperties().getProperty(S3_ARCHIVE_PREFIX_PROPERTY, "archive/"));
    logger.log(Level.INFO, "EventItemWriter initialized with S3 bucket: {0}, archive prefix: {1}",
        new Object[]{bucketName, archivePrefix});
  }

  @Override
  @Transactional
  public void writeItems(List<Object> items) throws Exception {
    String archiveKey = archivePrefix + "archive_"
        + jobContext.getJobName()
        + "_"
        + jobContext.getInstanceId()
        + ".csv";

    // Read existing content from S3 if it exists (append semantics)
    StringBuilder existingContent = new StringBuilder();
    try {
      GetObjectRequest getRequest = GetObjectRequest.builder()
          .bucket(bucketName)
          .key(archiveKey)
          .build();
      byte[] existingBytes = s3Client.getObjectAsBytes(getRequest).asByteArray();
      existingContent.append(new String(existingBytes, StandardCharsets.UTF_8));
    } catch (NoSuchKeyException e) {
      // Object doesn't exist yet, start fresh
      logger.log(Level.INFO, "Creating new archive object in S3: {0}", archiveKey);
    }

    // Append new items
    StringBuilder newContent = new StringBuilder(existingContent);
    items.stream()
        .map(item -> (HandlingEventRegistrationAttempt) item)
        .forEach(attempt -> {
          applicationEvents.receivedHandlingEventRegistrationAttempt(attempt);
          newContent.append(
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
                  + attempt.getType()
                  + "\n");
        });

    // Write updated content back to S3
    byte[] contentBytes = newContent.toString().getBytes(StandardCharsets.UTF_8);
    PutObjectRequest putRequest = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(archiveKey)
        .contentType("text/csv")
        .build();
    s3Client.putObject(putRequest, RequestBody.fromInputStream(
        new ByteArrayInputStream(contentBytes), contentBytes.length));

    logger.log(Level.INFO, "Archived {0} items to S3: {1}", new Object[]{items.size(), archiveKey});
  }
}
