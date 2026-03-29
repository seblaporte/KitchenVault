package fr.seblaporte.mycookidoo.config;

import fr.seblaporte.mycookidoo.cookidoo.CookidooServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class CookidooClientConfig {

    @Bean
    public CookidooServiceClient cookidooServiceClient(CookidooProperties properties) {
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.service().url())
                .build();

        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();

        return factory.createClient(CookidooServiceClient.class);
    }
}
