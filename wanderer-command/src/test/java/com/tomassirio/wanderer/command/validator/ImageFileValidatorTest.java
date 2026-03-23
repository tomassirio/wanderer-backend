package com.tomassirio.wanderer.command.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

class ImageFileValidatorTest {

    private ImageFileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ImageFileValidator();
    }

    @Test
    void validate_shouldAcceptValidJpegFile() {
        MultipartFile file = mockFile("test.jpg", "image/jpeg", 1024 * 1024);
        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void validate_shouldAcceptValidPngFile() {
        MultipartFile file = mockFile("test.png", "image/png", 1024 * 1024);
        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void validate_shouldAcceptValidWebpFile() {
        MultipartFile file = mockFile("test.webp", "image/webp", 1024 * 1024);
        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void validate_shouldAcceptFileWithValidExtensionButUnknownContentType() {
        MultipartFile file = mockFile("test.jpg", "application/octet-stream", 1024 * 1024);
        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void validate_shouldRejectNullFile() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be empty");
    }

    @Test
    void validate_shouldRejectEmptyFile() {
        MultipartFile file = mockFile("test.jpg", "image/jpeg", 0);
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be empty");
    }

    @Test
    void validate_shouldRejectFileExceeding5MB() {
        long size = 6 * 1024 * 1024; // 6MB
        MultipartFile file = mockFile("test.jpg", "image/jpeg", size);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum allowed size of 5MB");
    }

    @Test
    void validate_shouldRejectInvalidFileType() {
        MultipartFile file = mockFile("test.txt", "text/plain", 1024);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only JPEG, PNG, and WebP images are allowed");
    }

    @Test
    void validate_shouldRejectGifFile() {
        MultipartFile file = mockFile("test.gif", "image/gif", 1024 * 1024);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only JPEG, PNG, and WebP images are allowed");
    }

    @Test
    void validate_shouldAcceptExactly5MB() {
        long size = 5 * 1024 * 1024; // Exactly 5MB
        MultipartFile file = mockFile("test.jpg", "image/jpeg", size);
        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    private MultipartFile mockFile(String filename, String contentType, long size) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getContentType()).thenReturn(contentType);
        when(file.getSize()).thenReturn(size);
        when(file.isEmpty()).thenReturn(size == 0);
        return file;
    }
}
