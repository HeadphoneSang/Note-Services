package test.chbcraft.userinfo;


import com.chbcraft.userinfo.Main;
import com.chbcraft.userinfo.service.MinioService;
import io.minio.*;
import io.minio.messages.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.io.File;

@Slf4j
@SpringBootTest(classes = Main.class)
public class UserIcon {

    private final MinioService minioService;

    private final MinioClient client;

    @Autowired
    public UserIcon(MinioService service, MinioClient client){
        this.minioService = service;
        this.client = client;
    }

    @Test
    public void test() throws Exception {
        System.out.println(minioService.getDownloadUrl("性别-未知.png").toString());
        client.listBuckets().forEach(bucket -> {
            System.out.println(bucket.name());
        });
        client.makeBucket(MakeBucketArgs.builder().bucket("test").region("us-east-1").build());
    }


    public void createBucket(String bucketName) throws Exception {
        client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).region("us-east-1").build());
    }


    @Test
    public void testCreateBucket() throws Exception {
        String bucketName = "test-bucket";
        if(!client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())){
            log.info("Bucket does not exist, creating it...");
            createBucket(bucketName);
        }
        log.info("Listing all buckets:");
        for(Bucket bucket : client.listBuckets()){
            log.info(bucket.name());
        }
        log.info("Bucket listing completed.");
        log.info("Deleting the bucket...");
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
        log.info("Bucket deletion completed.");
        log.info("Listing all buckets:");
        for(Bucket bucket : client.listBuckets()){
            log.info(bucket.name());
        }
    }

    @Test
    public void simplyUploadFileTest() throws Exception {
        String bucketName = "test-bucket";
        if(!client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())){
            log.info("Bucket does not exist, creating it...");
            createBucket(bucketName);
        }
        File file = new File(this.getClass().getClassLoader().getResource("Nitro_Wallpaper_02_3840x2400.jpg").getFile());

        ObjectWriteResponse response = client.uploadObject(UploadObjectArgs.builder().bucket("test-bucket").object("/imgs/test-object.jpg").filename(file.getAbsolutePath()).build());
        log.info("Upload completed. " + response.toString());
    }
}
