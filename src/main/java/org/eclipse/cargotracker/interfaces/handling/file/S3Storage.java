package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Amazon S3 storage used by the handling event file batch job in place of the local upload,
 * archive and failed directories.
 *
 * <p>The bucket comes from the APP_S3_BUCKET environment variable, the region and the credentials
 * from the AWS SDK default chain. The client is built on first use, so the application starts when
 * S3 cannot be reached; every SDK failure is reported as an {@link IOException}, as the local file
 * access it replaces did.
 */
@ApplicationScoped
public class S3Storage {

  static final String BUCKET_ENV = "APP_S3_BUCKET";
  private static final String CSV_CONTENT_TYPE = "text/csv";

  private S3Client s3Client;

  /** Turns a configured directory-like value into an S3 key prefix such as "archive/". */
  public static String toPrefix(String directory) {
    String prefix = directory == null ? "" : directory.trim();

    while (prefix.startsWith("/")) {
      prefix = prefix.substring(1);
    }

    if (!prefix.isEmpty() && !prefix.endsWith("/")) {
      prefix = prefix + "/";
    }

    return prefix;
  }

  /** Lists the keys directly under the prefix, like the files of a directory. */
  public List<String> list(String prefix) throws IOException {
    try {
      List<String> keys = new ArrayList<>();
      ListObjectsV2Request request =
          ListObjectsV2Request.builder().bucket(bucket()).prefix(prefix).delimiter("/").build();

      for (S3Object object : client().listObjectsV2Paginator(request).contents()) {
        if (!object.key().equals(prefix) && !object.key().endsWith("/")) {
          keys.add(object.key());
        }
      }

      return keys;
    } catch (SdkException e) {
      throw new IOException("Cannot list S3 objects under " + prefix, e);
    }
  }

  /** Opens the object for reading, starting at the given byte offset. */
  public InputStream open(String key, long offset) throws IOException {
    try {
      GetObjectRequest.Builder request = GetObjectRequest.builder().bucket(bucket()).key(key);

      if (offset > 0) {
        request.range("bytes=" + offset + "-");
      }

      return client().getObject(request.build());
    } catch (S3Exception e) {
      if (e.statusCode() == 416) {
        // The offset is at the end of the object, like a seek to the end of a file.
        return new ByteArrayInputStream(new byte[0]);
      }

      throw new IOException("Cannot read S3 object " + key, e);
    } catch (SdkException e) {
      throw new IOException("Cannot read S3 object " + key, e);
    }
  }

  public void delete(String key) throws IOException {
    try {
      client().deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(key).build());
    } catch (SdkException e) {
      throw new IOException("Cannot delete S3 object " + key, e);
    }
  }

  /**
   * Appends the text to the object, creating it when it does not exist. S3 objects cannot be
   * appended to, so the object is read and written back; the batch job writes one object from one
   * thread.
   */
  public synchronized void append(String key, String text) throws IOException {
    try {
      String existing = "";

      try {
        existing =
            client()
                .getObjectAsBytes(GetObjectRequest.builder().bucket(bucket()).key(key).build())
                .asUtf8String();
      } catch (NoSuchKeyException e) {
        // First write to this object.
      }

      client()
          .putObject(
              PutObjectRequest.builder()
                  .bucket(bucket())
                  .key(key)
                  .contentType(CSV_CONTENT_TYPE)
                  .build(),
              RequestBody.fromBytes((existing + text).getBytes(StandardCharsets.UTF_8)));
    } catch (SdkException e) {
      throw new IOException("Cannot write S3 object " + key, e);
    }
  }

  private String bucket() throws IOException {
    String bucket = System.getenv(BUCKET_ENV);

    if (bucket == null || bucket.trim().isEmpty()) {
      throw new IOException(BUCKET_ENV + " is not set");
    }

    return bucket.trim();
  }

  private synchronized S3Client client() {
    if (s3Client == null) {
      s3Client = S3Client.create();
    }

    return s3Client;
  }

  @PreDestroy
  public synchronized void close() {
    if (s3Client != null) {
      s3Client.close();
      s3Client = null;
    }
  }
}
