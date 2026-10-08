package edu.mbu.library.labs;

import edu.mbu.library.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LabSubmissionRepository extends JpaRepository<LabSubmission, Long> {

    Optional<LabSubmission> findByExperimentAndStudent(LabExperiment experiment, User student);

    List<LabSubmission> findByExperiment(LabExperiment experiment);

    List<LabSubmission> findByStudentAndExperimentIn(User student, Collection<LabExperiment> experiments);

    long countByExperiment(LabExperiment experiment);

    void deleteByStudent(User student);
}
