package edu.mbu.library.library;

import edu.mbu.library.modules.Subject;
import edu.mbu.library.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LibraryItemRepository extends JpaRepository<LibraryItem, Long> {

    List<LibraryItem> findByKindOrderByCreatedAtDesc(ItemKind kind);

    List<LibraryItem> findBySubject(Subject subject);

    List<LibraryItem> findByPostedBy(User user);
}
