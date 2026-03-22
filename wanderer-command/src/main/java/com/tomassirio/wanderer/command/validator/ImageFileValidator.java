package com.tomassirio.wanderer.command.validator;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validator for uploaded image files.
 * Validates file format, size, and content type for profile pictures and other image uploads.
 *
 * @author tomassirio
 * @since 0.10.5
 */
@Component
public class ImageFileValidator {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );
    private static final List<String> ALLOWED_EXTENSIONS = List.of(
            ".jpg",
            ".jpeg",
            ".png",
            ".webp"
    );

    /**
     * Validates an uploaded image file.
     *
     * @param file the multipart file to validate
     * @throws IllegalArgumentException if validation fails
     */
    public void validate(MultipartFile file) {
        validateNotEmpty(file);
        validateFileSize(file);
        validateFileType(file);
    }

    private void validateNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
    }

    private void validateFileSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    String.format("File size (%d bytes) exceeds maximum allowed size of 5MB",
                            file.getSize()));
        }
    }

    private void validateFileType(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        boolean validContentType = contentType != null &&
                ALLOWED_CONTENT_TYPES.contains(contentType);

        boolean validExtension = filename != null &&
                ALLOWED_EXTENSIONS.stream()
                        .anyMatch(ext -> filename.toLowerCase().endsWith(ext));

        if (!validContentType && !validExtension) {
            throw new IllegalArgumentException(
                    String.format(
                            "Invalid file type. Only JPEG, PNG, and WebP images are allowed. " +
                            "Received: contentType=%s, filename=%s",
                            contentType, filename));
        }
    }
}
