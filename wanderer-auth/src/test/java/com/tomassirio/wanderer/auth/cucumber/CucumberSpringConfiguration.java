package com.tomassirio.wanderer.auth.cucumber;

import com.tomassirio.wanderer.auth.AuthApplication;
import com.tomassirio.wanderer.auth.client.WandererCommandClient;
import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.commons.cucumber.BaseCucumberSpringConfiguration;
import com.tomassirio.wanderer.commons.dto.UserBasicInfo;
import io.cucumber.spring.CucumberContextConfiguration;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.UUID;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@CucumberContextConfiguration
@SpringBootTest(
        classes = AuthApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfiguration extends BaseCucumberSpringConfiguration {

    @MockitoBean private WandererCommandClient wandererCommandClient;

    @MockitoBean private WandererQueryClient wandererQueryClient;

    // Configure mocks
    @PostConstruct
    public void setupMocks() {
        UUID userId = UUID.randomUUID();
        UserBasicInfo dummyUserInfo = new UserBasicInfo(userId, "testuser");

        Mockito.when(wandererCommandClient.createUser(Mockito.any(Map.class))).thenReturn(userId);
        Mockito.when(wandererQueryClient.getUserById(userId, "basic")).thenReturn(dummyUserInfo);
        Mockito.when(wandererQueryClient.getUserByUsername("testuser", "basic"))
                .thenReturn(dummyUserInfo);
        Mockito.when(wandererQueryClient.getUserByUsername("nonexistent", "basic"))
                .thenReturn(null);
    }
}
