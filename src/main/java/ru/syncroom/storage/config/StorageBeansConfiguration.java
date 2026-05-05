package ru.syncroom.storage.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.syncroom.games.service.GarticDrawingAssetStorage;
import ru.syncroom.games.service.LocalGarticDrawingAssetStorage;
import ru.syncroom.games.service.S3GarticDrawingAssetStorage;
import ru.syncroom.users.service.AvatarStorage;
import ru.syncroom.users.service.LocalAvatarStorage;
import ru.syncroom.users.service.S3AvatarStorage;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(SyncRoomStorageProperties.class)
public class StorageBeansConfiguration {

    @Bean
    @ConditionalOnProperty(name = "syncroom.storage.type", havingValue = "s3")
    public S3Client syncRoomS3Client(SyncRoomStorageProperties props) {
        SyncRoomStorageProperties.S3 s3 = props.getS3();
        return S3Client.builder()
                .endpointOverride(URI.create(s3.getEndpoint()))
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "syncroom.storage.type", havingValue = "local", matchIfMissing = true)
    public GarticDrawingAssetStorage localGarticDrawingAssetStorage(SyncRoomStorageProperties props) {
        String garticDir = props.getLocal().getDataDir() + "/gartic";
        return new LocalGarticDrawingAssetStorage(garticDir, props.normalizedPublicBaseUrl());
    }

    @Bean
    @ConditionalOnProperty(name = "syncroom.storage.type", havingValue = "s3")
    public GarticDrawingAssetStorage s3GarticDrawingAssetStorage(
            S3Client s3Client,
            SyncRoomStorageProperties props
    ) {
        return new S3GarticDrawingAssetStorage(s3Client, props);
    }

    @Bean
    @ConditionalOnProperty(name = "syncroom.storage.type", havingValue = "local", matchIfMissing = true)
    public AvatarStorage localAvatarStorage(SyncRoomStorageProperties props) {
        return new LocalAvatarStorage(props.getLocal().getDataDir() + "/avatars", props.normalizedPublicBaseUrl());
    }

    @Bean
    @ConditionalOnProperty(name = "syncroom.storage.type", havingValue = "s3")
    public AvatarStorage s3AvatarStorage(S3Client s3Client, SyncRoomStorageProperties props) {
        return new S3AvatarStorage(s3Client, props);
    }
}
