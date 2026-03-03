import io.lettuce.core.RedisBusyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@Slf4j
public class RedisStreamConfig {

    @Value("${app.stream.name:classroom-events}")
    private String streamName;

    @Value("${app.stream.group-prefix:local-group-}")
    private String groupPrefix;

    // ==================== STREAM LISTENER CONTAINER ====================
    // This is the "engine" that polls Redis Streams continuously.
    @Bean(initMethod = "start", destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>>
    streamMessageListenerContainer(RedisConnectionFactory connectionFactory) {

        // Track consecutive errors so we can back off if Redis is unreachable
        AtomicInteger consecutiveErrors = new AtomicInteger(0);

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<
                String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()

                        // ===== FAST POLLING =====
                        // How long XREADGROUP blocks on the server side waiting for new messages.
                        // Lower = faster reaction, higher = less CPU when idle.
                        // 100ms is a great balance; set to Duration.ZERO for absolute minimum latency
                        // (but that will spin-loop and burn CPU when the stream is empty).
                        .pollTimeout(Duration.ofMillis(100))

                        // Batch size — grab up to 50 messages per poll for throughput
                        .batchSize(50)

                        // Use virtual threads (Java 21+) for non-blocking listener execution.
                        // For Java 17, replace with:
                        //   .executor(Executors.newFixedThreadPool(
                        //       Runtime.getRuntime().availableProcessors()))
                        .executor(Executors.newVirtualThreadPerTaskExecutor())

                        // ===== ROBUST ERROR HANDLING =====
                        .errorHandler(t -> {
                            // Suppress the noisy "Connection closed" on shutdown
                            if (t.getMessage() != null
                                    && t.getMessage().contains("Connection closed")) {
                                log.debug("Redis connection closed during stream polling");
                                return;
                            }

                            // Suppress "Maintenance events not supported" (Lettuce on non-cluster
                            // or older Redis that doesn't support CLIENT TRACKING).
                            if (t.getMessage() != null
                                    && t.getMessage().contains("Maintenance events not supported")) {
                                // Log once, then ignore
                                if (consecutiveErrors.getAndIncrement() == 0) {
                                    log.warn("Maintenance events not supported by this Redis — "
                                            + "this message will not repeat");
                                }
                                return;   // <-- STOP the loop of log lines
                            }

                            // For any other error, log and reset counter
                            consecutiveErrors.set(0);
                            log.error("Error in Redis Stream listener", t);
                        })
                        .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }

    // ==================== SUBSCRIPTION ====================
    @Bean
    public Subscription subscription(
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> container,
            StreamListener<String, MapRecord<String, String, String>> streamListener,
            RedisConnectionFactory connectionFactory) {

        String groupName = groupPrefix + getHostName();
        String consumerName = "consumer-" + UUID.randomUUID();

        // Ensure the consumer group (and stream) exist
        createConsumerGroup(connectionFactory, streamName, groupName);

        // Subscribe — receiveAutoAck so acknowledged messages are not reprocessed.
        // Use ReadOffset.lastConsumed() so each consumer resumes where it left off.
        Subscription subscription = container.receiveAutoAck(
                Consumer.from(groupName, consumerName),
                StreamOffset.create(streamName, ReadOffset.lastConsumed()),
                streamListener
        );

        log.info("Redis Stream subscription registered — stream: {}, group: {}, consumer: {}",
                streamName, groupName, consumerName);

        return subscription;
    }

    // ==================== HELPER: CREATE GROUP ====================
    private void createConsumerGroup(RedisConnectionFactory connectionFactory,
                                     String stream, String group) {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        try {
            // Try to create the group; assumes the stream already exists
            template.opsForStream().createGroup(stream, ReadOffset.latest(), group);
            log.info("Created consumer group {} for stream {}", group, stream);

        } catch (RedisSystemException e) {
            if (e.getRootCause() != null
                    && e.getRootCause().getMessage() != null
                    && e.getRootCause().getMessage().contains("BUSYGROUP")) {
                // Group already exists — fine
                log.info("Consumer group {} already exists for stream {}", group, stream);

            } else {
                // Stream probably doesn't exist yet — create it with a seed message,
                // then create the group
                try {
                    template.opsForStream().add(stream,
                            Collections.singletonMap("_init", "true"));
                    template.opsForStream().createGroup(stream, ReadOffset.latest(), group);
                    log.info("Created stream {} and consumer group {}", stream, group);
                } catch (Exception ex) {
                    log.error("Failed to create consumer group for stream {}", stream, ex);
                }
            }
        }
    }

    // ==================== HELPER: HOSTNAME ====================
    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "host-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}