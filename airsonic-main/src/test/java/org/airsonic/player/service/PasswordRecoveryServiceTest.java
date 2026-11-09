package org.airsonic.player.service;

import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserCredential;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PasswordRecoveryServiceTest {
    private final SettingsService settings = mock(SettingsService.class);
    private final SecurityService security = mock(SecurityService.class);
    private final PasswordRecoveryService recovery = spy(new PasswordRecoveryService(settings, security));

    @Test public void emptyInputDoesNotLookUpOrModifyUsers() {
        assertTrue(recovery.recover("  ", null).isEmpty());
        verifyNoInteractions(security);
    }

    @Test public void preservesLookupAndValidationErrorsWithoutChangingCredentials() {
        assertEquals("recover.error.usernotfound", recovery.recover("missing", null).get("error"));
        when(security.getUserByName("noemail")).thenReturn(new User("noemail", null));
        assertEquals("recover.error.noemail", recovery.recover("noemail", null).get("error"));
        verify(security, never()).updateUser(any());
        verify(security, never()).createCredential(any());
    }

    @Test public void captchaIsRequiredWhenEnabledAndSecretIsNotReturned() {
        when(settings.isCaptchaEnabled()).thenReturn(true);
        when(settings.getRecaptchaSiteKey()).thenReturn("public-key");
        when(settings.getRecaptchaSecretKey()).thenReturn("secret-key");
        assertEquals(java.util.Map.of("recaptchaSiteKey", "public-key"), recovery.configuration());
        assertEquals("recover.error.invalidcaptcha", recovery.recover("unknown", null).get("error"));
        doReturn(false).when(recovery).verifyCaptcha("invalid");
        assertEquals("recover.error.invalidcaptcha", recovery.recover("unknown", "invalid").get("error"));
        doReturn(true).when(recovery).verifyCaptcha("valid");
        assertEquals("recover.error.usernotfound", recovery.recover("unknown", "valid").get("error"));
        verify(security, never()).createCredential(any());
    }

    @Test public void emailFailureLeavesLdapAndCredentialsUnchanged() {
        User user = new User("member", "member@example.test");
        user.setLdapAuthenticated(true);
        when(security.getUserByName("member")).thenReturn(user);
        doReturn(false).when(recovery).emailPassword(anyString(), anyString(), anyString());
        assertEquals("recover.error.sendfailed", recovery.recover("member", null).get("error"));
        assertTrue(user.isLdapAuthenticated());
        verify(security, never()).updateUser(any());
        verify(security, never()).createCredential(any());
    }

    @Test public void recoveryByEmailAddsRandomAirsonicCredentialAfterSendingAndDisablesLdap() {
        User user = new User("member", "member@example.test");
        user.setLdapAuthenticated(true);
        when(security.getUserByEmail(user.getEmail())).thenReturn(user);
        when(security.getPreferredPasswordEncoder(true)).thenReturn("encoder");
        doReturn(true).when(recovery).emailPassword(anyString(), anyString(), anyString());
        assertEquals(user.getEmail(), recovery.recover("  member@example.test  ", null).get("sentTo"));
        assertFalse(user.isLdapAuthenticated());
        ArgumentCaptor<UserCredential> credentials = ArgumentCaptor.forClass(UserCredential.class);
        var order = inOrder(recovery, security);
        order.verify(recovery).emailPassword(matches("[a-zA-Z0-9]{32}"), eq("member"), eq(user.getEmail()));
        order.verify(security).updateUser(user);
        order.verify(security).createCredential(credentials.capture());
        UserCredential credential = credentials.getValue();
        assertEquals(UserCredential.App.AIRSONIC, credential.getApp());
        assertEquals("member", credential.getUsername());
        assertEquals("member", credential.getAppUsername());
        assertEquals("encoder", credential.getEncoder());
        assertTrue(credential.getCredential().matches("[a-zA-Z0-9]{32}"));
    }
}
