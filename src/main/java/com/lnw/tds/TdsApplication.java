package com.lnw.tds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TdsApplication {

    public static void main(String[] args) {
        SpringApplication.run(TdsApplication.class, args);
    }
}
