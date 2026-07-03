package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
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
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

@Dependent
@Named("EventItemReader")
public class EventItemReader extends AbstractItemReader {

  private static final String UPLOAD_BUCKET = "upload_bucket";

  @Inject private Logger logger;
  @Inject private S3Client s3Client;

  @Inject private JobContext jobContext;
  private EventFilesCheckpoint checkpoint;
  private BufferedReader currentFileReader;
  private String currentS3Key;

  @Override
  public void open(Serializable checkpoint) throws Exception {
    String bucketName = jobContext.getProperties().getProperty(UPLOAD_BUCKET);

    if (checkpoint == null) {
      this.checkpoint = new EventFilesCheckpoint();
      logger.log(Level.INFO, "Scanning S3 bucket: {0}", bucketName);

      ListObjectsV2Response listResponse = s3Client.listObjectsV2(
          ListObjectsV2Request.builder().bucket(bucketName).build());
      
      List<String> keys = listResponse.contents().stream()
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
      currentFileReader = null;
    } else {
      openS3File(bucketName, key);
    }
  }

  private void openS3File(String bucketName, String key) throws IOException {
    GetObjectRequest getObjectRequest = GetObjectRequest.builder()
        .bucket(bucketName)
        .key(key)
        .build();
    
    ResponseInputStream<?> s3InputStream = s3Client.getObject(getObjectRequest);
    this.currentFileReader = new BufferedReader(new InputStreamReader(s3InputStream));
    this.currentS3Key = key;
    logger.log(Level.INFO, "Processing S3 object: {0}", key);
  }

  @Override
  public Object readItem() throws Exception {
    if (currentFileReader != null) {
      String line = currentFileReader.readLine();

      if (line != null) {
        this.checkpoint.setFilePointer(0); 
        return parseLine(line);
      } else {
        String key = this.checkpoint.currentFile();
        String bucketName = jobContext.getProperties().getProperty(UPLOAD_BUCKET);
        logger.log(Level.INFO, "Finished processing S3 object, deleting: {0}", key);
        currentFileReader.close();
        
        s3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build());

        String nextKey = this.checkpoint.nextFile();

        if (nextKey == null) {
          logger.log(Level.INFO, "No more files to process");
          return null;
        } else {
          openS3File(bucketName, nextKey);
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
        new HandlingEventRegistrationAttempt(
            LocalDateTime.now(java.time.Clock.systemUTC()), completionTime, trackingId, voyageNumber, eventType, unLocode);
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
