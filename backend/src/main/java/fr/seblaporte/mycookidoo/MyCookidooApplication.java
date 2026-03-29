package fr.seblaporte.mycookidoo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
public class MyCookidooApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyCookidooApplication.class, args);
    }
}
