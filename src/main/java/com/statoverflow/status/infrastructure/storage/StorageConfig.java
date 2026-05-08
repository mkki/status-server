package com.statoverflow.status.infrastructure.storage;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

	@Bean
	public S3Client s3Client(StorageProperties props) {
		return S3Client.builder()
			.endpointOverride(URI.create(props.getEndpoint()))
			.region(Region.of(props.getRegion()))
			.credentialsProvider(
				StaticCredentialsProvider.create(
					AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())
				)
			)
			.forcePathStyle(true)
			.build();
	}
}
