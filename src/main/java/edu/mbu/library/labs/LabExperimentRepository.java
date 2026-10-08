package edu.mbu.library.labs;

import edu.mbu.library.modules.Subject;
import edu.mbu.library.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LabExperimentRepository extends JpaRepository<LabExperiment, Long> {

    List<LabExperiment> findBySubjectInOrderByCreatedAtDesc(Collection<Subject> subjects);

    List<LabExperiment> findBySubjectFacultyOrderByCreatedAtDesc(User faculty);

    List<LabExperiment> findBySubject(Subject subject);
}
