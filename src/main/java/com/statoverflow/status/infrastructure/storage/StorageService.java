package com.statoverflow.status.infrastructure.storage;

import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.statoverflow.status.global.error.ErrorType;
import com.statoverflow.status.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
public class StorageService {

	private final S3Client s3Client;
	private final StorageProperties props;

	public String uploadFile(MultipartFile file, String key) {
		try {
			s3Client.putObject(
				PutObjectRequest.builder()
					.bucket(props.getBucket())
					.key(key)
					.contentType(file.getContentType())
					.contentLength(file.getSize())
					.build(),
				RequestBody.fromBytes(file.getBytes())
			);
		} catch (IOException e) {
			throw new CustomException(ErrorType.UPLOAD_FAILED);
		}

		return props.getEndpoint() + "/" + props.getBucket() + "/" + key;
	}
}
