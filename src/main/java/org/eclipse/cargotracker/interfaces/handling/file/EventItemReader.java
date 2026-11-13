package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jakarta.batch.api.chunk.AbstractItemReader;
import jakarta.batch.runtime.context.JobContext;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.cargotracker.application.util.DateConverter;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import org.eclipse.cargotracker.domain.model.handling.HandlingEvent;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import org.eclipse.cargotracker.domain.model.voyage.VoyageNumber;
import org.eclipse.cargotracker.interfaces.handling.HandlingEventRegistrationAttempt;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

@Dependent
@Named("EventItemReader")
public class EventItemReader extends AbstractItemReader {

  private static final String UPLOAD_DIRECTORY = "upload_directory";

  @Inject private Logger logger;

  @Inject private JobContext jobContext;
  private EventFilesCheckpoint checkpoint;
  private BufferedReader currentReader;
  private String currentKey;

  private S3Client s3Client;
  private String bucketName;

  @Override
  public void open(Serializable checkpoint) throws Exception {
    String uploadPrefix = jobContext.getProperties().getProperty(UPLOAD_DIRECTORY);

    try {
      s3Client = S3Client.create();
      bucketName = System.getenv("APP_S3_BUCKET");
      if (bucketName == null || bucketName.isEmpty()) {
        logger.log(Level.WARNING, "APP_S3_BUCKET is not set, no files to process");
        this.checkpoint = new EventFilesCheckpoint();
        return;
      }

      if (checkpoint == null) {
        this.checkpoint = new EventFilesCheckpoint();
        logger.log(Level.INFO, "Scanning upload prefix: {0}", uploadPrefix);

        ListObjectsV2Request listRequest =
            ListObjectsV2Request.builder().bucket(bucketName).prefix(uploadPrefix).build();

        ListObjectsV2Response response = s3Client.listObjectsV2(listRequest);

        List<String> keys =
            response.contents().stream()
                .filter(obj -> !obj.key().endsWith("/"))
                .map(S3Object::key)
                .collect(Collectors.toList());

        this.checkpoint.setFiles(keys);
      } else {
        logger.log(Level.INFO, "Starting from previous checkpoint");
        this.checkpoint = (EventFilesCheckpoint) checkpoint;
      }

      String key = this.checkpoint.currentFile();

      if (key == null) {
        logger.log(Level.INFO, "No files to process");
        currentReader = null;
        currentKey = null;
      } else {
        currentKey = key;
        logger.log(Level.INFO, "Processing file: {0}", key);
        GetObjectRequest getRequest =
            GetObjectRequest.builder().bucket(bucketName).key(key).build();
        currentReader =
            new BufferedReader(
                new InputStreamReader(
                    s3Client.getObjectAsBytes(getRequest).asInputStream(),
                    StandardCharsets.UTF_8));
        long skipped = currentReader.skip(this.checkpoint.getFilePointer());
        logger.log(Level.INFO, "Skipped {0} bytes", skipped);
      }
    } catch (SdkException e) {
      logger.log(Level.WARNING, "Failed to access S3 for upload directory, no files to process", e);
      this.checkpoint = new EventFilesCheckpoint();
    }
  }

  @Override
  public Object readItem() throws Exception {
    if (currentReader != null) {
      String line = currentReader.readLine();

      if (line != null) {
        this.checkpoint.setFilePointer(this.checkpoint.getFilePointer() + line.length() + 1);
        return parseLine(line);
      } else {
        logger.log(
            Level.INFO, "Finished processing file, deleting: {0}", currentKey);
        currentReader.close();
        // Delete the processed object from S3
        try {
          s3Client.deleteObject(
              req -> req.bucket(bucketName).key(currentKey));
        } catch (SdkException e) {
          logger.log(Level.WARNING, "Failed to delete processed file from S3: " + currentKey, e);
        }
        String nextKey = this.checkpoint.nextFile();

        if (nextKey == null) {
          logger.log(Level.INFO, "No more files to process");
          currentReader = null;
          currentKey = null;
          return null;
        } else {
          currentKey = nextKey;
          logger.log(Level.INFO, "Processing file: {0}", nextKey);
          GetObjectRequest getRequest =
              GetObjectRequest.builder().bucket(bucketName).key(nextKey).build();
          currentReader =
              new BufferedReader(
                  new InputStreamReader(
                      s3Client.getObjectAsBytes(getRequest).asInputStream(),
                      StandardCharsets.UTF_8));
          return readItem();
        }
      }
    } else {
      return null;
    }
  }

  private Object parseLine(String line) throws EventLineParseException {
    String[] result = line.split(",");

    if (result.length != 5) {
      throw new EventLineParseException("Wrong number of data elements", line);
    }

    LocalDateTime completionTime = null;

    try {
      completionTime = DateConverter.toDateTime(result[0]);
    } catch (DateTimeParseException e) {
      throw new EventLineParseException("Cannot parse completion time", e, line);
    }

    TrackingId trackingId = null;

    try {
      trackingId = new TrackingId(result[1]);
    } catch (NullPointerException e) {
      throw new EventLineParseException("Cannot parse tracking ID", e, line);
    }

    VoyageNumber voyageNumber = null;

    try {
      if (!result[2].isEmpty()) {
        voyageNumber = new VoyageNumber(result[2]);
      }
    } catch (NullPointerException e) {
      throw new EventLineParseException("Cannot parse voyage number", e, line);
    }

    UnLocode unLocode = null;

    try {
      unLocode = new UnLocode(result[3]);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new EventLineParseException("Cannot parse UN location code", e, line);
    }

    HandlingEvent.Type eventType = null;

    try {
      eventType = HandlingEvent.Type.valueOf(result[4]);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new EventLineParseException("Cannot parse event type", e, line);
    }

    HandlingEventRegistrationAttempt attempt =
        new HandlingEventRegistrationAttempt(
            LocalDateTime.now(), completionTime, trackingId, voyageNumber, eventType, unLocode);

    return attempt;
  }

  @Override
  public Serializable checkpointInfo() throws Exception {
    return this.checkpoint;
  }
}
