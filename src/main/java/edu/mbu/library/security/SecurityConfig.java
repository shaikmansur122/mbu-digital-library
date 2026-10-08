package edu.mbu.library.security;

import edu.mbu.library.user.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/error", "/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // Only faculty may change module content (the controller also checks they own the subject).
                .requestMatchers(HttpMethod.POST, "/modules/**").hasRole("FACULTY")
                // Labs: faculty assign and delete, students submit, both can run code in the editor.
                .requestMatchers(HttpMethod.POST, "/labs/run").hasAnyRole("STUDENT", "FACULTY")
                .requestMatchers(HttpMethod.POST, "/labs/*/submit").hasRole("STUDENT")
                .requestMatchers(HttpMethod.POST, "/labs/**").hasRole("FACULTY")
                // Assignments: faculty post, grade and delete; students submit.
                .requestMatchers(HttpMethod.POST, "/assignments/*/submit").hasRole("STUDENT")
                .requestMatchers(HttpMethod.POST, "/assignments/**").hasRole("FACULTY")
                // Attendance: faculty take it and see reports; students only read their own.
                .requestMatchers(HttpMethod.POST, "/attendance/**").hasRole("FACULTY")
                .requestMatchers("/attendance/report", "/attendance/report.csv").hasRole("FACULTY")
                // Students and faculty share the same interface; what they can do inside is decided per page.
                .requestMatchers("/home", "/modules/**", "/labs/**", "/tools", "/resources", "/results", "/codes",
                        "/editor", "/attendance/**", "/marks", "/assignments/**", "/profile", "/people/**", "/files/**")
                    .hasAnyRole("STUDENT", "FACULTY")
                .requestMatchers("/").authenticated()
                .anyRequest().denyAll())
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(roleBasedSuccessHandler())
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Sends each role to its own dashboard after login. */
    @Bean
    AuthenticationSuccessHandler roleBasedSuccessHandler() {
        return (request, response, authentication) ->
                response.sendRedirect(request.getContextPath() + homeFor(authentication));
    }

    public static String homeFor(Authentication authentication) {
        for (Role role : Role.values()) {
            boolean has = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_" + role.name()));
            if (has) {
                return role.homePath();
            }
        }
        return "/login";
    }
}
