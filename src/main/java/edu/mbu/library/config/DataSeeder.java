package edu.mbu.library.config;

import edu.mbu.library.user.Role;
import edu.mbu.library.user.User;
import edu.mbu.library.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates sample accounts on first start so the app can be tested straight away. */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public DataSeeder(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        seed("admin", "Admin@123", Role.ADMIN, "University Admin", null, null, null, null);
        seed("faculty1", "Faculty@123", Role.FACULTY, "Dr. Sample Faculty", null,
                "Data Science", null, null);
        seed("student1", "Student@123", Role.STUDENT, "Sample Student", "23MBU0001",
                "Data Science", "A", 3);
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
