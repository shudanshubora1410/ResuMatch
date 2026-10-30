package com.resumatch.auth;

import com.resumatch.common.AccountStatus;
import com.resumatch.common.ApiResponse;
import com.resumatch.common.AuthContext;
import com.resumatch.common.Role;
import com.resumatch.notifications.EmailService;
import com.resumatch.user.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final SeekerProfileRepository seekerRepo;
    private final CompanyRepository companyRepo;
    private final RecruiterProfileRepository recruiterRepo;
    private final PasswordResetTokenRepository resetRepo;
    private final EmailOtpRepository otpRepo;
    private final LoginAttemptRepository attemptRepo;
    private final PasswordService passwords;
    private final EmailService email;

    private static final int MAX_FAILS = 5;
    private static final int LOCK_MINUTES = 15;

    public AuthService(UserRepository userRepo,
                       SeekerProfileRepository seekerRepo,
                       CompanyRepository companyRepo,
                       RecruiterProfileRepository recruiterRepo,
                       PasswordResetTokenRepository resetRepo,
                       EmailOtpRepository otpRepo,
                       LoginAttemptRepository attemptRepo,
                       PasswordService passwords,
                       EmailService email) {
        this.userRepo = userRepo;
        this.seekerRepo = seekerRepo;
        this.companyRepo = companyRepo;
        this.recruiterRepo = recruiterRepo;
        this.resetRepo = resetRepo;
        this.otpRepo = otpRepo;
        this.attemptRepo = attemptRepo;
        this.passwords = passwords;
        this.email = email;
    }

    @Transactional
    public UserEntity registerSeeker(String fullName, String emailAddr, String phone, String password) {
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("Full name is required");
        validateEmail(emailAddr);
        validatePhone(phone);
        validatePassword(password);
        if (userRepo.findByEmail(emailAddr).isPresent()) throw new IllegalArgumentException("Email already registered");
        if (userRepo.findByDisplayName(fullName).isPresent()) throw new IllegalArgumentException("Display name already taken");

        UserEntity u = new UserEntity();
        u.role = Role.SEEKER;
        u.displayName = fullName.trim();
        u.email = emailAddr.trim().toLowerCase();
        u.phone = phone.trim();
        u.passwordHash = passwords.hash(password);
        u.accountStatus = AccountStatus.ACTIVE;
        u = userRepo.save(u);

        SeekerProfileEntity sp = new SeekerProfileEntity();
        sp.userId = u.id;
        sp.profileCompletionPct = 40;
        seekerRepo.save(sp);

        email.send(u.id, "Welcome to ResuMatch",
                "Hi " + u.displayName + ",\n\nYour ResuMatch seeker account is ready. Upload your resume to get a transparent ATS score.\n\n— ResuMatch");

        return u;
    }

    @Transactional
    public UserEntity registerRecruiter(String companyName, String recruiterName, String emailAddr, String phone, String password) {
        if (companyName == null || companyName.isBlank()) throw new IllegalArgumentException("Company name is required");
        if (recruiterName == null || recruiterName.isBlank()) throw new IllegalArgumentException("Recruiter name is required");
        validateEmail(emailAddr);
        validatePhone(phone);
        validatePassword(password);
        if (userRepo.findByEmail(emailAddr).isPresent()) throw new IllegalArgumentException("Email already registered");

        CompanyEntity c = new CompanyEntity();
        c.companyName = companyName.trim();
        c.companyDescription = "";
        c = companyRepo.save(c);

        UserEntity u = new UserEntity();
        u.role = Role.RECRUITER;
        u.displayName = recruiterName.trim();
        u.email = emailAddr.trim().toLowerCase();
        u.phone = phone.trim();
        u.passwordHash = passwords.hash(password);
        u.accountStatus = AccountStatus.ACTIVE;
        u = userRepo.save(u);

        RecruiterProfileEntity rp = new RecruiterProfileEntity();
        rp.userId = u.id;
        rp.companyId = c.id;
        rp.recruiterName = recruiterName.trim();
        rp.profileCompletionPct = 50;
        recruiterRepo.save(rp);

        email.send(u.id, "Welcome to ResuMatch",
                "Hi " + u.displayName + ",\n\nYour ResuMatch recruiter account for " + c.companyName + " is ready. Post your first job to start ranking applicants.\n\n— ResuMatch");
        return u;
    }

    @Transactional
    public UserEntity login(String identifier, String password, String ip) {
        if (identifier == null || identifier.isBlank()) throw new IllegalArgumentException("Identifier required");
        Instant since = Instant.now().minus(LOCK_MINUTES, ChronoUnit.MINUTES);
        if (attemptRepo.countRecentFailures(identifier, since) >= MAX_FAILS) {
            throw new IllegalArgumentException("Too many failed attempts. Try again in " + LOCK_MINUTES + " minutes.");
        }
        if (ip != null && attemptRepo.countRecentIpFailures(ip, since) >= MAX_FAILS * 2) {
            throw new IllegalArgumentException("Too many failed attempts from this network. Try again later.");
        }
        UserEntity u = userRepo.findByEmail(identifier.trim().toLowerCase())
                .or(() -> userRepo.findByDisplayName(identifier.trim()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (u.accountStatus != AccountStatus.ACTIVE) throw new IllegalArgumentException("Account is " + u.accountStatus);

        boolean ok = passwords.verify(password, u.passwordHash);
        recordAttempt(identifier, ip, ok);
        if (!ok) throw new IllegalArgumentException("Invalid credentials");

        u.lastLoginAt = Instant.now();
        u.updatedAt = Instant.now();
        return userRepo.save(u);
    }

    private void recordAttempt(String id, String ip, boolean ok) {
        LoginAttemptEntity a = new LoginAttemptEntity();
        a.identifierType = id.contains("@") ? "EMAIL" : "NAME";
        a.identifierValue = id;
        a.ipAddress = ip;
        a.success = ok;
        attemptRepo.save(a);
    }

    @Transactional
    public void startPasswordReset(String emailAddr) {
        if (emailAddr == null || emailAddr.isBlank()) return; // do not leak
        userRepo.findByEmail(emailAddr.trim().toLowerCase()).ifPresent(u -> {
            String token = passwords.randomToken(24);
            PasswordResetTokenEntity t = new PasswordResetTokenEntity();
            t.userId = u.id;
            t.tokenHash = passwords.hashOpaque(token);
            t.expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);
            resetRepo.save(t);
            email.send(u.id, "ResuMatch password reset",
                    "Use this token to reset your password (valid 30 minutes):\n\n" + token +
                            "\n\nOr open the reset page and paste it.\n\n— ResuMatch");
        });
    }

    @Transactional
    public void confirmPasswordReset(String token, String newPassword) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Token required");
        validatePassword(newPassword);
        String hash = passwords.hashOpaque(token);
        PasswordResetTokenEntity t = resetRepo.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token"));
        if (t.usedAt != null || t.expiresAt.isBefore(Instant.now())) throw new IllegalArgumentException("Token expired");
        UserEntity u = userRepo.findById(t.userId).orElseThrow(() -> new NoSuchElementException("User not found"));
        u.passwordHash = passwords.hash(newPassword);
        u.updatedAt = Instant.now();
        t.usedAt = Instant.now();
    }

    public void loginSession(HttpSession session, UserEntity u) {
        AuthContext.login(session, u.id, u.role.name(), u.displayName);
    }

    public UserEntity requireUser(HttpSession session) {
        Long id = AuthContext.userId(session);
        return userRepo.findById(id).orElseThrow(() -> new SecurityException("User not found"));
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank() || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new IllegalArgumentException("Invalid email");
    }
    private void validatePhone(String phone) {
        if (phone == null || phone.isBlank() || !phone.matches("\\+?\\d[\\d\\s\\-]{7,}\\d"))
            throw new IllegalArgumentException("Invalid phone");
    }
    private void validatePassword(String pw) {
        if (pw == null || pw.length() < 8) throw new IllegalArgumentException("Password must be at least 8 characters");
        if (!pw.matches(".*[A-Za-z].*") || !pw.matches(".*\\d.*"))
            throw new IllegalArgumentException("Password must contain letters and digits");
    }
}
