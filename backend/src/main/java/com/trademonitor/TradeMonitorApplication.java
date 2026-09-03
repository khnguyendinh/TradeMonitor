package com.trademonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TradeMonitorApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradeMonitorApplication.class, args);
    }
}
