package com.dpi.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
@org.springframework.scheduling.annotation.EnableAsync
public class DpipApplication {
    public static void main(String[] args) {
        SpringApplication.run(DpipApplication.class, args);
    }
}
