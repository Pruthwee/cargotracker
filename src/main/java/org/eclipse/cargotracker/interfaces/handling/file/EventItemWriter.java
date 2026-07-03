package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.List;
import java.nio.charset.StandardCharsets;
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

@Dependent
@Named("EventItemWriter")
public class EventItemWriter extends AbstractItemWriter {

  private static final String ARCHIVE_BUCKET = "archive_bucket";

  @Inject private JobContext jobContext;
  @Inject private ApplicationEvents applicationEvents;
  @Inject private S3Client s3Client;

  @Override
  public void open(Serializable checkpoint) throws Exception {
    // S3 buckets are created outside the application or via infrastructure code.
    // No need to check for existence/create directory here.
  }

  @Override
  @Transactional
  public void writeItems(List<Object> items) throws Exception {
    // S3 buckets are created outside the application or via infrastructure code.
    String key = "archive_"
                + jobContext.getJobName()
                + "_"
                + jobContext.getInstanceId()
                + ".csv";

    try (StringWriter sw = new StringWriter();
      String bucketName = jobContext.getProperties().getProperty(ARCHIVE_BUCKET);
      PutObjectRequest putObjectRequest = PutObjectRequest.builder()
          .bucket(bucketName)
          .key(key)
          .build();                applicationEvents.receivedHandlingEventRegistrationAttempt(attempt);
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

      String content = sw.toString();
      
      PutObjectRequest putObjectRequest = PutObjectRequest.builder()
          .bucket(bucketName)
          .key(key)
          .build();
      
      s3Client.putObject(putObjectRequest, RequestBody.fromString(content));
    }
  }
}
