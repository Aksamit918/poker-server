package com.poker.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
public class GoogleAuthService {

    private final GoogleIdTokenVerifier verifier;

    public GoogleAuthService(@Value("${google.client.id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public GoogleUser verifyToken(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken != null) {
                Payload payload = idToken.getPayload();

                if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                    throw new RuntimeException("Unverified Google Account");
                }

                return new GoogleUser(
                        payload.getSubject(),
                        payload.getEmail(),
                        (String) payload.get("name")
                );
            } else {
                throw new RuntimeException("Invalid ID token.");
            }
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Token verification failed", e);
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