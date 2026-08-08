package com.example.logistics.lastmile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(excludeName = {
    "org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration",
    "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration",
    "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration"
})
@EnableScheduling
public class LogisticsDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogisticsDemoApplication.class, args);
    }

}
