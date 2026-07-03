package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

/**
 * Checkpoint for tracking S3 object processing state.
 * Replaces java.io.File-based checkpoint with S3 key-based tracking
 * for cloud-native, durable storage without host-level file system dependencies.
 */
public class EventFilesCheckpoint implements Serializable {

  private static final long serialVersionUID = 1L;

  // Use S3 object keys instead of local File references for cloud-native storage
  private List<String> s3Keys = new LinkedList<>();
  private int keyIndex = 0;
  private long filePointer = 0;

  public void setS3Keys(List<String> s3Keys) {
    this.s3Keys = s3Keys;
  }

  public long getFilePointer() {
    return filePointer;
  }

  public void setFilePointer(long filePointer) {
    this.filePointer = filePointer;
  }

  public String currentFile() {
    if (s3Keys.size() > keyIndex) {
      return s3Keys.get(keyIndex);
    } else {
      return null;
    }
  }

  public String nextFile() {
    filePointer = 0;

    if (s3Keys.size() > ++keyIndex) {
      return s3Keys.get(keyIndex);
    } else {
      return null;
    }
  }
}
