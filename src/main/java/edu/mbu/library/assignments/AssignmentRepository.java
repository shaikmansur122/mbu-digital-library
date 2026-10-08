package edu.mbu.library.assignments;

import edu.mbu.library.modules.Subject;
import edu.mbu.library.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findBySubjectInOrderByCreatedAtDesc(Collection<Subject> subjects);

    List<Assignment> findBySubjectFacultyOrderByCreatedAtDesc(User faculty);

    List<Assignment> findBySubject(Subject subject);
}
