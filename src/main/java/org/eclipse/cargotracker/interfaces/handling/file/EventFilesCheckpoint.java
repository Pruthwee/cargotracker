package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Legacy checkpoint class retained for binary compatibility.
 *
 * <p>This class previously tracked local {@code java.io.File} objects and byte offsets.
 * All file-system dependencies have been removed. Active checkpoint tracking is now
 * handled by {@link S3EventFilesCheckpoint}, which stores S3 object keys and line
 * indices instead of local file references.
 *
 * @deprecated Use {@link S3EventFilesCheckpoint} for cloud-native S3-backed checkpointing.
 */
@Deprecated
public class EventFilesCheckpoint implements Serializable {

  private static final long serialVersionUID = 1L;

  /** S3 object keys to process (replaces the old local File list). */
  private List<String> s3Keys = new ArrayList<>();

  /** Index of the S3 key currently being processed. */
  private int keyIndex = 0;

  /** Line index within the current S3 object (replaces the old byte-offset filePointer). */
  private long lineIndex = 0;

  public List<String> getS3Keys() {
    return s3Keys;
  }

  public void setS3Keys(List<String> s3Keys) {
    this.s3Keys = s3Keys != null ? new ArrayList<>(s3Keys) : new ArrayList<>();
  }

  public long getLineIndex() {
    return lineIndex;
  }

  public void setLineIndex(long lineIndex) {
    this.lineIndex = lineIndex;
  }

  public String currentKey() {
    if (s3Keys.size() > keyIndex) {
      return s3Keys.get(keyIndex);
    }
    return null;
  }

  public String nextKey() {
    lineIndex = 0;
    if (s3Keys.size() > ++keyIndex) {
      return s3Keys.get(keyIndex);
    }
    return null;
  }
}
