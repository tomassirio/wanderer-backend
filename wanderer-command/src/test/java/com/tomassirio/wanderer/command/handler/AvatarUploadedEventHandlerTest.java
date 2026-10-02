package com.tomassirio.wanderer.command.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.tomassirio.wanderer.command.event.AvatarUploadedEvent;
import com.tomassirio.wanderer.command.service.AchievementService;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvatarUploadedEventHandlerTest {

    @Mock private ThumbnailService thumbnailService;
    @Mock private AchievementService achievementCalculationService;

    @InjectMocks private AvatarUploadedEventHandler handler;

    private final UUID userId = UUID.randomUUID();
    private final byte[] bytes = {1, 2, 3};

    private AvatarUploadedEvent event() {
        return AvatarUploadedEvent.builder()
                .userId(userId)
                .fileBytes(bytes)
                .contentType("image/png")
                .originalFilename("me.png")
                .build();
    }

    @Test
    void handle_whenSaved_shouldSaveAndCheckSocialAchievements() {
        handler.handle(event());

        verify(thumbnailService).processAndSaveProfilePicture(userId, bytes, "image/png", "me.png");
        verify(achievementCalculationService).checkAndUnlockSocialAchievements(userId);
    }

    @Test
    void handle_whenSaveFails_shouldNotCheckAchievements() {
        doThrow(new IllegalArgumentException("bad image"))
                .when(thumbnailService)
                .processAndSaveProfilePicture(any(), any(), any(), any());

        handler.handle(event());

        verify(achievementCalculationService, never()).checkAndUnlockSocialAchievements(any());
    }
}
