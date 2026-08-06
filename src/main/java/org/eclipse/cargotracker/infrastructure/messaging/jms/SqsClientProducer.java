package org.eclipse.cargotracker.infrastructure.messaging.jms;

import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * CDI producer that creates and manages the lifecycle of the AWS SDK v2 {@link SqsClient}.
 *
 * <p>The AWS region is resolved from the environment variable {@code AWS_REGION}. If the variable
 * is not set, the SDK's default region-provider chain (instance metadata, config file, etc.) is
 * used automatically. Credentials are resolved via the SDK's default credential-provider chain
 * (environment variables, IAM role, ~/.aws/credentials, etc.).
 */
@ApplicationScoped
public class SqsClientProducer {

  /** Environment variable for the AWS region override. */
  private static final String AWS_REGION_ENV = "AWS_REGION";

  @Inject
  private Logger logger;

  private SqsClient sqsClient;

  /**
   * Produces a singleton {@link SqsClient} for the application.
   *
   * @return a configured {@link SqsClient} instance
   */
  @Produces
  @ApplicationScoped
  public SqsClient produceSqsClient() {
    String regionStr = System.getenv(AWS_REGION_ENV);
    SqsClient.Builder builder = SqsClient.builder();
    if (regionStr != null && !regionStr.isBlank()) {
      builder.region(Region.of(regionStr));
      logger.log(Level.INFO, "Creating SqsClient for AWS region: {0}", regionStr);
    } else {
      logger.log(Level.INFO,
          "AWS_REGION not set; SqsClient will use the SDK default region-provider chain.");
    }
    sqsClient = builder.build();
    return sqsClient;
  }

  @PreDestroy
  public void close() {
    if (sqsClient != null) {
      try {
        sqsClient.close();
        logger.log(Level.INFO, "SqsClient closed successfully.");
      } catch (Exception e) {
        logger.log(Level.WARNING, "Error closing SqsClient", e);
      }
    }
  }
}
