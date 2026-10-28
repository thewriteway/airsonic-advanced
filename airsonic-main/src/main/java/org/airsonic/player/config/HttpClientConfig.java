package org.airsonic.player.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared HTTP client for outbound requests (podcasts, cover art, lyrics, scrobbling, Sonos, version checks).
 * Callers set a per-request response timeout where they need something other than the default.
 */
@Configuration
public class HttpClientConfig {

    private static final Timeout CONNECT_TIMEOUT = Timeout.ofSeconds(30);
    private static final Timeout SOCKET_TIMEOUT = Timeout.ofSeconds(60);
    private static final Timeout CONNECTION_REQUEST_TIMEOUT = Timeout.ofSeconds(30);

    @Bean
    public CloseableHttpClient httpClient() {
        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(CONNECT_TIMEOUT)
                                .setSocketTimeout(SOCKET_TIMEOUT)
                                .build())
                        .setMaxConnTotal(50)
                        .setMaxConnPerRoute(10)
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT)
                        .build())
                .evictExpiredConnections()
                .build();
    }

    /**
     * Request config with the given response (socket read) timeout.
     */
    public static RequestConfig responseTimeout(Timeout timeout) {
        return RequestConfig.custom()
                .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT)
                .setResponseTimeout(timeout)
                .build();
    }
}
