package com.statoverflow.status.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "oci.storage")
@Getter
@Setter
public class StorageProperties {
	private String endpoint;
	private String region;
	private String bucket;
	private String accessKey;
	private String secretKey;
}
