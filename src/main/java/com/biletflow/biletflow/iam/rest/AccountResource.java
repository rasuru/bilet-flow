package com.biletflow.biletflow.iam.rest;

import com.biletflow.biletflow.iam.application.MailService;
import com.biletflow.biletflow.iam.application.UserService;
import com.biletflow.biletflow.iam.application.dto.AdminUserDTO;
import com.biletflow.biletflow.iam.domain.User;
import com.biletflow.biletflow.iam.persistence.UserRepository;
import com.biletflow.biletflow.iam.rest.dto.AccountResponse;
import com.biletflow.biletflow.iam.rest.dto.ChangePasswordRequest;
import com.biletflow.biletflow.iam.rest.dto.PasswordResetFinishRequest;
import com.biletflow.biletflow.iam.rest.dto.PasswordResetInitRequest;
import com.biletflow.biletflow.iam.rest.dto.RegisterRequest;
import com.biletflow.biletflow.iam.rest.dto.UpdateAccountRequest;
import com.biletflow.biletflow.iam.rest.errors.EmailAlreadyUsedException;
import com.biletflow.biletflow.iam.rest.errors.LoginAlreadyUsedException;
import com.biletflow.biletflow.iam.security.SecurityUtils;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Validated
public class AccountResource {

    @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Account resource request invalid")
    private static class AccountResourceException extends RuntimeException {

        private AccountResourceException(String message) {
            super(message);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(AccountResource.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;

    public AccountResource(
        UserRepository userRepository,
        UserService userService,
        MailService mailService,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void registerAccount(@Valid @RequestBody RegisterRequest request) {
        LOG.debug("REST request to register account");

        AdminUserDTO userDTO = new AdminUserDTO();
        userDTO.setLogin(request.login());
        userDTO.setFirstName(request.firstName());
        userDTO.setLastName(request.lastName());
        userDTO.setEmail(request.email());
        userDTO.setLangKey(request.langKey());

        User user = userService.registerUser(userDTO, request.password());
        mailService.sendActivationEmail(user);
    }

    @GetMapping("/activate")
    public void activateAccount(@RequestParam(value = "key") String key) {
        LOG.debug("REST request to activate account");

        Optional<User> user = userService.activateRegistration(key);
        if (user.isEmpty()) {
            throw new AccountResourceException("No user was found for this activation key");
        }
    }

    @GetMapping("/account")
    public AccountResponse getAccount() {
        LOG.debug("REST request to get account");

        AdminUserDTO user = userService
            .getUserWithAuthorities()
            .map(AdminUserDTO::new)
            .orElseThrow(() -> new AccountResourceException("User could not be found"));

        return toAccountResponse(user);
    }

    @PostMapping("/account")
    public void saveAccount(@Valid @RequestBody UpdateAccountRequest request) {
        LOG.debug("REST request to save account");

        String userLogin = SecurityUtils.getCurrentUserLogin().orElseThrow(() ->
            new AccountResourceException("Current user login not found")
        );

        Optional<User> existingUser = userRepository.findOneByEmailIgnoreCase(request.email());
        if (existingUser.isPresent() && !existingUser.orElseThrow().getLogin().equalsIgnoreCase(userLogin)) {
            throw new EmailAlreadyUsedException();
        }

        Optional<User> user = userRepository.findOneByLogin(userLogin);
        if (user.isEmpty()) {
            throw new AccountResourceException("User could not be found");
        }

        userService.updateUser(request.firstName(), request.lastName(), request.email(), request.langKey(), request.imageUrl());
    }

    @PostMapping("/account/change-password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        LOG.debug("REST request to change password");

        userService.changePassword(request.currentPassword(), request.newPassword());
    }

    @PostMapping("/account/reset-password/init")
    public void requestPasswordReset(@Valid @RequestBody PasswordResetInitRequest request) {
        LOG.debug("REST request to request password reset");

        Optional<User> user = userService.requestPasswordReset(request.email());
        if (user.isPresent()) {
            mailService.sendPasswordResetMail(user.orElseThrow());
        } else {
            LOG.warn("Password reset requested for non existing mail");
        }
    }

    @PostMapping("/account/reset-password/finish")
    public void finishPasswordReset(@Valid @RequestBody PasswordResetFinishRequest request) {
        Optional<User> user = userService.completePasswordReset(request.newPassword(), request.key());

        if (user.isEmpty()) {
            passwordEncoder.encode(request.newPassword());
            throw new AccountResourceException("No user was found for this reset key");
        }
    }

    private static AccountResponse toAccountResponse(AdminUserDTO user) {
        return new AccountResponse(
            user.getId(),
            user.getLogin(),
            user.getFirstName(),
            user.getLastName(),
            user.getEmail(),
            user.getImageUrl(),
            user.isActivated(),
            user.getLangKey(),
            user.getAuthorities() == null ? Set.of() : Set.copyOf(user.getAuthorities())
        );
    }
}
