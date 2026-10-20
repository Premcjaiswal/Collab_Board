package com.collabboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// Authentication is handled by AuthService; Boot's default in-memory user (with a generated password) is not wanted.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class CollabBoardApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollabBoardApplication.class, args);
    }
}
