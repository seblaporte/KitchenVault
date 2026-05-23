package fr.seblaporte.kitchenvault.config;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import fr.seblaporte.kitchenvault.cookidoo.CookidooServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;

@Configuration
public class CookidooClientConfig {

    @Bean
    public CookidooServiceClient cookidooServiceClient(CookidooProperties properties) {
        var snakeCaseConverter = new MappingJackson2HttpMessageConverter(
                JsonMapper.builder()
                        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                        .build()
        );

        // Force HTTP/1.1 : le JdkHttpClient tente par défaut une upgrade h2c (HTTP/2 cleartext)
        // qui perd le body POST quand uvicorn la rejette
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
        );

        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.service().url())
                .messageConverters(c -> {
                    c.removeIf(mc -> mc instanceof MappingJackson2HttpMessageConverter);
                    c.add(0, snakeCaseConverter);
                })
                .build();

        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();

        return factory.createClient(CookidooServiceClient.class);
    }
}
