package edu.mbu.library.marks;

import edu.mbu.library.modules.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    List<Assessment> findBySubjectOrderByPositionAscIdAsc(Subject subject);

    List<Assessment> findBySubjectInOrderByPositionAscIdAsc(Collection<Subject> subjects);

    boolean existsBySubjectAndNameIgnoreCase(Subject subject, String name);

    long countBySubject(Subject subject);
}
