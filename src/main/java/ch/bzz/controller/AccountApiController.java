package ch.bzz.controller;

import ch.bzz.classes.AccountEntity;
import ch.bzz.classes.JwtUtil;
import ch.bzz.classes.Project;
import ch.bzz.generated.api.AccountApi;
import ch.bzz.generated.model.Account;
import ch.bzz.generated.model.AccountUpdate;
import ch.bzz.generated.model.UpdateAccountsRequest;
import ch.bzz.repositories.AccountRepository;
import ch.bzz.repositories.ProjectRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RestController
public class AccountApiController implements AccountApi {
    private final AccountRepository accountRepository;
    private final ProjectRepository projectRepository;
    private final JwtUtil jwtUtil;
    private final HttpServletRequest request;

    public AccountApiController(AccountRepository accountRepository,
                                ProjectRepository projectRepository,
                                JwtUtil jwtUtil,
                                HttpServletRequest request) {
        this.accountRepository = accountRepository;
        this.projectRepository = projectRepository;
        this.jwtUtil = jwtUtil;
        this.request = request;
    }

    @Override
    public ResponseEntity<List<Account>> getAccounts() {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Kein oder ungültiger Authorization-Header gefunden.");
            return ResponseEntity.status(401).build();
        }

        String projectName;
        try {
            projectName = jwtUtil.extractProjectName(authHeader.substring(7));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Ungültiger oder abgelaufener Token.");
            return ResponseEntity.status(401).build();
        }
        log.info("Projektname aus JWT extrahiert: {}", projectName);

        Project project = projectRepository.findByProjectName(projectName).orElse(null);
        if (project == null) {
            log.warn("Projekt nicht gefunden: {}", projectName);
            return ResponseEntity.status(401).build();
        }

        List<AccountEntity> entityAccounts = accountRepository.findByProject(project);

        List<Account> apiAccounts = entityAccounts.stream()
                .map(entity -> {
                    Account account = new Account();
                    account.setNumber(entity.getAccountNumber());
                    account.setName(entity.getName());
                    return account;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(apiAccounts);
    }

    @Transactional
    @Override
    public ResponseEntity<Void> updateAccounts(UpdateAccountsRequest updateAccountsRequest) {
        // 1. Subject (ProjectName) aus dem JWT auslesen
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

        List<AccountUpdate> incoming = updateAccountsRequest.getAccounts();
        if (incoming == null) {
            return ResponseEntity.badRequest().build();
        }

        // 2. Entscheiden: neu, aktualisieren oder löschen
        Map<Integer, AccountEntity> existing = accountRepository.findByProject(project).stream()
                .collect(Collectors.toMap(AccountEntity::getAccountNumber, Function.identity()));

        List<AccountEntity> toSave = new ArrayList<>();   // neu + aktualisiert
        Set<Integer> seen = new HashSet<>();
        List<AccountEntity> toDelete = new ArrayList<>();

        for (AccountUpdate in : incoming) {
            if (!seen.add(in.getNumber())) {
                return ResponseEntity.badRequest().build();
            }

            AccountEntity entity = existing.remove(in.getNumber());
            boolean nameSent = in.getName() != null && in.getName().isPresent();
            String newName = nameSent ? in.getName().get() : null;

            // name explicitly null -> delete button
            if (nameSent && newName == null) {
                if (entity != null) {
                    toDelete.add(entity);
                }
                continue;                                      // never setName(null)
            }

            if (entity == null) {
                if (newName == null) {
                    return ResponseEntity.badRequest().build();
                }                entity = new AccountEntity();
                entity.setProject(project);
                entity.setAccountNumber(in.getNumber());
            }
            if (in.getName().isPresent()) {
                entity.setName(in.getName().get());
            }
            if (entity.getName() == null) {
                return ResponseEntity.badRequest().build();
            }
            toSave.add(entity);
        }

        log.info("Incoming={}, save={}, delete={}",
                incoming.stream().map(AccountUpdate::getNumber).toList(),
                toSave.stream().map(AccountEntity::getAccountNumber).toList(),
                toDelete.stream().map(AccountEntity::getAccountNumber).toList());

        accountRepository.saveAll(toSave);
        accountRepository.deleteAll(toDelete);

        log.info("Accounts für Projekt {}: {} gespeichert, {} gelöscht",
                projectName, toSave.size(), toDelete.size());
        return ResponseEntity.noContent().build();
    }
}
