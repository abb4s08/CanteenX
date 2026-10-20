package edu.canteenx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * CanteenX Web Application entry point.
 * Serves the modern digital token platform at http://localhost:8080.
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class CanteenXWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(CanteenXWebApplication.class, args);
    }
}
