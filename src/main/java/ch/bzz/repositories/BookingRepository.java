package ch.bzz.repositories;

import ch.bzz.classes.Booking;
import ch.bzz.classes.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByProject(Project project);
}
