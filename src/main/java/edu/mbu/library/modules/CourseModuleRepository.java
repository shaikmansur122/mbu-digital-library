package edu.mbu.library.modules;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseModuleRepository extends JpaRepository<CourseModule, Long> {

    List<CourseModule> findBySubjectOrderByPositionAscIdAsc(Subject subject);

    long countBySubject(Subject subject);
}
