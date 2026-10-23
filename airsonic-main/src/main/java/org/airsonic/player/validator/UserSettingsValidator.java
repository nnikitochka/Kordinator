package org.airsonic.player.validator;

import org.airsonic.player.command.UserSettingsCommand;
import org.airsonic.player.controller.UserSettingsController;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.apache.commons.lang.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Validator for {@link UserSettingsController}.
 *
 * @author Sindre Mehus
 */
public class UserSettingsValidator implements Validator {

    private SecurityService securityService;
    private SettingsService settingsService;
    private HttpServletRequest request;

    public UserSettingsValidator(SecurityService securityService, SettingsService settingsService, HttpServletRequest request) {
        this.securityService = securityService;
        this.settingsService = settingsService;
        this.request = request;
    }

    /**
     * {@inheritDoc}
     */
    public boolean supports(Class clazz) {
        return clazz.equals(UserSettingsCommand.class);
    }

    /**
     * {@inheritDoc}
     */
    public void validate(Object obj, Errors errors) {
        UserSettingsCommand command = (UserSettingsCommand) obj;
        String username = command.getUsername();
        String email = StringUtils.trimToNull(command.getEmail());
        String password = StringUtils.trimToNull(command.getPassword());
        String confirmPassword = command.getConfirmPassword();

        if (command.isNewUser()) {
            if (username == null || username.isEmpty()) {
                errors.rejectValue("username", "usersettings.nousername");
            } else if (securityService.getUserByName(username) != null) {
                errors.rejectValue("username", "usersettings.useralreadyexists");
            } else if (email == null) {
                errors.rejectValue("email", "usersettings.noemail");
            } else if (command.isLdapAuthenticated() && !settingsService.isLdapEnabled()) {
                errors.rejectValue("password", "usersettings.ldapdisabled");
            } else if (command.isLdapAuthenticated() && password != null) {
                errors.rejectValue("password", "usersettings.passwordnotsupportedforldap");
            }
        }

        if ((command.isNewUser() || command.isPasswordChange()) && !command.isLdapAuthenticated()) {
            if (password == null) {
                errors.rejectValue("password", "usersettings.nopassword");
            } else if (!password.equals(confirmPassword)) {
                errors.rejectValue("password", "usersettings.wrongpassword");
            }
        }

        if (command.isPasswordChange() && command.isLdapAuthenticated()) {
            errors.rejectValue("password", "usersettings.passwordnotsupportedforldap");
        }

        if (securityService.getCurrentUser(request).getUsername().equals(username)) {
            // These errors don't need translation since the option isn't exposed to the user
            if (command.isDeleteUser()) {
                errors.rejectValue("deleteUser", null, "Cannot delete the current user");
            }
            if (! command.isAdminRole()) {
                errors.rejectValue("adminRole", null, "Cannot remove admin from the current user");
            }
        }

    }

}