package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
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
import org.eclipse.cargotracker.infrastructure.storage.S3ObjectStore;
import org.eclipse.cargotracker.interfaces.handling.HandlingEventRegistrationAttempt;

@Dependent
@Named("EventItemReader")
public class EventItemReader extends AbstractItemReader {

  private static final String UPLOAD_DIRECTORY = "upload_directory";

  @Inject private Logger logger;

  @Inject private JobContext jobContext;
  @Inject private S3ObjectStore objectStore;
  private EventFilesCheckpoint checkpoint;

  /** Content of the event file being processed, read from S3, and the read position in it. */
  private byte[] currentFile;

  private int currentPosition;

  @Override
  public void open(Serializable checkpoint) throws Exception {
    String uploadPrefix =
        S3ObjectStore.prefixOf(jobContext.getProperties().getProperty(UPLOAD_DIRECTORY));

    if (checkpoint == null) {
      this.checkpoint = new EventFilesCheckpoint();
      logger.log(Level.INFO, "Scanning upload prefix in S3: {0}", uploadPrefix);

      this.checkpoint.setFiles(objectStore.list(uploadPrefix));
    } else {
      logger.log(Level.INFO, "Starting from previous checkpoint");
      this.checkpoint = (EventFilesCheckpoint) checkpoint;
    }

    String file = this.checkpoint.currentFile();

    if (file == null) {
      logger.log(Level.INFO, "No files to process");
      currentFile = null;
    } else {
      currentFile = objectStore.read(file);
      logger.log(Level.INFO, "Processing file: {0}", file);
      currentPosition = (int) this.checkpoint.getFilePointer();
    }
  }

  @Override
  public Object readItem() throws Exception {
    if (currentFile != null) {
      String line = readLine();

      if (line != null) {
        this.checkpoint.setFilePointer(currentPosition);
        return parseLine(line);
      } else {
        logger.log(
            Level.INFO, "Finished processing file, deleting: {0}", this.checkpoint.currentFile());
        objectStore.delete(this.checkpoint.currentFile());
        String nextFile = this.checkpoint.nextFile();

        if (nextFile == null) {
          logger.log(Level.INFO, "No more files to process");
          currentFile = null;
          return null;
        } else {
          currentFile = objectStore.read(nextFile);
          currentPosition = 0;
          logger.log(Level.INFO, "Processing file: {0}", nextFile);
          return readItem();
        }
      }
    } else {
      return null;
    }
  }

  /** Reads the next line the way RandomAccessFile.readLine did: \n, \r and \r\n end a line. */
  private String readLine() {
    if (currentPosition >= currentFile.length) {
      return null;
    }

    StringBuilder line = new StringBuilder();

    while (currentPosition < currentFile.length) {
      int c = currentFile[currentPosition++] & 0xff;

      if (c == '\n') {
        break;
      } else if (c == '\r') {
        if (currentPosition < currentFile.length && currentFile[currentPosition] == '\n') {
          currentPosition++;
        }
        break;
      }

      line.append((char) c);
    }

    return line.toString();
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
