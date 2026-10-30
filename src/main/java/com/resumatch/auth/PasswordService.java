package com.resumatch.auth;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Strong adaptive-style hashing using SHA-256 + per-user salt + iterations.
 * (Pure JDK; no external crypto deps to keep the stack zero-cost.)
 */
@Service
public class PasswordService {
    private static final int ITERATIONS = 5000;
    private static final SecureRandom RNG = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        byte[] hash = pbkdf(password.getBytes(StandardCharsets.UTF_8), salt, ITERATIONS);
        return "pbkdf$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
    }

    public boolean verify(String password, String stored) {
        if (stored == null) return false;
        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 4 || !"pbkdf".equals(parts[0])) return false;
            int iters = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf(password.getBytes(StandardCharsets.UTF_8), salt, iters);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    public String randomToken(int bytes) {
        byte[] b = new byte[bytes];
        RNG.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public String hashOpaque(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return token;
        }
    }

    public String otp(int digits) {
        int max = (int) Math.pow(10, digits);
        int v = RNG.nextInt(max);
        return String.format("%0" + digits + "d", v);
    }

    private byte[] pbkdf(byte[] password, byte[] salt, int iters) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hash = md.digest(password);
            for (int i = 1; i < iters; i++) {
                md.reset();
                hash = md.digest(hash);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
