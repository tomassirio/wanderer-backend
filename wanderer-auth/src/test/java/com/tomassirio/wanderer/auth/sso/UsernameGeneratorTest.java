package com.tomassirio.wanderer.auth.sso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.commons.dto.UserBasicInfo;
import feign.FeignException;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsernameGeneratorTest {

    @Mock private WandererQueryClient wandererQueryClient;
    @InjectMocks private UsernameGenerator generator;

    private final UserBasicInfo taken = new UserBasicInfo(UUID.randomUUID(), "taken");

    @Test
    void baseFrom_lowercasesAndStripsInvalidCharacters() {
        assertEquals("anamariatrips", UsernameGenerator.baseFrom("Ana.Maria+trips@gmail.com"));
    }

    @Test
    void baseFrom_padsShortLocalPart() {
        assertEquals("wandererab", UsernameGenerator.baseFrom("ab@x.com"));
    }

    @Test
    void baseFrom_truncatesLongLocalPart() {
        assertEquals(40, UsernameGenerator.baseFrom("a".repeat(80) + "@x.com").length());
    }

    @Test
    void generate_returnsBaseWhenAvailable() {
        when(wandererQueryClient.getUserByUsername("ana", "basic")).thenReturn(null);

        assertEquals("ana", generator.generate("ana@gmail.com"));
    }

    @Test
    void generate_treats404AsAvailable() {
        when(wandererQueryClient.getUserByUsername("ana", "basic")).thenThrow(notFound());

        assertEquals("ana", generator.generate("ana@gmail.com"));
    }

    @Test
    void generate_appendsSuffixWhenBaseTaken() {
        when(wandererQueryClient.getUserByUsername("ana", "basic")).thenReturn(taken);
        when(wandererQueryClient.getUserByUsername(argThat(u -> u.startsWith("ana_")), eq("basic")))
                .thenReturn(null);

        String username = generator.generate("ana@gmail.com");

        assertTrue(username.matches("ana_\\d{4}"), username);
    }

    @Test
    void generate_whenEverythingTaken_throws() {
        when(wandererQueryClient.getUserByUsername(anyString(), eq("basic"))).thenReturn(taken);

        assertThrows(IllegalStateException.class, () -> generator.generate("ana@gmail.com"));
    }

    @Test
    void generate_whenQueryServiceFails_throws() {
        when(wandererQueryClient.getUserByUsername("ana", "basic")).thenThrow(FeignException.class);

        assertThrows(IllegalStateException.class, () -> generator.generate("ana@gmail.com"));
    }

    private FeignException notFound() {
        Request request =
                Request.create(
                        Request.HttpMethod.GET,
                        "/users/username/ana",
                        Map.of(),
                        null,
                        StandardCharsets.UTF_8,
                        null);
        return new FeignException.NotFound("not found", request, null, null);
    }
}
