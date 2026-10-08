package edu.mbu.library.web;

import edu.mbu.library.user.User;
import edu.mbu.library.user.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Makes the logged-in user available to every page as "me" (used by the top bar and ID card). */
@ControllerAdvice
public class GlobalModelAdvice {

    private final UserRepository users;

    public GlobalModelAdvice(UserRepository users) {
        this.users = users;
    }

    @ModelAttribute("me")
    public User me(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return null;
        }
        return users.findByUsername(authentication.getName()).orElse(null);
    }
}
