package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Checkpoint that tracks progress through a list of S3 object keys.
 *
 * <p>Replaces the original {@code EventFilesCheckpoint} which tracked local
 * {@link java.io.File} objects and byte offsets. In the S3-backed implementation
 * progress is tracked by key index and line index within the current object.
 */
public class S3EventFilesCheckpoint implements Serializable {

  private static final long serialVersionUID = 1L;

  private final List<String> s3Keys;
  private final int currentKeyIndex;
  private final int currentLineIndex;

  public S3EventFilesCheckpoint(List<String> s3Keys, int currentKeyIndex, int currentLineIndex) {
    this.s3Keys = new ArrayList<>(s3Keys != null ? s3Keys : List.of());
    this.currentKeyIndex = currentKeyIndex;
    this.currentLineIndex = currentLineIndex;
  }

  public List<String> getS3Keys() {
    return new ArrayList<>(s3Keys);
  }

  public int getCurrentKeyIndex() {
    return currentKeyIndex;
  }

  public int getCurrentLineIndex() {
    return currentLineIndex;
  }
}
