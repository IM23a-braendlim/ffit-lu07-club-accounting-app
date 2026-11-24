package ch.bzz.controller;

import ch.bzz.classes.JwtUtil;
import ch.bzz.classes.Project;
import ch.bzz.generated.api.ProjectApi;
import ch.bzz.generated.model.LoginProject200Response;
import ch.bzz.generated.model.LoginRequest;
import ch.bzz.repositories.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectApiController implements ProjectApi {
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
        Project project = projectRepository.findByProjectName(loginRequest.getProjectName())
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        if (!encoder.matches(loginRequest.getPassword(), project.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = jwtUtil.generateToken(project.getProjectName());

        LoginProject200Response response = new LoginProject200Response();
        response.setAccessToken(token);

        return ResponseEntity.ok(response);
    }
}
