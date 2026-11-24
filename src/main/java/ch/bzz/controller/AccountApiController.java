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
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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
        try {
            String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Kein oder ungültiger Authorization-Header gefunden.");
                return ResponseEntity.status(401).build();
            }

            String token = authHeader.substring(7);

            String projectName = jwtUtil.extractProjectName(token);
            log.info("Projektname aus JWT extrahiert: {}", projectName);

            Project project = projectRepository.findByProjectName(projectName)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectName));

            List<AccountEntity> entityAccounts = accountRepository.findByProject(project);

            List<Account> apiAccounts = entityAccounts.stream()
                    .map(entity -> {
                        return new Account();
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(apiAccounts);
        } catch (Exception e) {
            log.error("Fehler beim Abrufen der Accounts über den JWT-Header: ", e);
            return ResponseEntity.status(401).build();
        }
    }


    @Override
    public ResponseEntity<Void> updateAccounts(UpdateAccountsRequest updateAccountsRequest) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401).build();
            }

            String token = authHeader.substring(7);
            String projectName = jwtUtil.extractProjectName(token);

            Project project = projectRepository.findByProjectName(projectName)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectName));

            List<AccountUpdate> incomingAccounts = updateAccountsRequest.getAccounts();
            if (incomingAccounts == null) {
                return ResponseEntity.badRequest().build();
            }

            List<AccountEntity> existingEntities = accountRepository.findByProject(project);
            accountRepository.deleteAll(existingEntities);

            List<AccountEntity> newEntities = incomingAccounts.stream()
                    .map(incoming -> {
                        AccountEntity entity = new AccountEntity();
                        entity.setProject(project);
                        return entity;
                    })
                    .collect(Collectors.toList());

            accountRepository.saveAll(newEntities);

            log.info("Accounts erfolgreich aktualisiert für Projekt: {}", projectName);
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            log.error("Fehler beim Aktualisieren der Accounts: ", e);
            return ResponseEntity.status(500).build();
        }
    }
}
