package com.linkscale.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LinkScaleApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkScaleApplication.class, args);
    }
}

