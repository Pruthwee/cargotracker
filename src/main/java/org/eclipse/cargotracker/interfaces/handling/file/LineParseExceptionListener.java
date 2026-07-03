package org.eclipse.cargotracker.interfaces.handling.file;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.batch.api.chunk.listener.SkipReadListener;
import jakarta.batch.runtime.context.JobContext;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Writes failed event parse records to Amazon S3 instead of local file system.
 * Replaces java.io.File-based write operations with S3 client calls to achieve
 * cloud-native, durable, and scalable storage without host-level file system dependencies.
 */
@Dependent
@Named("LineParseExceptionListener")
public class LineParseExceptionListener implements SkipReadListener {

  private static final String S3_BUCKET_PROPERTY = "s3_bucket";
  private static final String S3_FAILED_PREFIX_PROPERTY = "failed_prefix";

  @Inject private Logger logger;

  @Inject private JobContext jobContext;

  @Override
  public void onSkipReadItem(Exception e) throws Exception {
    String bucketName = System.getenv().getOrDefault("S3_BUCKET_NAME",
        jobContext.getProperties().getProperty(S3_BUCKET_PROPERTY, "cargo-tracker-uploads"));
    String failedPrefix = System.getenv().getOrDefault("S3_FAILED_PREFIX",
        jobContext.getProperties().getProperty(S3_FAILED_PREFIX_PROPERTY, "failed/"));

    EventLineParseException parseException = (EventLineParseException) e;

    logger.log(Level.WARNING, "Problem parsing event file line", parseException);

    String failedKey = failedPrefix + "failed_"
        + jobContext.getJobName()
        + "_"
        + jobContext.getInstanceId()
        + ".csv";

    // Write failed line to Amazon S3 for durable storage
    S3Client s3Client = S3Client.builder().build();

    // Read existing content from S3 if it exists (append semantics)
    StringBuilder existingContent = new StringBuilder();
    try {
      GetObjectRequest getRequest = GetObjectRequest.builder()
          .bucket(bucketName)
          .key(failedKey)
          .build();
      byte[] existingBytes = s3Client.getObjectAsBytes(getRequest).asByteArray();
      existingContent.append(new String(existingBytes, StandardCharsets.UTF_8));
    } catch (NoSuchKeyException ex) {
      // Object doesn't exist yet, start fresh
      logger.log(Level.INFO, "Creating new failed records object in S3: {0}", failedKey);
    }

    existingContent.append(parseException.getLine()).append("\n");

    byte[] contentBytes = existingContent.toString().getBytes(StandardCharsets.UTF_8);
    PutObjectRequest putRequest = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(failedKey)
        .contentType("text/csv")
        .build();
    s3Client.putObject(putRequest, RequestBody.fromInputStream(
        new java.io.ByteArrayInputStream(contentBytes), contentBytes.length));

    logger.log(Level.INFO, "Wrote failed line to S3: {0}", failedKey);
  }
}
