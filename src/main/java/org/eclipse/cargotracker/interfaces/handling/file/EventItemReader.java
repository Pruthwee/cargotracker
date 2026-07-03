package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Reads handling event files from Amazon S3 instead of local file system.
 * Replaces hard-coded file paths and java.io.File usage with S3 object storage
 * for cloud-native, durable, and scalable storage.
 */
@Dependent
@Named("EventItemReader")
public class EventItemReader extends AbstractItemReader {

  private static final String S3_BUCKET_PROPERTY = "s3_bucket";
  private static final String S3_PREFIX_PROPERTY = "upload_prefix";

  @Inject private Logger logger;

  @Inject private JobContext jobContext;
  private EventFilesCheckpoint checkpoint;
  private BufferedReader currentReader;
  private List<String> s3Keys;
  private int currentKeyIndex;

  private S3Client s3Client;

  @Override
  public void open(Serializable checkpoint) throws Exception {
    String bucketName = System.getenv().getOrDefault("S3_BUCKET_NAME",
        jobContext.getProperties().getProperty(S3_BUCKET_PROPERTY, "cargo-tracker-uploads"));
    String prefix = System.getenv().getOrDefault("S3_UPLOAD_PREFIX",
        jobContext.getProperties().getProperty(S3_PREFIX_PROPERTY, "upload/"));

    s3Client = S3Client.builder().build();

    if (checkpoint == null) {
      this.checkpoint = new EventFilesCheckpoint();
      logger.log(Level.INFO, "Scanning S3 bucket: {0} with prefix: {1}", new Object[]{bucketName, prefix});

      // Use ListObjectsV2 to discover objects in S3 (replaces local directory scanning)
      s3Keys = listS3Objects(bucketName, prefix);
      currentKeyIndex = 0;

      if (s3Keys.isEmpty()) {
        logger.log(Level.INFO, "No files found in S3 bucket/prefix");
        currentReader = null;
      } else {
        openS3Object(bucketName, s3Keys.get(currentKeyIndex));
      }
    } else {
      logger.log(Level.INFO, "Starting from previous checkpoint");
      this.checkpoint = (EventFilesCheckpoint) checkpoint;
      s3Keys = listS3Objects(bucketName, prefix);
      currentKeyIndex = 0;
      if (!s3Keys.isEmpty()) {
        openS3Object(bucketName, s3Keys.get(currentKeyIndex));
      }
    }
  }

  private List<String> listS3Objects(String bucketName, String prefix) {
    List<String> keys = new ArrayList<>();
    try {
      ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
          .bucket(bucketName)
          .prefix(prefix)
          .build();

      ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
      for (S3Object s3Object : listResponse.contents()) {
        keys.add(s3Object.key());
      }
    } catch (Exception e) {
      logger.log(Level.WARNING, "Error listing S3 objects: {0}", e.getMessage());
    }
    return keys;
  }

  private void openS3Object(String bucketName, String key) {
    try {
      GetObjectRequest getRequest = GetObjectRequest.builder()
          .bucket(bucketName)
          .key(key)
          .build();
      ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getRequest);
      currentReader = new BufferedReader(new InputStreamReader(s3Object));
      logger.log(Level.INFO, "Processing S3 object: {0}", key);
    } catch (Exception e) {
      logger.log(Level.WARNING, "Error opening S3 object {0}: {1}", new Object[]{key, e.getMessage()});
      currentReader = null;
    }
  }

  @Override
  public Object readItem() throws Exception {
    if (currentReader != null) {
      String line = currentReader.readLine();

      if (line != null) {
        return parseLine(line);
      } else {
        currentReader.close();
        currentKeyIndex++;

        if (currentKeyIndex >= s3Keys.size()) {
          logger.log(Level.INFO, "No more S3 objects to process");
          return null;
        } else {
          String bucketName = System.getenv().getOrDefault("S3_BUCKET_NAME",
              jobContext.getProperties().getProperty(S3_BUCKET_PROPERTY, "cargo-tracker-uploads"));
          openS3Object(bucketName, s3Keys.get(currentKeyIndex));
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

    // Use UTC-based Instant for cloud-native time handling
    HandlingEventRegistrationAttempt attempt =
        new HandlingEventRegistrationAttempt(
            LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC),
            completionTime, trackingId, voyageNumber, eventType, unLocode);

    return attempt;
  }

  @Override
  public Serializable checkpointInfo() throws Exception {
    return this.checkpoint;
  }
}
