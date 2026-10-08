# MBU Digital Library

Data Science Digital Library for Mohan Babu University: modules, lab experiments, assignments,
attendance and marks for students, managed by faculty and an admin.

**Stack:** Java 21+ / Spring Boot 3.5, Spring Security, Spring Data JPA, MySQL 8, Thymeleaf, HTML/CSS/JS.

## Setup

1. Install JDK 21+, Maven and MySQL 8.
2. In MySQL Workbench (as root) run [`database/setup.sql`](database/setup.sql). It creates the
   `mbu_library` database and an `mbu_app` user.
3. Start the app:

   ```bash
   mvn spring-boot:run
   ```

4. Open <http://localhost:8080>.

Database settings live in `src/main/resources/application.properties` and can be overridden with
the `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` environment variables.

## Sample accounts (created on first start)

| Role    | Username   | Password       |
|---------|------------|----------------|
| Admin   | `admin`    | `Admin@123`    |
| Faculty | `faculty1` | `Faculty@123`  |
| Student | `student1` | `Student@123`  |

Change these before using the app for anything real. Admins create further accounts from the
admin dashboard.

## Progress

- [x] Phase 1: project setup, MySQL, login for student / faculty / admin, admin account management
- [ ] Phase 2: main layout (top nav, side menu, ID card)
- [ ] Phase 3: modules (subjects, PDFs, YouTube links)
- [ ] Phase 4: lab experiments with in-browser code editor
- [ ] Phase 5: assignments
- [ ] Phase 6: attendance
- [ ] Phase 7: marks and results
- [ ] Phase 8: tools, resources, saved codes
- [ ] Phase 9: polish
