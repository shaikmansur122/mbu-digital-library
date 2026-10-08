package edu.mbu.library.labs;

import edu.mbu.library.modules.Subject;
import edu.mbu.library.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Rules for assigning lab experiments and submitting solutions. Failures are IllegalArgumentExceptions with a friendly message. */
@Service
public class LabService {

    public static final int MAX_CODE_CHARS = 50_000;

    private final LabExperimentRepository experiments;
    private final LabSubmissionRepository submissions;

    public LabService(LabExperimentRepository experiments, LabSubmissionRepository submissions) {
        this.experiments = experiments;
        this.submissions = submissions;
    }

    @Transactional
    public LabExperiment create(Subject subject, String title, String description, Language language,
                                String starterCode, String dueDateText) {
        title = title == null ? "" : title.trim();
        if (title.isEmpty() || title.length() > 150) {
            throw new IllegalArgumentException("Title is required (up to 150 characters).");
        }
        description = description == null ? "" : description.trim();
        if (description.length() > 4000) {
            throw new IllegalArgumentException("Description is too long (up to 4000 characters).");
        }
        if (language == null) {
            throw new IllegalArgumentException("Choose the programming language.");
        }
        starterCode = starterCode == null ? "" : starterCode;
        if (starterCode.length() > MAX_CODE_CHARS) {
            throw new IllegalArgumentException("Starter code is too long.");
        }

        LabExperiment e = new LabExperiment();
        e.setSubject(subject);
        e.setTitle(title);
        e.setDescription(description.isEmpty() ? null : description);
        e.setLanguage(language);
        e.setStarterCode(starterCode.isBlank() ? null : starterCode);
        e.setDueDate(parseDue(dueDateText));
        return experiments.save(e);
    }

    @Transactional
    public void delete(LabExperiment experiment) {
        submissions.deleteAll(submissions.findByExperiment(experiment));
        experiments.delete(experiment);
    }

    /** Removes every lab experiment of a subject (and their submissions); used when the subject is deleted. */
    @Transactional
    public void deleteAllForSubject(Subject subject) {
        for (LabExperiment e : experiments.findBySubject(subject)) {
            delete(e);
        }
    }

    /** Removes a student's lab submissions; used when the student's account is deleted. */
    @Transactional
    public void deleteSubmissionsOf(User student) {
        submissions.deleteByStudent(student);
    }

    /** Saves the student's solution, replacing an earlier submission for the same experiment. */
    @Transactional
    public LabSubmission submit(LabExperiment experiment, User student, String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Write some code before submitting.");
        }
        if (code.length() > MAX_CODE_CHARS) {
            throw new IllegalArgumentException("Your code is too long (up to " + MAX_CODE_CHARS + " characters).");
        }
        LocalDateTime now = LocalDateTime.now();
        LabSubmission s = submissions.findByExperimentAndStudent(experiment, student).orElse(null);
        if (s == null) {
            s = new LabSubmission();
            s.setExperiment(experiment);
            s.setStudent(student);
        } else {
            s.setAttempts(s.getAttempts() + 1);
        }
        s.setCode(code);
        s.setSubmittedAt(now);
        s.setLate(experiment.getDueDate() != null && now.isAfter(experiment.getDueDate()));
        return submissions.save(s);
    }

    private static LocalDateTime parseDue(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim());   // from <input type="datetime-local">
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("The due date is not valid.");
        }
    }

    public List<Language> languages() {
        return List.of(Language.values());
    }
}
