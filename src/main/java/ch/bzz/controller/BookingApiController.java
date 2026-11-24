package ch.bzz.controller;

import ch.bzz.classes.BookingEntity;
import ch.bzz.classes.JwtUtil;
import ch.bzz.classes.Project;
import ch.bzz.generated.api.BookingApi;
import ch.bzz.generated.model.Booking;
import ch.bzz.generated.model.BookingUpdate;
import ch.bzz.generated.model.UpdateBookingsRequest;
import ch.bzz.repositories.BookingRepository;
import ch.bzz.repositories.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
public class BookingApiController implements BookingApi {
    private final BookingRepository bookingRepository;
    private final ProjectRepository projectRepository;
    private final JwtUtil jwtUtil;
    private final HttpServletRequest request;

    public BookingApiController(BookingRepository bookingRepository,
                                ProjectRepository projectRepository,
                                JwtUtil jwtUtil,
                                HttpServletRequest request) {
        this.bookingRepository = bookingRepository;
        this.projectRepository = projectRepository;
        this.jwtUtil = jwtUtil;
        this.request = request;
    }

    @Override
    public ResponseEntity<List<Booking>> getBookings() {
        try {
            String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Kein oder ungültiger Authorization-Header für Bookings gefunden.");
                return ResponseEntity.status(401).build();
            }

            String token = authHeader.substring(7);
            String projectName = jwtUtil.extractProjectName(token);
            log.info("Projektname für Bookings aus JWT extrahiert: {}", projectName);

            Project project = projectRepository.findByProjectName(projectName)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectName));

            List<BookingEntity> entityBookings = bookingRepository.findByProject(project);

            List<Booking> apiBookings = entityBookings.stream()
                    .map(entity -> {
                        return new Booking();
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(apiBookings);
        } catch (Exception e) {
            log.error("Fehler beim Abrufen der Bookings: ", e);
            return ResponseEntity.status(401).build();
        }
    }

    @Override
    public ResponseEntity<Void> updateBookings(UpdateBookingsRequest updateBookingsRequest) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401).build();
            }

            String token = authHeader.substring(7);
            String projectName = jwtUtil.extractProjectName(token);

            Project project = projectRepository.findByProjectName(projectName)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectName));

            List<BookingUpdate> incomingBookings = updateBookingsRequest.getEntries();
            if (incomingBookings == null) {
                return ResponseEntity.badRequest().build();
            }

            List<BookingEntity> existingEntities = bookingRepository.findByProject(project);
            bookingRepository.deleteAll(existingEntities);

            List<BookingEntity> newEntities = incomingBookings.stream()
                    .map(incoming -> {
                        BookingEntity entity = new BookingEntity();
                        entity.setProject(project);
                        return entity;
                    })
                    .collect(Collectors.toList());

            bookingRepository.saveAll(newEntities);

            log.info("Bookings erfolgreich aktualisiert für Projekt: {}", projectName);
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            log.error("Fehler beim Aktualisieren der Bookings: ", e);
            return ResponseEntity.status(500).build();
        }
    }
}
