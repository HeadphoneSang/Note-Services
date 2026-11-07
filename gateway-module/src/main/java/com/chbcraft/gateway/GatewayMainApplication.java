package com.chbcraft.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication
public class GatewayMainApplication {
    public static void main(String[] args) {
        System.out.println("-----------------（竹庄瑞是傻逼）---------------");
        SpringApplication.run(GatewayMainApplication.class,args);
    }

}
