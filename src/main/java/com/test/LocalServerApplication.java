package com.test;

import org.apache.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LocalServerApplication {

    private static final Logger logger = Logger.getLogger(LocalServerApplication.class);

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(LocalServerApplication.class, args);

        for (int i = 0; i < 100000; i++) {
            Thread.sleep(10000);
            logger.info("Test log message " + i);
        }
    }
}
