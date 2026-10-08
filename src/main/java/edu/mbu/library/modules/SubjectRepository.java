package edu.mbu.library.modules;

import edu.mbu.library.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findBySemesterOrderByNameAsc(int semester);

    List<Subject> findByFacultyOrderBySemesterAscNameAsc(User faculty);

    boolean existsByCodeIgnoreCase(String code);

    long countByFaculty(User faculty);
}
