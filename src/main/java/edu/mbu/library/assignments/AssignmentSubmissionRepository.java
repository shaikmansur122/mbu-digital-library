package edu.mbu.library.assignments;

import edu.mbu.library.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, Long> {

    Optional<AssignmentSubmission> findByAssignmentAndStudent(Assignment assignment, User student);

    List<AssignmentSubmission> findByAssignment(Assignment assignment);

    List<AssignmentSubmission> findByStudent(User student);

    List<AssignmentSubmission> findByStudentAndAssignmentIn(User student, Collection<Assignment> assignments);

    long countByAssignment(Assignment assignment);
}
