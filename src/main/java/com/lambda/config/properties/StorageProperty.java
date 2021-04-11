package com.lambda.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

@Data
@RefreshScope
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageProperty {

    enum StorageType {
        LOCAL, CLOUDINARY, FIREBASE
    }


    private StorageType storageType;

    private String temp;
}
