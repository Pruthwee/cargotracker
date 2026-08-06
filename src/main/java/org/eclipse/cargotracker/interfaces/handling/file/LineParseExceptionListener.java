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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Listens for skipped (unparseable) lines and writes them to an Amazon S3 failed-records bucket.
 *
 * <p>The job property {@code s3_bucket_name} specifies the S3 bucket, and
 * {@code failed_prefix} specifies the S3 key prefix (folder) for failed-record objects.
 * Local file-system dependencies have been replaced with Amazon S3 object storage
 * (AWS SDK for Java v2) to ensure cloud-readiness.
 */
@Dependent
@Named("LineParseExceptionListener")
public class LineParseExceptionListener implements SkipReadListener {

  /** Job property: name of the S3 bucket used for failed records. */
  private static final String S3_BUCKET_NAME = "s3_bucket_name";

  /** Job property: S3 key prefix (folder) for failed CSV objects. */
  private static final String FAILED_PREFIX = "failed_prefix";

  @Inject private Logger logger;

  @Inject private JobContext jobContext;

  @Override
  public void onSkipReadItem(Exception e) throws Exception {
    EventLineParseException parseException = (EventLineParseException) e;

    logger.log(Level.WARNING, "Problem parsing event file line", parseException);

    String bucketName = jobContext.getProperties().getProperty(S3_BUCKET_NAME);
    String failedPrefix = jobContext.getProperties().getProperty(FAILED_PREFIX);

    String prefix = (failedPrefix != null && !failedPrefix.isEmpty())
        ? (failedPrefix.endsWith("/") ? failedPrefix : failedPrefix + "/")
        : "";

    String s3Key = prefix
        + "failed_"
        + jobContext.getJobName()
        + "_"
        + jobContext.getInstanceId()
        + ".csv";

    byte[] contentBytes = parseException.getLine().getBytes(StandardCharsets.UTF_8);

    // Build a region-aware S3 client; region is resolved from the environment
    // (AWS_REGION env var, ~/.aws/config, or EC2/ECS instance metadata).
    try (S3Client s3Client = S3Client.builder().build()) {
      PutObjectRequest putRequest = PutObjectRequest.builder()
          .bucket(bucketName)
          .key(s3Key)
          .contentType("text/csv")
          .contentLength((long) contentBytes.length)
          .build();

      s3Client.putObject(putRequest, RequestBody.fromBytes(contentBytes));

      logger.log(Level.INFO, "Written failed line to S3: s3://{0}/{1}",
          new Object[]{bucketName, s3Key});
    }
  }
}
