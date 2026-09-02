package org.eclipse.cargotracker.infrastructure.config;

import java.io.Serializable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Redis/ElastiCache configuration for externalizing singleton state.
 *
 * <p>Reads connection settings from environment variables so all EKS pod replicas
 * share a single consistent data store via Amazon ElastiCache (Redis).
 *
 * <p>Required environment variables:
 * <ul>
 *   <li>REDIS_HOST - ElastiCache Redis endpoint (default: localhost)</li>
 *   <li>REDIS_PORT - Redis port (default: 6379)</li>
 *   <li>REDIS_PASSWORD - Redis auth token (optional)</li>
 * </ul>
 */
@ApplicationScoped
public class RedisConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Redis host resolved from environment variable REDIS_HOST (ElastiCache endpoint). */
    @Produces
    public String redisHost() {
        String host = System.getenv("REDIS_HOST");
        return (host != null && !host.isEmpty()) ? host : "localhost";
    }

    /** Redis port resolved from environment variable REDIS_PORT. */
    public int redisPort() {
        String port = System.getenv("REDIS_PORT");
        try {
            return (port != null && !port.isEmpty()) ? Integer.parseInt(port) : 6379;
        } catch (NumberFormatException e) {
            return 6379;
        }
    }

    /** Redis password/auth token resolved from environment variable REDIS_PASSWORD. */
    public String redisPassword() {
        return System.getenv("REDIS_PASSWORD");
    }

    /** Full Redis connection URL for use with Jedis/Lettuce clients. */
    public String redisUrl() {
        String password = redisPassword();
        if (password != null && !password.isEmpty()) {
            return "redis://:" + password + "@" + redisHost() + ":" + redisPort();
        }
        return "redis://" + redisHost() + ":" + redisPort();
    }
}
