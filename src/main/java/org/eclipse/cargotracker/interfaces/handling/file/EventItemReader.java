package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.time.Clock;
import java.time.LocalDateTime;
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
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Reads handling event records from CSV files stored in an Amazon S3 bucket.
 *
 * <p>The job property {@code s3_bucket_name} specifies the S3 bucket, and
 * {@code upload_prefix} specifies the S3 key prefix (folder) where upload files reside.
 * File-system dependencies have been replaced with Amazon S3 object storage
 * (AWS SDK for Java v2) to ensure cloud-readiness.
 */
@Dependent
@Named("EventItemReader")
public class EventItemReader extends AbstractItemReader {

  /** Job property: name of the S3 bucket that holds upload objects. */
  private static final String S3_BUCKET_NAME = "s3_bucket_name";

  /** Job property: S3 key prefix (folder) for upload CSV files. */
  private static final String UPLOAD_PREFIX = "upload_prefix";

  @Inject private Logger logger;

  @Inject private JobContext jobContext;

  private S3Client s3Client;
  private String bucketName;
  private String uploadPrefix;

  /** Ordered list of S3 object keys discovered in the upload prefix. */
  private List<String> s3Keys;

  /** Index of the key currently being processed. */
  private int currentKeyIndex;

  /** Lines of the current S3 object, loaded entirely into memory. */
  private List<String> currentLines;

  /** Line index within {@link #currentLines}. */
  private int currentLineIndex;

  @Override
  public void open(Serializable checkpoint) throws Exception {
    bucketName = jobContext.getProperties().getProperty(S3_BUCKET_NAME);
    uploadPrefix = jobContext.getProperties().getProperty(UPLOAD_PREFIX);

    // Build a region-aware S3 client; region is resolved from the environment
    // (AWS_REGION env var, ~/.aws/config, or EC2/ECS instance metadata).
    s3Client = S3Client.builder().build();

    if (checkpoint == null) {
      logger.log(Level.INFO, "Scanning S3 upload prefix: s3://{0}/{1}", new Object[]{bucketName, uploadPrefix});
      s3Keys = listUploadKeys();
      currentKeyIndex = 0;
      currentLines = null;
      currentLineIndex = 0;
    } else {
      logger.log(Level.INFO, "Resuming from previous checkpoint");
      S3EventFilesCheckpoint s3Checkpoint = (S3EventFilesCheckpoint) checkpoint;
      s3Keys = s3Checkpoint.getS3Keys();
      currentKeyIndex = s3Checkpoint.getCurrentKeyIndex();
      currentLineIndex = s3Checkpoint.getCurrentLineIndex();
      currentLines = null; // will be (re-)loaded on first readItem call
    }

    loadCurrentObject();
  }

  /** Lists all S3 object keys under the configured upload prefix. */
  private List<String> listUploadKeys() {
    List<String> keys = new ArrayList<>();
    String prefix = (uploadPrefix != null && !uploadPrefix.isEmpty())
        ? (uploadPrefix.endsWith("/") ? uploadPrefix : uploadPrefix + "/")
        : "";

    ListObjectsV2Request request = ListObjectsV2Request.builder()
        .bucket(bucketName)
        .prefix(prefix)
        .build();

    ListObjectsV2Response response;
    do {
      response = s3Client.listObjectsV2(request);
      for (S3Object obj : response.contents()) {
        // Skip "directory" placeholder keys
        if (!obj.key().endsWith("/")) {
          keys.add(obj.key());
        }
      }
      request = request.toBuilder()
          .continuationToken(response.nextContinuationToken())
          .build();
    } while (Boolean.TRUE.equals(response.isTruncated()));

    logger.log(Level.INFO, "Found {0} file(s) to process in S3", keys.size());
    return keys;
  }

  /**
   * Downloads the S3 object at {@link #currentKeyIndex} and stores its lines
   * in {@link #currentLines}, starting from {@link #currentLineIndex}.
   */
  private void loadCurrentObject() throws Exception {
    if (s3Keys == null || currentKeyIndex >= s3Keys.size()) {
      currentLines = null;
      return;
    }

    String key = s3Keys.get(currentKeyIndex);
    logger.log(Level.INFO, "Loading S3 object: s3://{0}/{1}", new Object[]{bucketName, key});

    GetObjectRequest getRequest = GetObjectRequest.builder()
        .bucket(bucketName)
        .key(key)
        .build();

    List<String> lines = new ArrayList<>();
    try (ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getRequest);
         BufferedReader reader = new BufferedReader(new InputStreamReader(s3Stream))) {
      String line;
      while ((line = reader.readLine()) != null) {
        lines.add(line);
      }
    }
    currentLines = lines;
    // currentLineIndex is already set (either 0 for a new file or restored from checkpoint)
  }

  @Override
  public Object readItem() throws Exception {
    if (currentLines == null) {
      return null;
    }

    // Advance past already-processed lines (e.g. after checkpoint restore)
    while (currentLineIndex < currentLines.size()) {
      String line = currentLines.get(currentLineIndex);
      currentLineIndex++;
      return parseLine(line);
    }

    // Current object exhausted — delete it from S3 and move to the next one
    String processedKey = s3Keys.get(currentKeyIndex);
    logger.log(Level.INFO, "Finished processing S3 object, deleting: s3://{0}/{1}",
        new Object[]{bucketName, processedKey});
    s3Client.deleteObject(DeleteObjectRequest.builder()
        .bucket(bucketName)
        .key(processedKey)
        .build());

    currentKeyIndex++;
    currentLineIndex = 0;

    if (currentKeyIndex >= s3Keys.size()) {
      logger.log(Level.INFO, "No more S3 objects to process");
      currentLines = null;
      return null;
    }

    loadCurrentObject();
    return readItem();
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

    return new HandlingEventRegistrationAttempt(
        LocalDateTime.now(Clock.systemUTC()), completionTime, trackingId, voyageNumber, eventType, unLocode);
  }

  @Override
  public Serializable checkpointInfo() throws Exception {
    return new S3EventFilesCheckpoint(s3Keys, currentKeyIndex, currentLineIndex);
  }

  @Override
  public void close() throws Exception {
    if (s3Client != null) {
      s3Client.close();
    }
  }
}
