package com.schoolmanagement.auth.controller;

import com.schoolmanagement.auth.dto.ChangePasswordRequest;
import com.schoolmanagement.auth.dto.CurrentUserResponse;
import com.schoolmanagement.auth.dto.ForgotPasswordRequest;
import com.schoolmanagement.auth.dto.LoginRequest;
import com.schoolmanagement.auth.dto.MessageResponse;
import com.schoolmanagement.auth.dto.ResetPasswordRequest;
import com.schoolmanagement.auth.service.AuthService;
import com.schoolmanagement.auth.service.AuthSession;
import com.schoolmanagement.auth.service.ClientInfo;
import com.schoolmanagement.auth.service.PasswordResetService;
import com.schoolmanagement.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private static final String RESET_REQUEST_ACCEPTED =
            "If an account exists for that email address, a password reset link has been sent.";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final AuthCookies cookies;

    public AuthController(AuthService authService, PasswordResetService passwordResetService,
            AuthCookies cookies) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.cookies = cookies;
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email, student admission number or teacher employee ID")
    public CurrentUserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
            HttpServletResponse response) {
        AuthSession session = authService.login(request, ClientInfo.from(httpRequest));
        cookies.write(response, session);
        return session.user();
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh token and issue a new access token (cookies)")
    public CurrentUserResponse refresh(HttpServletRequest httpRequest, HttpServletResponse response) {
        try {
            AuthSession session = authService.refresh(cookies.readRefreshToken(httpRequest),
                    ClientInfo.from(httpRequest));
            cookies.write(response, session);
            return session.user();
        } catch (RuntimeException e) {
            cookies.clear(response);
            throw e;
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the refresh token and clear the authentication cookies")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse response) {
        authService.logout(cookies.readRefreshToken(httpRequest), ClientInfo.from(httpRequest));
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Identity and roles of the signed-in user")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return CurrentUserResponse.from(user);
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change the password; ends all other sessions")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest,
            HttpServletResponse response) {
        AuthSession session = authService.changePassword(user.id(), request, ClientInfo.from(httpRequest));
        cookies.write(response, session);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset email; the response never reveals whether the account exists")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        passwordResetService.requestReset(request.email(), ClientInfo.from(httpRequest));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new MessageResponse(RESET_REQUEST_ACCEPTED));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password using an emailed single-use token")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        passwordResetService.resetPassword(request.token(), request.newPassword(), ClientInfo.from(httpRequest));
        return ResponseEntity.noContent().build();
    }
}
