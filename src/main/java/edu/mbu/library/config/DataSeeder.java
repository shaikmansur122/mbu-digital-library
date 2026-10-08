package edu.mbu.library.config;

import edu.mbu.library.user.Role;
import edu.mbu.library.user.User;
import edu.mbu.library.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates sample accounts on first start so the app can be tried straight away.
 * For a real deployment set app.seed-sample-accounts=false (their passwords are public) and
 * app.initial-admin-password to create just one admin account with a password of your own.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final boolean seedSamples;
    private final String initialAdminPassword;

    public DataSeeder(UserRepository users, PasswordEncoder encoder,
                      @Value("${app.seed-sample-accounts:true}") boolean seedSamples,
                      @Value("${app.initial-admin-password:}") String initialAdminPassword) {
        this.users = users;
        this.encoder = encoder;
        this.seedSamples = seedSamples;
        this.initialAdminPassword = initialAdminPassword;
    }

    @Override
    public void run(String... args) {
        if (!seedSamples) {
            if (!initialAdminPassword.isBlank()) {
                seed("admin", initialAdminPassword, Role.ADMIN, "University Admin", null, null, null, null);
            }
            return;
        }
        seed("admin", "Admin@123", Role.ADMIN, "University Admin", null, null, null, null);
        seed("faculty1", "Faculty@123", Role.FACULTY, "Dr. Sample Faculty", null,
                "Data Science", null, null);
        seed("student1", "Student@123", Role.STUDENT, "Sample Student", "23MBU0001",
                "Data Science", "A", 3);

        // Give the sample student a faculty card (also fixes accounts created before this field existed).
        users.findByUsername("student1").ifPresent(student ->
                users.findByUsername("faculty1").ifPresent(faculty -> {
                    if (student.getMentor() == null) {
                        student.setMentor(faculty);
                        users.save(student);
                    }
                }));
    }

    private void seed(String username, String rawPassword, Role role, String fullName,
                      String rollNo, String department, String section, Integer semester) {
        if (users.existsByUsername(username)) {
            return;
        }
        User u = new User();
        u.setUsername(username);
        u.setPassword(encoder.encode(rawPassword));
        u.setRole(role);
        u.setFullName(fullName);
        u.setRollNo(rollNo);
        u.setDepartment(department);
        u.setSection(section);
        u.setSemester(semester);
        users.save(u);
        log.info("Seeded sample {} account '{}'", role, username);
    }
}
