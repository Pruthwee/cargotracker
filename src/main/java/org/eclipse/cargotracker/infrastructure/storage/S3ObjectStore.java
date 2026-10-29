package org.eclipse.cargotracker.infrastructure.storage;

import java.io.IOException;
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
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Object storage in Amazon S3 in place of the local upload, archive and failed folders of the event
 * file batch job.
 *
 * <p>The bucket is read from the APP_S3_BUCKET environment variable; the region and the credentials
 * come from the AWS SDK default chain (AWS_REGION, task or instance role). The client is built on
 * first use, so the application starts when S3 cannot be reached. Every failure is reported as an
 * {@link IOException}, as the file access it replaces did.
 */
@ApplicationScoped
public class S3ObjectStore {

  public static final String BUCKET_ENV = "APP_S3_BUCKET";

  private volatile S3Client s3Client;

  /** Turns a folder setting such as "/tmp/uploads" into the key prefix "tmp/uploads/". */
  public static String prefixOf(String folder) {
    String prefix = folder.trim();

    while (prefix.startsWith("/")) {
      prefix = prefix.substring(1);
    }

    if (!prefix.isEmpty() && !prefix.endsWith("/")) {
      prefix = prefix + "/";
    }

    return prefix;
  }

  /** Lists the keys of the objects directly under the prefix, like listing a folder's files. */
  public List<String> list(String prefix) throws IOException {
    try {
      List<String> keys = new ArrayList<>();
      ListObjectsV2Request request =
          ListObjectsV2Request.builder().bucket(bucket()).prefix(prefix).delimiter("/").build();

      for (S3Object object : client().listObjectsV2Paginator(request).contents()) {
        if (!object.key().endsWith("/")) {
          keys.add(object.key());
        }
      }

      return keys;
    } catch (SdkException e) {
      throw new IOException("Cannot list S3 objects with prefix " + prefix, e);
    }
  }

  /** Reads the whole object. */
  public byte[] read(String key) throws IOException {
    try {
      GetObjectRequest request = GetObjectRequest.builder().bucket(bucket()).key(key).build();
      return client().getObjectAsBytes(request).asByteArray();
    } catch (SdkException e) {
      throw new IOException("Cannot read S3 object " + key, e);
    }
  }

  /** Deletes the object. */
  public void delete(String key) throws IOException {
    try {
      client()
          .deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(key).build());
    } catch (SdkException e) {
      throw new IOException("Cannot delete S3 object " + key, e);
    }
  }

  /**
   * Appends text to the object, creating it when it does not exist yet. S3 objects cannot be
   * appended to, so the current content is read and written back with the text added.
   */
  public synchronized void append(String key, String text) throws IOException {
    try {
      byte[] current;

      try {
        current =
            client()
                .getObjectAsBytes(GetObjectRequest.builder().bucket(bucket()).key(key).build())
                .asByteArray();
      } catch (NoSuchKeyException e) {
        current = new byte[0];
      }

      byte[] added = text.getBytes(StandardCharsets.UTF_8);
      byte[] updated = new byte[current.length + added.length];
      System.arraycopy(current, 0, updated, 0, current.length);
      System.arraycopy(added, 0, updated, current.length, added.length);

      client()
          .putObject(
              PutObjectRequest.builder()
                  .bucket(bucket())
                  .key(key)
                  .contentType("text/csv")
                  .build(),
              RequestBody.fromBytes(updated));
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

  private S3Client client() {
    S3Client client = s3Client;

    if (client == null) {
      synchronized (this) {
        client = s3Client;

        if (client == null) {
          client = S3Client.create();
          s3Client = client;
        }
      }
    }

    return client;
  }

  @PreDestroy
  void close() {
    S3Client client = s3Client;

    if (client != null) {
      client.close();
    }
  }
}
