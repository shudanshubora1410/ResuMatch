package com.resumatch.common;

import com.resumatch.auth.PasswordService;
import com.resumatch.user.UserEntity;
import com.resumatch.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds a default admin user on first boot.
 */
@Component
@Order(0)
public class AdminSeed implements CommandLineRunner {
    private final UserRepository userRepo;
    private final PasswordService passwords;

    public AdminSeed(UserRepository userRepo, PasswordService passwords) {
        this.userRepo = userRepo;
        this.passwords = passwords;
    }

    @Override
    public void run(String... args) {
        if (userRepo.findByEmail("admin@resumatch.local").isPresent()) return;
        UserEntity u = new UserEntity();
        u.role = Role.ADMIN;
        u.displayName = "Admin";
        u.email = "admin@resumatch.local";
        u.phone = "0000000000";
        u.passwordHash = passwords.hash("Admin@123");
        u.emailVerified = true;
        u.accountStatus = AccountStatus.ACTIVE;
        userRepo.save(u);
    }
}
