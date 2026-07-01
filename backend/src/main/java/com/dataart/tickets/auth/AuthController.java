package com.dataart.tickets.auth;

import com.dataart.tickets.auth.dto.LoginRequest;
import com.dataart.tickets.auth.dto.ResendRequest;
import com.dataart.tickets.auth.dto.SignupRequest;
import com.dataart.tickets.auth.dto.UserResponse;
import com.dataart.tickets.auth.dto.VerificationResult;
import com.dataart.tickets.config.SessionAbsoluteTimeoutFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

/**
 * Auth endpoints (architecture.md §8/§9). Sign-up/verify/resend are public; login establishes a
 * server-side session, logout invalidates it, and {@code /me} reports the current user. The
 * public allowlist + CSRF are enforced in HTS-013 (FR-A12).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerification;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UserRepository users;
    private final Clock clock;

    public AuthController(AuthService authService,
                          EmailVerificationService emailVerification,
                          AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          UserRepository users,
                          Clock clock) {
        this.authService = authService;
        this.emailVerification = emailVerification;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.users = users;
        this.clock = clock;
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        User user = authService.signup(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    /**
     * Verify an email address from the link in the verification email (FR-A6..A9). Public;
     * does not create a session (no auto-login). Invalid/expired/consumed tokens → 400
     * TOKEN_INVALID via the exception handler.
     */
    @GetMapping("/verify")
    public VerificationResult verify(@RequestParam("token") String token) {
        User user = emailVerification.verify(token);
        return new VerificationResult("verified", user.getEmail());
    }

    /**
     * Resend a verification email (FR-A10/A11). Public. Always returns 202 with a generic body
     * regardless of whether the account exists or is already verified (no enumeration).
     */
    @PostMapping("/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VerificationResult resend(@Valid @RequestBody ResendRequest request) {
        emailVerification.resend(request.email());
        return new VerificationResult("sent", request.email());
    }

    /**
     * Authenticate and start a session (FR-A3/A7). Bad credentials → 401 BAD_CREDENTIALS;
     * unverified account → 403 EMAIL_NOT_VERIFIED (both mapped in the exception handler).
     */
    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = EmailNormalizer.normalize(request.email());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        // Persist the authenticated context into the session so subsequent requests are recognized.
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        // Stamp the login instant so the absolute session-lifetime cap can be enforced (HTS-046).
        httpRequest.getSession().setAttribute(
                SessionAbsoluteTimeoutFilter.SESSION_CREATED_AT, clock.instant());

        return UserResponse.from(users.findByEmail(email).orElseThrow());
    }

    /** Current authenticated user, for the FE auth context. 401 when unauthenticated. */
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        String email = EmailNormalizer.normalize(authentication.getName());
        return UserResponse.from(users.findByEmail(email).orElseThrow());
    }

    /** Invalidate the server session and clear the security context (FR-A3). */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
