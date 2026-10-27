package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Helper for reading and writing event files to Amazon S3, replacing local file system operations.
 * The S3 client and bucket name are lazily initialized on first use.
 */
@Singleton
@Named("EventFileS3Helper")
public class EventFileS3Helper {

  private static final String S3_BUCKET_ENV = "EVENT_FILES_S3_BUCKET";
  private static final String UPLOAD_PREFIX_ENV = "EVENT_FILES_UPLOAD_PREFIX";
  private static final String ARCHIVE_PREFIX_ENV = "EVENT_FILES_ARCHIVE_PREFIX";
  private static final String FAILED_PREFIX_ENV = "EVENT_FILES_FAILED_PREFIX";

  @Inject private Logger logger;

  private S3Client s3Client;
  private String bucketName;
  private String uploadPrefix;
  private String archivePrefix;
  private String failedPrefix;

  /** Lazily initializes the S3 client and configuration from environment variables. */
  private synchronized void ensureInitialized() throws IOException {
    if (s3Client != null) {
      return;
    }
    try {
      bucketName = System.getenv(S3_BUCKET_ENV);
      uploadPrefix = System.getenv(UPLOAD_PREFIX_ENV);
      archivePrefix = System.getenv(ARCHIVE_PREFIX_ENV);
      failedPrefix = System.getenv(FAILED_PREFIX_ENV);

      if (bucketName == null || bucketName.isEmpty()) {
        throw new IOException(S3_BUCKET_ENV + " environment variable is not set");
      }
      if (uploadPrefix == null || uploadPrefix.isEmpty()) {
        throw new IOException(UPLOAD_PREFIX_ENV + " environment variable is not set");
      }
      if (archivePrefix == null || archivePrefix.isEmpty()) {
        throw new IOException(ARCHIVE_PREFIX_ENV + " environment variable is not set");
      }
      if (failedPrefix == null || failedPrefix.isEmpty()) {
        throw new IOException(FAILED_PREFIX_ENV + " environment variable is not set");
      }

      s3Client = S3Client.builder().build();
    } catch (SdkException e) {
      throw new IOException("Failed to initialize S3 client", e);
    }
  }

  /** Lists all object keys under the upload prefix, simulating directory listing. */
  public List<String> listUploadFiles() throws IOException {
    ensureInitialized();
    try {
      ListObjectsV2Request request =
          ListObjectsV2Request.builder()
              .bucket(bucketName)
              .prefix(uploadPrefix.endsWith("/") ? uploadPrefix : uploadPrefix + "/")
              .build();

      List<String> keys = new ArrayList<>();
      s3Client
          .listObjectsV2Paginator(request)
          .contents()
          .stream()
          .map(S3Object::key)
          .filter(key -> !key.endsWith("/"))
          .forEach(keys::add);

      logger.log(Level.INFO, "Found {0} files in S3 upload prefix: {1}", new Object[] {keys.size(), uploadPrefix});
      return keys;
    } catch (SdkException e) {
      throw new IOException("Failed to list S3 objects", e);
    }
  }

  /** Reads the full content of an S3 object as a string. */
  public String readObjectAsString(String key) throws IOException {
    ensureInitialized();
    try {
      GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build();
      try (InputStream inputStream = s3Client.getObject(getRequest)) {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      }
    } catch (SdkException e) {
      throw new IOException("Failed to read S3 object: " + key, e);
    }
  }

  /** Reads the full content of an S3 object as bytes. */
  public byte[] readObjectAsBytes(String key) throws IOException {
    ensureInitialized();
    try {
      GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build();
      try (InputStream inputStream = s3Client.getObject(getRequest)) {
        return inputStream.readAllBytes();
      }
    } catch (SdkException e) {
      throw new IOException("Failed to read S3 object: " + key, e);
    }
  }

  /** Uploads a string to the archive prefix in S3. */
  public void writeArchiveFile(String fileName, String content) throws IOException {
    ensureInitialized();
    String key =
        archivePrefix.endsWith("/")
            ? archivePrefix + fileName
            : archivePrefix + "/" + fileName;
    try {
      PutObjectRequest putRequest =
          PutObjectRequest.builder()
              .bucket(bucketName)
              .key(key)
              .contentType("text/csv")
              .build();
      s3Client.putObject(putRequest, RequestBody.fromString(content));
      logger.log(Level.INFO, "Archived event file to S3: {0}", key);
    } catch (SdkException e) {
      throw new IOException("Failed to write archive to S3: " + key, e);
    }
  }

  /** Uploads a single line to the failed prefix in S3, appending to an existing file. */
  public void appendFailedLine(String fileName, String line) throws IOException {
    ensureInitialized();
    String key =
        failedPrefix.endsWith("/")
            ? failedPrefix + fileName
            : failedPrefix + "/" + fileName;

    // Read existing content (if any), append the line, and write back
    String existingContent = "";
    try {
      GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build();
      try (InputStream inputStream = s3Client.getObject(getRequest)) {
        existingContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      }
    } catch (SdkException e) {
      // File doesn't exist yet, that's fine
      logger.log(Level.FINE, "No existing failed file in S3, creating new: {0}", key);
    }

    String newContent = existingContent + line + System.lineSeparator();
    try {
      PutObjectRequest putRequest =
          PutObjectRequest.builder()
              .bucket(bucketName)
              .key(key)
              .contentType("text/csv")
              .build();
      s3Client.putObject(putRequest, RequestBody.fromString(newContent));
      logger.log(Level.INFO, "Appended failed line to S3: {0}", key);
    } catch (SdkException e) {
      throw new IOException("Failed to write failed line to S3: " + key, e);
    }
  }

  public String getBucketName() {
    return bucketName;
  }

  public String getUploadPrefix() {
    return uploadPrefix;
  }

  public String getArchivePrefix() {
    return archivePrefix;
  }

  public String getFailedPrefix() {
    return failedPrefix;
  }
}
