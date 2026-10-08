package com.khedma;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KhedmaApplication {

    public static void main(String[] args) {
        SpringApplication.run(KhedmaApplication.class, args);
    }
}
