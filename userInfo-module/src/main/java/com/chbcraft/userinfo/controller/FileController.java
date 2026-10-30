package com.chbcraft.userinfo.controller;

import com.chbcraft.userinfo.config.MinioConfig;
import com.chcbcraft.dto.vo.Result;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController()
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private MinioConfig minioConfig;

    private final MinioClient client;

    @Autowired
    public FileController(MinioClient client){
        this.client = client;
    }


    @PutMapping("/file")
    public Result<String> uploadFile(MultipartFile file) throws Exception{
        if (file==null || file.isEmpty()){
            return Result.error("文件为空");
        }else{
            String originalFilename = file.getOriginalFilename();
            String objectName = UUID.randomUUID()+"_"+originalFilename;
            ObjectWriteResponse response = client.putObject(PutObjectArgs.builder().bucket(minioConfig.bucketName).contentType(file.getContentType()).object(objectName).stream(file.getInputStream(), file.getSize(), -1).build());
            return Result.success(minioConfig.endpoint + "/" + minioConfig.bucketName + "/" + objectName);
        }
    }
}
