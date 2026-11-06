package com.chbcraft.deploy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.chbcraft.deploy.mapper")
public class DeployApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeployApplication.class, args);
    }

}
