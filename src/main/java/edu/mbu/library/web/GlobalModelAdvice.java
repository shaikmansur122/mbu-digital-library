package edu.mbu.library.web;

import edu.mbu.library.modules.Subject;
import edu.mbu.library.modules.SubjectRepository;
import edu.mbu.library.user.Role;
import edu.mbu.library.user.User;
import edu.mbu.library.user.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.stream.Collectors;

/** Makes the logged-in user available to every page as "me" (used by the top bar and ID card). */
@ControllerAdvice
public class GlobalModelAdvice {

    private final UserRepository users;
    private final SubjectRepository subjects;

    public GlobalModelAdvice(UserRepository users, SubjectRepository subjects) {
        this.users = users;
        this.subjects = subjects;
    }

    @ModelAttribute("me")
    public User me(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return null;
        }
        return users.findByUsername(authentication.getName()).orElse(null);
    }

    /** The "Subject" line of the ID card: a student's subjects this semester, or the subjects a faculty teaches. */
    @ModelAttribute("mySubjects")
    public String mySubjects(Authentication authentication) {
        User me = me(authentication);
        if (me == null) {
            return null;
        }
        List<Subject> list = null;
        if (me.getRole() == Role.STUDENT && me.getSemester() != null) {
            list = subjects.findBySemesterOrderByNameAsc(me.getSemester());
        } else if (me.getRole() == Role.FACULTY) {
            list = subjects.findByFacultyOrderBySemesterAscNameAsc(me);
        }
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.stream().map(Subject::getName).collect(Collectors.joining(", "));
    }
}
