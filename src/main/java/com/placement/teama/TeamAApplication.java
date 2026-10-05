package com.placement.teama;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TeamAApplication {
    public static void main(String[] args) {
        SpringApplication.run(TeamAApplication.class, args);
    }
}