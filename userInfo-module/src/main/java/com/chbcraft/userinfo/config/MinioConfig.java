package com.chbcraft.userinfo.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Value("${minio.endpoint}")
    public String endpoint;
    @Value("${minio.access-key}")
    public String accessKey;

    @Value("${minio.secret-key}")
    public String secretKey;
    @Value("${minio.bucket}")
    public String bucketName;


    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }



}
