package com.pps.profilesystem.Config;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hybrid password encoder that supports both MD5 (legacy) and BCrypt (new)
 * This allows gradual migration from MD5 to BCrypt
 */
public class HybridPasswordEncoder implements PasswordEncoder {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);

    @Override
    public String encode(CharSequence rawPassword) {
        // Always encode new passwords with BCrypt
        return bcrypt.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        // If the encoded password starts with $2a$ or $2b$, it's BCrypt
        if (encodedPassword.startsWith("$2a$") || encodedPassword.startsWith("$2b$")) {
            return bcrypt.matches(rawPassword, encodedPassword);
        }
        
        // Otherwise, try MD5 (legacy)
        String md5Hash = md5Encode(rawPassword.toString());
        return md5Hash.equals(encodedPassword);
    }

    private String md5Encode(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }
}
