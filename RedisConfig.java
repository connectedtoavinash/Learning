import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
// import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.List;

@Configuration
public class RedisConfig {

    // ==================== CLUSTER PROPERTIES ====================
    @Value("${spring.redis.cluster.nodes:127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002}")
    private List<String> clusterNodes;

    @Value("${spring.redis.cluster.max-redirects:3}")
    private int maxRedirects;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    // ==================== STANDALONE PROPERTIES (for reference) ====================
    // @Value("${spring.redis.host:localhost}")
    // private String redisHost;
    //
    // @Value("${spring.redis.port:6379}")
    // private int redisPort;

    // ==================== CONNECTION FACTORY — CLUSTER ====================
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {

        // --- Cluster configuration ---
        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(clusterNodes);
        clusterConfig.setMaxRedirects(maxRedirects);
        if (redisPassword != null && !redisPassword.isBlank()) {
            clusterConfig.setPassword(redisPassword);
        }

        // Periodic + adaptive topology refresh so the driver reacts to node changes
        ClusterTopologyRefreshOptions topologyRefreshOptions =
                ClusterTopologyRefreshOptions.builder()
                        .enablePeriodicRefresh(Duration.ofSeconds(30))    // refresh topology every 30s
                        .enableAllAdaptiveRefreshTriggers()               // react to MOVED / ASK etc.
                        .build();

        // ClusterClientOptions replaces plain ClientOptions for clusters
        ClusterClientOptions clientOptions = ClusterClientOptions.builder()
                .topologyRefreshOptions(topologyRefreshOptions)
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .publishOnScheduler(true) // prevents the "Maintenance events not supported" spam
                .build();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .readFrom(ReadFrom.REPLICA_PREFERRED)  // read from replicas when possible
                .commandTimeout(Duration.ofSeconds(5))
                .build();

        return new LettuceConnectionFactory(clusterConfig, clientConfig);
    }

    // ==================== CONNECTION FACTORY — STANDALONE (commented out) ====================
    /*
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration standaloneConfig =
                new RedisStandaloneConfiguration(redisHost, redisPort);

        // KEY FIX: disable "maintenance events" warning by setting publishOnScheduler
        // and turning off the unsupported CLIENT tracking.
        ClientOptions clientOptions = ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .publishOnScheduler(true)        // <-- suppresses "Maintenance events not supported"
                .build();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(Duration.ofSeconds(5))
                .build();

        return new LettuceConnectionFactory(standaloneConfig, clientConfig);
    }
    */

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}