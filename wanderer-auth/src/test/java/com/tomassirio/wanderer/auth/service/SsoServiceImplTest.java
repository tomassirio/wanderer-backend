package com.tomassirio.wanderer.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.domain.Credential;
import com.tomassirio.wanderer.auth.domain.UserIdentity;
import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.repository.CredentialRepository;
import com.tomassirio.wanderer.auth.repository.UserIdentityRepository;
import com.tomassirio.wanderer.auth.service.impl.SsoServiceImpl;
import com.tomassirio.wanderer.auth.sso.ExternalIdentity;
import com.tomassirio.wanderer.auth.sso.UsernameGenerator;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.dto.UserBasicInfo;
import com.tomassirio.wanderer.commons.security.Role;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SsoServiceImplTest {

    @Mock private UserIdentityRepository userIdentityRepository;
    @Mock private CredentialRepository credentialRepository;
    @Mock private UserProvisioningService userProvisioningService;
    @Mock private UsernameGenerator usernameGenerator;
    @Mock private WandererQueryClient wandererQueryClient;
    @Mock private TokenService tokenService;
    @InjectMocks private SsoServiceImpl ssoService;

    private final UUID userId = UUID.randomUUID();
    private final ExternalIdentity verified =
            new ExternalIdentity("google", "sub-1", "ana@gmail.com", true, "Ana Maria");
    private final LoginResponse tokens =
            new LoginResponse("access", "refresh", "Bearer", 900000L, "ana");

    @Test
    void signIn_whenIdentityLinked_issuesTokensWithStoredRoles() {
        Credential credential = Credential.ssoOnly(userId, "ana@gmail.com");
        credential.setRoles(Set.of(Role.ADMIN, Role.USER));
        when(userIdentityRepository.findByProviderAndSubject("google", "sub-1"))
                .thenReturn(Optional.of(link()));
        when(credentialRepository.findById(userId)).thenReturn(Optional.of(credential));
        when(wandererQueryClient.getUserById(userId, "basic"))
                .thenReturn(new UserBasicInfo(userId, "ana"));
        when(tokenService.issueLoginTokens(any(User.class), eq(Set.of(Role.ADMIN, Role.USER))))
                .thenReturn(tokens);

        assertSame(tokens, ssoService.signIn(verified));
        verify(userIdentityRepository, never()).save(any());
        verify(userProvisioningService, never()).provisionWithoutPassword(any(), any(), any());
    }

    @Test
    void signIn_whenEmailMatchesExistingAccount_linksIdentity() {
        Credential existing = Credential.withPassword(userId, "ana@gmail.com", "$2a$hash");
        when(userIdentityRepository.findByProviderAndSubject("google", "sub-1"))
                .thenReturn(Optional.empty());
        when(credentialRepository.findByEmailIgnoreCase("ana@gmail.com"))
                .thenReturn(Optional.of(existing));
        when(wandererQueryClient.getUserById(userId, "basic"))
                .thenReturn(new UserBasicInfo(userId, "ana"));
        when(tokenService.issueLoginTokens(any(User.class), eq(Set.of(Role.USER))))
                .thenReturn(tokens);

        ssoService.signIn(verified);

        ArgumentCaptor<UserIdentity> saved = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userIdentityRepository).save(saved.capture());
        assertEquals(userId, saved.getValue().getUserId());
        assertEquals("google", saved.getValue().getProvider());
        assertEquals("sub-1", saved.getValue().getSubject());
        verify(userProvisioningService, never()).provisionWithoutPassword(any(), any(), any());
    }

    @Test
    void signIn_whenExistingAccountEmailDiffersInCase_linksInsteadOfCreating() {
        Credential existing = Credential.withPassword(userId, "Ana@Gmail.com", "$2a$hash");
        when(userIdentityRepository.findByProviderAndSubject("google", "sub-1"))
                .thenReturn(Optional.empty());
        when(credentialRepository.findByEmailIgnoreCase("ana@gmail.com"))
                .thenReturn(Optional.of(existing));
        when(wandererQueryClient.getUserById(userId, "basic"))
                .thenReturn(new UserBasicInfo(userId, "ana"));
        when(tokenService.issueLoginTokens(any(User.class), eq(Set.of(Role.USER))))
                .thenReturn(tokens);

        assertSame(tokens, ssoService.signIn(verified));

        ArgumentCaptor<UserIdentity> saved = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userIdentityRepository).save(saved.capture());
        assertEquals(userId, saved.getValue().getUserId());
        verify(credentialRepository, never()).findByEmail(any());
        verify(userProvisioningService, never()).provisionWithoutPassword(any(), any(), any());
    }

    @Test
    void signIn_whenEmailNotVerified_rejectsWithoutLinkingOrCreating() {
        ExternalIdentity unverified =
                new ExternalIdentity("google", "sub-1", "ana@gmail.com", false, "Ana");
        when(userIdentityRepository.findByProviderAndSubject("google", "sub-1"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> ssoService.signIn(unverified));
        verify(credentialRepository, never()).findByEmailIgnoreCase(any());
        verify(userIdentityRepository, never()).save(any());
        verify(userProvisioningService, never()).provisionWithoutPassword(any(), any(), any());
    }

    @Test
    void signIn_whenNewEmail_provisionsWithoutPasswordAndLinks() {
        User created = new User();
        created.setId(userId);
        created.setUsername("ana");
        when(userIdentityRepository.findByProviderAndSubject("google", "sub-1"))
                .thenReturn(Optional.empty());
        when(credentialRepository.findByEmailIgnoreCase("ana@gmail.com"))
                .thenReturn(Optional.empty());
        when(usernameGenerator.generate("ana@gmail.com")).thenReturn("ana");
        when(userProvisioningService.provisionWithoutPassword("ana", "ana@gmail.com", "Ana Maria"))
                .thenReturn(created);
        when(credentialRepository.findById(userId))
                .thenReturn(Optional.of(Credential.ssoOnly(userId, "ana@gmail.com")));
        when(wandererQueryClient.getUserById(userId, "basic"))
                .thenReturn(new UserBasicInfo(userId, "ana"));
        when(tokenService.issueLoginTokens(any(User.class), eq(Set.of(Role.USER))))
                .thenReturn(tokens);

        assertSame(tokens, ssoService.signIn(verified));
        verify(userIdentityRepository).save(any(UserIdentity.class));
        verify(userProvisioningService, never()).provisionWithPassword(any(), any(), any(), any());
    }

    @Test
    void signIn_whenProviderHasNoName_usesUsernameAsDisplayName() {
        ExternalIdentity noName =
                new ExternalIdentity("google", "sub-1", "ana@gmail.com", true, " ");
        User created = new User();
        created.setId(userId);
        created.setUsername("ana");
        when(userIdentityRepository.findByProviderAndSubject("google", "sub-1"))
                .thenReturn(Optional.empty());
        when(credentialRepository.findByEmailIgnoreCase("ana@gmail.com"))
                .thenReturn(Optional.empty());
        when(usernameGenerator.generate("ana@gmail.com")).thenReturn("ana");
        when(userProvisioningService.provisionWithoutPassword("ana", "ana@gmail.com", "ana"))
                .thenReturn(created);
        when(credentialRepository.findById(userId))
                .thenReturn(Optional.of(Credential.ssoOnly(userId, "ana@gmail.com")));
        when(wandererQueryClient.getUserById(userId, "basic"))
                .thenReturn(new UserBasicInfo(userId, "ana"));
        when(tokenService.issueLoginTokens(any(User.class), any())).thenReturn(tokens);

        ssoService.signIn(noName);

        verify(userProvisioningService).provisionWithoutPassword("ana", "ana@gmail.com", "ana");
    }

    @Test
    void signIn_whenAccountDisabled_rejects() {
        Credential disabled = Credential.ssoOnly(userId, "ana@gmail.com");
        disabled.setEnabled(false);
        when(userIdentityRepository.findByProviderAndSubject("google", "sub-1"))
                .thenReturn(Optional.of(link()));
        when(credentialRepository.findById(userId)).thenReturn(Optional.of(disabled));

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> ssoService.signIn(verified));
        assertEquals("Account disabled", ex.getMessage());
        verify(tokenService, never()).issueLoginTokens(any(), any());
    }

    private UserIdentity link() {
        return UserIdentity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .provider("google")
                .subject("sub-1")
                .email("ana@gmail.com")
                .build();
    }
}
