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

## Code editor and running programs

The editor is Monaco (the VS Code editor), loaded from a CDN, so the machine running the browser needs
internet access. It falls back to a plain text box if Monaco cannot load.

| Language | Where it runs |
|----------|---------------|
| Python   | In the browser (Pyodide, with numpy/pandas). Student code never reaches the server. |
| SQL      | In the browser (sql.js / SQLite). Each run starts with an empty database. |
| Java     | On the server, with the JDK that runs the app. |
| C, C++   | On the server, and need `gcc` / `g++` on the PATH (for example MinGW-w64 or WinLibs on Windows). Without them, students can still write and submit, but not run. |

Server-side runs use a temporary folder, a 5 second run limit, a 20 KB output cap, at most 2 runs at a
time and a minimal environment. They still run with the same operating-system permissions as the app, so
for real deployment run the app in a container or under a locked-down account. Settings
(`app.code-runner.*` in `application.properties`) can turn server-side running off or change the limits.

## Progress

- [x] Phase 1: project setup, MySQL, login for student / faculty / admin, admin account management
- [x] Phase 2: main layout (top nav, side menu, ID card, faculty card, profile with photo and resume upload)
- [x] Phase 3: modules (semester, subjects, modules, PDFs and YouTube links posted by the subject's faculty)
- [x] Phase 4: lab experiments with in-browser code editor (Python, Java, C++, C, SQL)
- [x] Phase 5: assignments (posting, file submission, grading with feedback, private downloads)
- [x] Phase 6: attendance (faculty take it per subject and day, students see percentages, report and CSV)
- [ ] Phase 7: marks and results
- [ ] Phase 8: tools, resources, saved codes
- [ ] Phase 9: polish
