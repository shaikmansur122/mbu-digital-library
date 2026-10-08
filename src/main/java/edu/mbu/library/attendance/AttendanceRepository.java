package edu.mbu.library.attendance;

import edu.mbu.library.modules.Subject;
import edu.mbu.library.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findBySubjectAndClassDate(Subject subject, LocalDate classDate);

    List<AttendanceRecord> findBySubject(Subject subject);

    List<AttendanceRecord> findByStudentAndSubjectInOrderByClassDateDesc(User student, Collection<Subject> subjects);

    List<AttendanceRecord> findByStudent(User student);

    /** Number of different days on which this subject's attendance was taken. */
    @Query("select count(distinct a.classDate) from AttendanceRecord a where a.subject = :subject")
    long countClassDays(@Param("subject") Subject subject);

    /** The most recent days attendance was taken for a subject, newest first. */
    @Query("select distinct a.classDate from AttendanceRecord a where a.subject = :subject order by a.classDate desc")
    List<LocalDate> findClassDays(@Param("subject") Subject subject);
}
