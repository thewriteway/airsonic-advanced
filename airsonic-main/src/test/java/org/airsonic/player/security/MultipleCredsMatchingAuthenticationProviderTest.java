package org.airsonic.player.security;

import org.airsonic.player.controller.SubsonicRESTController.APIException;
import org.airsonic.player.domain.UserCredential;
import org.airsonic.player.repository.UserCredentialRepository;
import org.airsonic.player.service.SecurityService.UserDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultipleCredsMatchingAuthenticationProviderTest {

    @Mock
    private UserCredentialRepository userCredentialRepository;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserDetail userDetail;
    @Mock
    private UserCredential cred;
    @Mock
    private UserCredential cred2;


    @InjectMocks
    private MultipleCredsMatchingAuthenticationProvider provider;

    @Captor
    private ArgumentCaptor<UserCredential> userCredentialCaptor;

    @BeforeEach
    public void setup() {
        provider = new MultipleCredsMatchingAuthenticationProvider(userDetailsService, passwordEncoder, userCredentialRepository);
    }


    @Test
    void shouldThrowBadCredentialsExceptionWhenNoCredentialsProvided() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("user", null);

        assertThrows(BadCredentialsException.class, () -> provider.additionalAuthenticationChecks(userDetail, authentication));
    }


    @Test
    void shouldThrowBadCredentialsExceptionWhenNoMatchingCredential() {
        when(userDetail.getCredentials()).thenReturn(Collections.emptyList());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("user", "pass");

        assertThrows(BadCredentialsException.class, () -> provider.additionalAuthenticationChecks(userDetail, authentication));
    }

    @Test
    void shouldThrowBadCredentialsExceptionWithApiExceptionForSaltToken() {
        when(userDetail.getCredentials()).thenReturn(Collections.emptyList());
        UsernameSaltedTokenAuthenticationToken authentication = new UsernameSaltedTokenAuthenticationToken("user", "pass", null);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> provider.additionalAuthenticationChecks(userDetail, authentication));

        assertTrue(ex.getCause() instanceof APIException);
    }

    @Test
    void shouldThrowCredentialsExpiredExceptionWhenCredentialExpired() {
        UserCredential credential = new UserCredential();
        credential.setEncoder("bcrypt");
        credential.setCredential("encoded");
        credential.setExpiration(Instant.now().minusSeconds(100));
        when(userDetail.getCredentials()).thenReturn(Collections.singletonList(credential));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("user", "pass");

        when(passwordEncoder.matches(eq("pass"), anyString())).thenReturn(true);

        assertThrows(CredentialsExpiredException.class, () -> provider.additionalAuthenticationChecks(userDetail, authentication));
    }

    @Test
    void shouldUpgradeEncodingWhenNeeded() {
        when(cred.getEncoder()).thenReturn("bcrypt");
        when(cred.getCredential()).thenReturn("encoded");
        when(cred.getExpiration()).thenReturn(null);
        when(cred.getComment()).thenReturn("old");
        when(cred.updateEncoder(anyString(), eq(true))).thenReturn(true);

        when(userDetail.getCredentials()).thenReturn(Collections.singletonList(cred));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("user", "pass");

        when(passwordEncoder.matches(eq("pass"), anyString())).thenReturn(true);
        when(passwordEncoder.upgradeEncoding(anyString())).thenReturn(true);

        provider.additionalAuthenticationChecks(userDetail, authentication);

        verify(cred).setComment(contains("Automatically upgraded by system"));
        verify(userCredentialRepository).save(cred);
    }

    @Test
    void shouldNotUpgradeEncodingIfNotNeeded() {
        when(cred.getEncoder()).thenReturn("bcrypt");
        when(cred.getCredential()).thenReturn("encoded");
        when(cred.getExpiration()).thenReturn(null);

        when(userDetail.getCredentials()).thenReturn(Collections.singletonList(cred));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("user", "pass");

        when(passwordEncoder.matches(eq("pass"), anyString())).thenReturn(true);
        when(passwordEncoder.upgradeEncoding(anyString())).thenReturn(false);

        provider.additionalAuthenticationChecks(userDetail, authentication);

        verify(userCredentialRepository, never()).save(any());
    }

    @Test
    void shouldAuthenticateWithMultipleCredentials() {
        when(cred.getEncoder()).thenReturn("bcrypt");
        when(cred.getCredential()).thenReturn("encoded1");

        when(cred2.getEncoder()).thenReturn("pbkdf2");
        when(cred2.getCredential()).thenReturn("encoded2");
        when(cred2.getExpiration()).thenReturn(null);

        when(userDetail.getCredentials()).thenReturn(Arrays.asList(cred, cred2));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("user", "pass");

        // Only second credential matches
        when(passwordEncoder.matches(eq("pass"), eq("{bcrypt}encoded1"))).thenReturn(false);
        when(passwordEncoder.matches(eq("pass"), eq("{pbkdf2}encoded2"))).thenReturn(true);
        when(passwordEncoder.upgradeEncoding(anyString())).thenReturn(false);

        provider.additionalAuthenticationChecks(userDetail, authentication);

        verify(userCredentialRepository, never()).save(any());
    }
}