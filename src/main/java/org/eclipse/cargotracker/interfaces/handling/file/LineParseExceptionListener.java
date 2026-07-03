package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
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

@Dependent
@Named("LineParseExceptionListener")
public class LineParseExceptionListener implements SkipReadListener {

  private static final String FAILED_BUCKET = "failed_bucket";

  @Inject private Logger logger;

  @Inject private JobContext jobContext;
  @Inject private S3Client s3Client;

  @Override
  public void onSkipReadItem(Exception e) throws Exception {
    String bucketName = jobContext.getProperties().getProperty(FAILED_BUCKET);

    EventLineParseException parseException = (EventLineParseException) e;

    logger.log(Level.WARNING, "Problem parsing event file line", parseException);

    try (StringWriter sw = new StringWriter();
         PrintWriter failed = new PrintWriter(sw)) {
      failed.println(parseException.getLine());
      
      String content = sw.toString();
      String key = "failed_"
                  + jobContext.getJobName()
                  + "_"
                  + jobContext.getInstanceId()
                  + ".csv";
      
      PutObjectRequest putObjectRequest = PutObjectRequest.builder()
          .bucket(bucketName)
          .key(key)
          .build();
      
      s3Client.putObject(putObjectRequest, RequestBody.fromString(content));
    }
  }
}
