package ch.bzz.repositories;

import ch.bzz.classes.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, String> {
    Boolean existsByProjectName(String projectName);
    Optional<Project> findByProjectName(String projectName);
}
