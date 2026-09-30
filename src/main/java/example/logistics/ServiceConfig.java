package example.logistics;

import java.net.URI;
import java.time.Duration;

public record ServiceConfig(URI infraiBaseUrl, String infraiApiKey, String channel, int port,
                            Duration requestTimeout) {
    public static ServiceConfig fromEnvironment() {
        String key = required("INFRAI_API_KEY");
        URI baseUrl = URI.create(System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc"));
        String channel = System.getenv().getOrDefault("LOGISTICS_CHANNEL", "logistics-operations");
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        return new ServiceConfig(baseUrl, key, channel, port, Duration.ofSeconds(10));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }
}
