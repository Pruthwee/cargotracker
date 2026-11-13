package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.ByteArrayOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.batch.api.chunk.listener.SkipReadListener;
import jakarta.batch.runtime.context.JobContext;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Dependent
@Named("LineParseExceptionListener")
public class LineParseExceptionListener implements SkipReadListener {

  private static final String FAILED_DIRECTORY = "failed_directory";

  @Inject private Logger logger;

  @Inject private JobContext jobContext;

  private S3Client s3Client;
  private String bucketName;

  @Override
  public void onSkipReadItem(Exception e) throws Exception {
    String failedPrefix = jobContext.getProperties().getProperty(FAILED_DIRECTORY);

    try {
      s3Client = S3Client.create();
      bucketName = System.getenv("APP_S3_BUCKET");
      if (bucketName == null || bucketName.isEmpty()) {
        logger.log(Level.WARNING, "APP_S3_BUCKET is not set, cannot write failed line");
        return;
      }
    } catch (SdkException ex) {
      logger.log(Level.WARNING, "Failed to create S3 client", ex);
      return;
    }

    EventLineParseException parseException = (EventLineParseException) e;

    logger.log(Level.WARNING, "Problem parsing event file line", parseException);

    String key =
        failedPrefix
            + "/failed_"
            + jobContext.getJobName()
            + "_"
            + jobContext.getInstanceId()
            + ".csv";

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    buffer.writeBytes((parseException.getLine() + "\n").getBytes());

    try {
      // Append to existing object if it exists, otherwise create new
      String existingContent = "";
      try {
        GetObjectRequest getRequest =
            GetObjectRequest.builder().bucket(bucketName).key(key).build();
        existingContent =
            new String(s3Client.getObjectAsBytes(getRequest).asByteArray());
      } catch (SdkException getEx) {
        logger.log(Level.INFO, "No existing failed file, creating new one");
      }

      PutObjectRequest putRequest =
          PutObjectRequest.builder().bucket(bucketName).key(key).build();
      s3Client.putObject(
          putRequest, RequestBody.fromBytes((existingContent + buffer.toString()).getBytes()));
    } catch (SdkException putEx) {
      logger.log(Level.WARNING, "Failed to write failed line to S3", putEx);
    }
  }
}
