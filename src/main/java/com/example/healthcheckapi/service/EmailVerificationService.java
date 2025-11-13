package com.example.healthcheckapi.service;

import com.example.healthcheckapi.entity.EmailVerificationToken;
import com.example.healthcheckapi.entity.User;
import com.example.healthcheckapi.repository.EmailVerificationTokenRepository;
import com.example.healthcheckapi.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    public EmailVerificationToken createToken(String email) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserEmail(email);
        return tokenRepository.save(token);
    }

    public boolean verifyToken(String email, String token) {
        Optional<EmailVerificationToken> tokenOpt = tokenRepository.findByToken(token);

        if (tokenOpt.isEmpty()) {
            log.warn("Verification failed: Token not found");
            return false;
        }

        EmailVerificationToken verificationToken = tokenOpt.get();

        // Check if token belongs to the correct user
        if (!verificationToken.getUserEmail().equals(email)) {
            log.warn("Verification failed: Token doesn't match email");
            return false;
        }

        // Check if token is expired
        if (verificationToken.isExpired()) {
            log.warn("Verification failed: Token expired");
            return false;
        }

        // Check if already verified
        if (verificationToken.isVerified()) {
            log.warn("Verification failed: Token already used");
            return false;
        }

        // Mark token as verified
        verificationToken.setVerified(true);
        tokenRepository.save(verificationToken);

        // Mark user as verified
        Optional<User> userOpt = userRepository.findByUsername(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setEmailVerified(true);
            userRepository.save(user);
            log.info("Email verified successfully for user: {}", email);
            return true;
        }

        log.error("User not found for email: {}", email);
        return false;
    }

    public boolean hasUnverifiedToken(String email) {
        Optional<EmailVerificationToken> tokenOpt = tokenRepository.findByUserEmailAndVerifiedFalse(email);
        return tokenOpt.isPresent();
    }
}