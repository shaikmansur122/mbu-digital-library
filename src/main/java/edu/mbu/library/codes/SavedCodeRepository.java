package edu.mbu.library.codes;

import edu.mbu.library.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavedCodeRepository extends JpaRepository<SavedCode, Long> {

    List<SavedCode> findByOwnerOrderByUpdatedAtDesc(User owner);

    List<SavedCode> findByOwner(User owner);

    long countByOwner(User owner);
}
