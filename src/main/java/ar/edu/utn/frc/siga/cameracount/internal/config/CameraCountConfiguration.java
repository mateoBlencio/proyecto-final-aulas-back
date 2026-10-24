package ar.edu.utn.frc.siga.cameracount.internal.config;

import ar.edu.utn.frc.siga.cameracount.internal.client.HttpCameraCountClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(CameraCountProperties.class)
@ConditionalOnProperty(prefix = "siga.camera-count", name = "enabled", havingValue = "true")
public class CameraCountConfiguration {

    private static final String API_KEY_HEADER = "X-API-Key";

    // El RestClient no se publica como bean para no competir por tipo con sysacadRestClient.
    @Bean
    public HttpCameraCountClient httpCameraCountClient(CameraCountProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());

        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getUrl())
                .defaultHeader(API_KEY_HEADER, properties.getApiKey())
                .requestFactory(requestFactory)
                .build();
        return new HttpCameraCountClient(restClient);
    }
}
