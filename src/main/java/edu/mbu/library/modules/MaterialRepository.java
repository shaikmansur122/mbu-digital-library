package edu.mbu.library.modules;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    List<Material> findByModuleInOrderByIdAsc(Collection<CourseModule> modules);

    List<Material> findByModule(CourseModule module);
}
