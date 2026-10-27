package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;
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

@Dependent
@Named("EventItemReader")
public class EventItemReader extends AbstractItemReader {

  @Inject private Logger logger;

  @Inject private JobContext jobContext;
  @Inject private EventFileS3Helper s3Helper;

  private EventFilesCheckpoint checkpoint;
  private Scanner currentFileScanner;
  private String currentFileKey;

  @Override
  public void open(Serializable checkpoint) throws Exception {
    if (checkpoint == null) {
      this.checkpoint = new EventFilesCheckpoint();
      logger.log(Level.INFO, "Scanning S3 upload prefix for event files");

      List<String> files = s3Helper.listUploadFiles();
      this.checkpoint.setFiles(files);
    } else {
      logger.log(Level.INFO, "Starting from previous checkpoint");
      this.checkpoint = (EventFilesCheckpoint) checkpoint;
    }

    String fileKey = this.checkpoint.currentFile();

    if (fileKey == null) {
      logger.log(Level.INFO, "No files to process");
      currentFileScanner = null;
    } else {
      currentFileKey = fileKey;
      logger.log(Level.INFO, "Processing file: {0}", fileKey);
      String content = s3Helper.readObjectAsString(fileKey);
      currentFileScanner = new Scanner(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8.name());
      // Skip to the checkpointed position
      for (int i = 0; i < this.checkpoint.getFilePointer(); i++) {
        if (!currentFileScanner.hasNextLine()) {
          break;
        }
        currentFileScanner.nextLine();
      }
    }
  }

  @Override
  public Object readItem() throws Exception {
    if (currentFileScanner != null) {
      String line = currentFileScanner.hasNextLine() ? currentFileScanner.nextLine() : null;

      if (line != null) {
        this.checkpoint.setFilePointer(this.checkpoint.getFilePointer() + 1);
        return parseLine(line);
      } else {
        logger.log(
            Level.INFO, "Finished processing file, archiving: {0}", currentFileKey);
        currentFileScanner.close();
        archiveFile(currentFileKey);
        String nextFileKey = this.checkpoint.nextFile();

        if (nextFileKey == null) {
          logger.log(Level.INFO, "No more files to process");
          return null;
        } else {
          currentFileKey = nextFileKey;
          logger.log(Level.INFO, "Processing file: {0}", nextFileKey);
          String content = s3Helper.readObjectAsString(nextFileKey);
          currentFileScanner = new Scanner(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8.name());
          return readItem();
        }
      }
    } else {
      return null;
    }
  }

  /** Archives a processed file by copying its content to the archive prefix in S3. */
  private void archiveFile(String fileKey) {
    try {
      String content = s3Helper.readObjectAsString(fileKey);
      String fileName = fileKey.substring(fileKey.lastIndexOf('/') + 1);
      s3Helper.writeArchiveFile(fileName, content);
    } catch (IOException e) {
      logger.log(Level.WARNING, "Failed to archive file: " + fileKey, e);
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
