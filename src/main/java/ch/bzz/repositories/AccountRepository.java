package ch.bzz.repositories;

import ch.bzz.classes.Account;
import ch.bzz.classes.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByProject(Project project);
}
