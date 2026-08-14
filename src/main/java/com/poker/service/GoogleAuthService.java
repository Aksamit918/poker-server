package com.poker.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.poker.exception.InvalidCredentialsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Slf4j
@Service
public class GoogleAuthService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 250L;

    private final GoogleIdTokenVerifier verifier;

    public GoogleAuthService(@Value("${google.client.id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public GoogleUser verifyToken(String idTokenString) {
        IOException lastIo = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return verifyOnce(idTokenString);
            } catch (GeneralSecurityException e) {
                log.warn("Google ID token failed security checks: {}", e.getMessage());
                throw new InvalidCredentialsException("error.google.invalid.token");
            } catch (IOException e) {
                lastIo = e;
                log.warn("Google public key fetch failed (attempt {}/{}): {}", attempt, MAX_ATTEMPTS, e.toString());
                if (attempt < MAX_ATTEMPTS) {
                    sleepBeforeRetry(attempt);
                }
            }
        }

        log.error("Cannot reach Google to verify ID token after {} attempts", MAX_ATTEMPTS, lastIo);
        throw new RuntimeException("Token verification failed", lastIo);
    }

    private GoogleUser verifyOnce(String idTokenString) throws GeneralSecurityException, IOException {
        GoogleIdToken idToken = verifier.verify(idTokenString);
        if (idToken == null) {
            throw new InvalidCredentialsException("error.google.invalid.token");
        }

        Payload payload = idToken.getPayload();
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new InvalidCredentialsException("error.google.unverified.email");
        }

        return new GoogleUser(
                payload.getSubject(),
                payload.getEmail(),
                (String) payload.get("name")
        );
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_DELAY_MS * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Token verification interrupted", ie);
        }
    }

    public static class GoogleUser {
        private final String googleId;
        private final String email;
        private final String name;

        public GoogleUser(String googleId, String email, String name) {
            this.googleId = googleId;
            this.email = email;
            this.name = name;
        }
        public String getGoogleId() { return googleId; }
        public String getEmail() { return email; }
        public String getName() { return name; }
    }
}
