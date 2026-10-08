package edu.mbu.library.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    List<User> findAllByOrderByRoleAscFullNameAsc();

    List<User> findAllByRoleOrderByFullNameAsc(Role role);

    /** The class roster for a semester, used to show who has and has not submitted a lab. */
    List<User> findAllByRoleAndSemesterOrderByRollNoAscFullNameAsc(Role role, Integer semester);

    long countByRoleAndSemester(Role role, Integer semester);

    /** Used before deleting a faculty account so students are not left pointing at it. */
    @Modifying
    @Query("update User u set u.mentor = null where u.mentor = :mentor")
    void clearMentor(@Param("mentor") User mentor);
}
