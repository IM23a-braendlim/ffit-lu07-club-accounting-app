package ch.bzz.controller;

import ch.bzz.classes.AccountEntity;
import ch.bzz.classes.BookingEntity;
import ch.bzz.classes.JwtUtil;
import ch.bzz.classes.Project;
import ch.bzz.generated.api.BookingApi;
import ch.bzz.generated.model.Booking;
import ch.bzz.generated.model.BookingUpdate;
import ch.bzz.generated.model.UpdateBookingsRequest;
import ch.bzz.repositories.AccountRepository;
import ch.bzz.repositories.BookingRepository;
import ch.bzz.repositories.ProjectRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RestController
public class BookingApiController implements BookingApi {
    private final AccountRepository accountRepository;
    private final BookingRepository bookingRepository;
    private final ProjectRepository projectRepository;
    private final JwtUtil jwtUtil;
    private final HttpServletRequest request;

    public BookingApiController(AccountRepository accountRepository, BookingRepository bookingRepository,
                                ProjectRepository projectRepository,
                                JwtUtil jwtUtil,
                                HttpServletRequest request) {
        this.accountRepository = accountRepository;
        this.bookingRepository = bookingRepository;
        this.projectRepository = projectRepository;
        this.jwtUtil = jwtUtil;
        this.request = request;
    }

    @Override
    public ResponseEntity<List<Booking>> getBookings() {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Kein oder ungültiger Authorization-Header für Bookings gefunden.");
            return ResponseEntity.status(401).build();
        }

        String projectName;
        try {
            projectName = jwtUtil.extractProjectName(authHeader.substring(7));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Ungültiger oder abgelaufener Token.");
            return ResponseEntity.status(401).build();
        }
        log.info("Projektname für Bookings aus JWT extrahiert: {}", projectName);

        Project project = projectRepository.findByProjectName(projectName).orElse(null);
        if (project == null) {
            log.warn("Projekt nicht gefunden: {}", projectName);
            return ResponseEntity.status(401).build();
        }

        List<BookingEntity> entityBookings = bookingRepository.findByProject(project);

        List<Booking> apiBookings = entityBookings.stream()
                .map(entity -> {
                    Booking booking = new Booking();
                    booking.setNumber(Integer.valueOf(entity.getBookingNumber()));
                    booking.setDate(entity.getDate());
                    booking.setText(entity.getText());
                    booking.setDebit(entity.getDebitAccount().getAccountNumber());
                    booking.setCredit(entity.getCreditAccount().getAccountNumber());
                    booking.setAmount(entity.getAmount().floatValue());
                    log.info("Booking {} date={}", entity.getBookingNumber(), entity.getDate());
                    return booking;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(apiBookings);
    }

    private static <T> T val(JsonNullable<T> n) {
        return (n != null && n.isPresent()) ? n.get() : null;
    }

    @Override
    public ResponseEntity<Void> updateBookings(UpdateBookingsRequest updateBookingsRequest) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }

        String projectName;
        try {
            projectName = jwtUtil.extractProjectName(authHeader.substring(7));
        } catch (JwtException | IllegalArgumentException e) {
            return ResponseEntity.status(401).build();
        }

        Project project = projectRepository.findByProjectName(projectName).orElse(null);
        if (project == null) {
            return ResponseEntity.status(401).build();
        }

        List<BookingUpdate> incoming = updateBookingsRequest.getEntries();
        if (incoming == null) {
            return ResponseEntity.badRequest().build();
        }

        Map<Integer, AccountEntity> accounts = accountRepository.findByProject(project).stream()
                .collect(Collectors.toMap(AccountEntity::getAccountNumber, Function.identity()));

        Map<String, BookingEntity> existing = bookingRepository.findByProject(project).stream()
                .collect(Collectors.toMap(BookingEntity::getBookingNumber, Function.identity()));

        List<BookingEntity> toSave = new ArrayList<>();
        List<BookingEntity> toDelete = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (BookingUpdate in : incoming) {
            if (in.getId() == null) {
                return ResponseEntity.badRequest().build();
            }
            String number = String.valueOf(in.getId());
            if (!seen.add(number)) {
                return ResponseEntity.badRequest().build();   // duplicate id
            }

            LocalDate date = val(in.getDate());
            String text = val(in.getText());
            Integer debitNr = val(in.getDebit());
            Integer creditNr = val(in.getCredit());
            Float amountF = val(in.getAmount());

            BookingEntity entity = existing.get(number);

            // Only the id is sent -> delete button
            boolean onlyId = date == null && debitNr == null && creditNr == null && amountF == null;
            if (onlyId) {
                if (entity != null) {
                    toDelete.add(entity);
                }
                continue;
            }

            if (entity == null) {
                // new booking: everything required must be there
                if (date == null || debitNr == null || creditNr == null || amountF == null) {
                    return ResponseEntity.badRequest().build();
                }
                entity = new BookingEntity();
                entity.setProject(project);
                entity.setBookingNumber(number);
            }

            if (date != null) entity.setDate(date);
            if (text != null) entity.setText(text);
            if (amountF != null) entity.setAmount(BigDecimal.valueOf(amountF).setScale(2, RoundingMode.HALF_UP));
            if (debitNr != null) {
                AccountEntity debit = accounts.get(debitNr);
                if (debit == null) return ResponseEntity.badRequest().build();   // unknown account
                entity.setDebitAccount(debit);
            }
            if (creditNr != null) {
                AccountEntity credit = accounts.get(creditNr);
                if (credit == null) return ResponseEntity.badRequest().build();
                entity.setCreditAccount(credit);
            }

            toSave.add(entity);
        }

        log.info("Bookings: Incoming={}, save={}, delete={}",
                seen, toSave.size(), toDelete.stream().map(BookingEntity::getBookingNumber).toList());

        bookingRepository.saveAll(toSave);
        bookingRepository.deleteAll(toDelete);

        return ResponseEntity.noContent().build();
    }
}
