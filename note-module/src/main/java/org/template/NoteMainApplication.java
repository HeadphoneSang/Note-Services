package org.template;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@EnableDiscoveryClient
@MapperScan("org.template.mapper")
public class NoteMainApplication {
    public static void main(String[] args) {
        SpringApplication.run(NoteMainApplication.class, args);
    }
}