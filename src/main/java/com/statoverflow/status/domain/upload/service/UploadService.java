package com.statoverflow.status.domain.upload.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.statoverflow.status.global.error.ErrorType;
import com.statoverflow.status.global.exception.CustomException;
import com.statoverflow.status.infrastructure.storage.StorageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UploadService {

	private static final int MAX_IMAGE_COUNT = 5;
	private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10MB

	private final StorageService storageService;

	public List<String> uploadImages(List<MultipartFile> files) {
		if (files.size() > MAX_IMAGE_COUNT) {
			throw new CustomException(ErrorType.INVALID_FILE_COUNT);
		}

		return files.stream()
			.map(this::uploadSingleImage)
			.toList();
	}

	private String uploadSingleImage(MultipartFile file) {
		String contentType = file.getContentType();
		if (contentType == null || !contentType.startsWith("image/")) {
			throw new CustomException(ErrorType.INVALID_FILE_TYPE);
		}
		if (file.getSize() > MAX_FILE_SIZE) {
			throw new CustomException(ErrorType.UPLOAD_FAILED);
		}

		String key = "uploads/quests/" + UUID.randomUUID() + ".jpg";
		return storageService.uploadFile(file, key);
	}
}
