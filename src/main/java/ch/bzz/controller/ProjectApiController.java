package ch.bzz.controller;

import ch.bzz.classes.JwtUtil;
import ch.bzz.classes.Project;
import ch.bzz.generated.api.ProjectApi;
import ch.bzz.generated.model.LoginProject200Response;
import ch.bzz.generated.model.LoginRequest;
import ch.bzz.repositories.ProjectRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@RestController
public class ProjectApiController implements ProjectApi {
    @Value("${jwt.expiration-ms:3600000}")
    private long expirationMs;
    private final ProjectRepository projectRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtUtil jwtUtil;

    public ProjectApiController(ProjectRepository projectRepository, JwtUtil jwtUtil) {
        this.projectRepository = projectRepository;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public ResponseEntity<Void> createProject(LoginRequest loginRequest) {
        if (projectRepository.existsByProjectName(loginRequest.getProjectName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        Project project = new Project();
        project.setProjectName(loginRequest.getProjectName());
        project.setPasswordHash(encoder.encode(loginRequest.getPassword()));

        projectRepository.save(project);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<LoginProject200Response> loginProject(LoginRequest loginRequest) {
        Optional<Project> project = projectRepository
                .findByProjectName(loginRequest.getProjectName());

        if (project.isEmpty()
                || !encoder.matches(loginRequest.getPassword(), project.get().getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = jwtUtil.generateToken(project.get().getProjectName());

        LoginProject200Response body = new LoginProject200Response();
        body.setAccessToken(token);
        return ResponseEntity.ok(body);
    }
}
