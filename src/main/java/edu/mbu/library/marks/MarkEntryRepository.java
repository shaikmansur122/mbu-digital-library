package edu.mbu.library.marks;

import edu.mbu.library.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MarkEntryRepository extends JpaRepository<MarkEntry, Long> {

    List<MarkEntry> findByAssessmentIn(Collection<Assessment> assessments);

    List<MarkEntry> findByAssessment(Assessment assessment);

    List<MarkEntry> findByStudent(User student);

    List<MarkEntry> findByStudentAndAssessmentIn(User student, Collection<Assessment> assessments);
}
