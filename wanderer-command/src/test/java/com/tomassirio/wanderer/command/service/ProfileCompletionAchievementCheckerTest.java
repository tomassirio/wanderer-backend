package com.tomassirio.wanderer.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.repository.UserRepository;
import com.tomassirio.wanderer.command.service.impl.checker.ProfileCompletionAchievementChecker;
import com.tomassirio.wanderer.commons.domain.AchievementType;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.domain.UserDetails;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileCompletionAchievementCheckerTest {

    @Mock private UserRepository userRepository;
    @Mock private ThumbnailService thumbnailService;

    private ProfileCompletionAchievementChecker checker;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        checker = new ProfileCompletionAchievementChecker(userRepository, thumbnailService);
    }

    @Test
    void getApplicableTypes_shouldReturnProfileCompletedOnly() {
        List<AchievementType> types = checker.getApplicableTypes();

        assertThat(types).containsExactly(AchievementType.PROFILE_COMPLETED);
    }

    @Test
    void computeMetric_whenUserNotFound_shouldReturnZero() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        double result = checker.computeMetric(USER_ID);

        assertThat(result).isEqualTo(0.0);
    }

    @Test
    void computeMetric_whenUserDetailsIsNull_shouldReturnZero() {
        User user = User.builder().id(USER_ID).username("johndoe").build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        double result = checker.computeMetric(USER_ID);

        assertThat(result).isEqualTo(0.0);
    }

    @Test
    void computeMetric_whenOnlyDisplayNameSet_shouldReturnZero() {
        UserDetails details = UserDetails.builder().displayName("John").build();
        User user = User.builder().id(USER_ID).username("johndoe").userDetails(details).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        double result = checker.computeMetric(USER_ID);

        assertThat(result).isEqualTo(0.0);
    }

    @Test
    void computeMetric_whenOnlyBioSet_shouldReturnZero() {
        UserDetails details = UserDetails.builder().bio("Walking the Camino").build();
        User user = User.builder().id(USER_ID).username("johndoe").userDetails(details).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        double result = checker.computeMetric(USER_ID);

        assertThat(result).isEqualTo(0.0);
    }

    @Test
    void computeMetric_whenDisplayNameBioAndPhotoSet_shouldReturnOne() {
        UserDetails details =
                UserDetails.builder().displayName("John").bio("Walking the Camino").build();
        User user = User.builder().id(USER_ID).username("johndoe").userDetails(details).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(thumbnailService.thumbnailExists(USER_ID, ThumbnailEntityType.USER_PROFILE))
                .thenReturn(true);

        double result = checker.computeMetric(USER_ID);

        assertThat(result).isEqualTo(1.0);
    }

    @Test
    void computeMetric_whenDisplayNameAndBioSetButNoPhoto_shouldReturnZero() {
        UserDetails details =
                UserDetails.builder().displayName("John").bio("Walking the Camino").build();
        User user = User.builder().id(USER_ID).username("johndoe").userDetails(details).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(thumbnailService.thumbnailExists(USER_ID, ThumbnailEntityType.USER_PROFILE))
                .thenReturn(false);

        double result = checker.computeMetric(USER_ID);

        assertThat(result).isEqualTo(0.0);
    }

    @Test
    void computeMetric_whenDisplayNameAndBioAreBlank_shouldReturnZero() {
        UserDetails details = UserDetails.builder().displayName("   ").bio("").build();
        User user = User.builder().id(USER_ID).username("johndoe").userDetails(details).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        double result = checker.computeMetric(USER_ID);

        assertThat(result).isEqualTo(0.0);
    }
}
