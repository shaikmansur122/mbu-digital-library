package edu.mbu.library.assignments;

import edu.mbu.library.modules.Subject;
import edu.mbu.library.storage.FileStorageService;
import edu.mbu.library.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** Rules for posting, submitting and grading assignments. Failures are IllegalArgumentExceptions with a friendly message. */
@Service
public class AssignmentService {

    private final AssignmentRepository assignments;
    private final AssignmentSubmissionRepository submissions;
    private final FileStorageService storage;

    public AssignmentService(AssignmentRepository assignments, AssignmentSubmissionRepository submissions,
                             FileStorageService storage) {
        this.assignments = assignments;
        this.submissions = submissions;
        this.storage = storage;
    }

    @Transactional
    public Assignment create(Subject subject, String title, String description, String dueDateText,
                             Integer maxMarks, MultipartFile attachment) throws IOException {
        title = title == null ? "" : title.trim();
        if (title.isEmpty() || title.length() > 150) {
            throw new IllegalArgumentException("Title is required (up to 150 characters).");
        }
        description = description == null ? "" : description.trim();
        if (description.length() > 4000) {
            throw new IllegalArgumentException("Description is too long (up to 4000 characters).");
        }
        int max = maxMarks == null ? 10 : maxMarks;
        if (max < 1 || max > 100) {
            throw new IllegalArgumentException("Maximum marks must be between 1 and 100.");
        }

        Assignment a = new Assignment();
        a.setSubject(subject);
        a.setTitle(title);
        a.setDescription(description.isEmpty() ? null : description);
        a.setDueDate(parseDue(dueDateText));
        a.setMaxMarks(max);
        if (attachment != null && !attachment.isEmpty()) {
            a.setAttachmentFile(storage.saveAssignmentFile(FileStorageService.ASSIGNMENTS, attachment));
            a.setAttachmentName(FileStorageService.cleanOriginalName(attachment));
        }
        return assignments.save(a);
    }

    @Transactional
    public void delete(Assignment assignment) {
        for (AssignmentSubmission s : submissions.findByAssignment(assignment)) {
            storage.deleteQuietly(FileStorageService.SUBMISSIONS, s.getFileName());
            submissions.delete(s);
        }
        storage.deleteQuietly(FileStorageService.ASSIGNMENTS, assignment.getAttachmentFile());
        assignments.delete(assignment);
    }

    /** Removes every assignment of a subject; used when the subject is deleted. */
    @Transactional
    public void deleteAllForSubject(Subject subject) {
        for (Assignment a : assignments.findBySubject(subject)) {
            delete(a);
        }
    }

    /** Removes a student's submissions (and files); used when the student's account is deleted. */
    @Transactional
    public void deleteSubmissionsOf(User student) {
        for (AssignmentSubmission s : submissions.findByStudent(student)) {
            storage.deleteQuietly(FileStorageService.SUBMISSIONS, s.getFileName());
            submissions.delete(s);
        }
    }

    /** Saves the student's file. A later submission replaces the earlier one until the work has been graded. */
    @Transactional
    public AssignmentSubmission submit(Assignment assignment, User student, MultipartFile file, String note)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose the file to upload.");
        }
        note = note == null ? "" : note.trim();
        if (note.length() > 1000) {
            throw new IllegalArgumentException("The note is too long (up to 1000 characters).");
        }
        AssignmentSubmission s = submissions.findByAssignmentAndStudent(assignment, student).orElse(null);
        if (s != null && s.isGraded()) {
            throw new IllegalArgumentException("This assignment has already been graded, so it can no longer be changed.");
        }

        String newFile = storage.saveAssignmentFile(FileStorageService.SUBMISSIONS, file);
        String oldFile = null;
        LocalDateTime now = LocalDateTime.now();
        if (s == null) {
            s = new AssignmentSubmission();
            s.setAssignment(assignment);
            s.setStudent(student);
        } else {
            oldFile = s.getFileName();
            s.setAttempts(s.getAttempts() + 1);
        }
        s.setFileName(newFile);
        s.setOriginalName(FileStorageService.cleanOriginalName(file));
        s.setNote(note.isEmpty() ? null : note);
        s.setSubmittedAt(now);
        s.setLate(assignment.getDueDate() != null && now.isAfter(assignment.getDueDate()));
        s = submissions.save(s);
        storage.deleteQuietly(FileStorageService.SUBMISSIONS, oldFile);
        return s;
    }

    @Transactional
    public AssignmentSubmission grade(AssignmentSubmission submission, Integer marks, String feedback) {
        int max = submission.getAssignment().getMaxMarks();
        if (marks == null || marks < 0 || marks > max) {
            throw new IllegalArgumentException("Marks must be a number from 0 to " + max + ".");
        }
        feedback = feedback == null ? "" : feedback.trim();
        if (feedback.length() > 1000) {
            throw new IllegalArgumentException("Feedback is too long (up to 1000 characters).");
        }
        submission.setMarks(marks);
        submission.setFeedback(feedback.isEmpty() ? null : feedback);
        submission.setGradedAt(LocalDateTime.now());
        return submissions.save(submission);
    }

    /** Lets faculty reopen graded work, for example if the student must resubmit. */
    @Transactional
    public AssignmentSubmission removeGrade(AssignmentSubmission submission) {
        submission.setMarks(null);
        submission.setFeedback(null);
        submission.setGradedAt(null);
        return submissions.save(submission);
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
}
