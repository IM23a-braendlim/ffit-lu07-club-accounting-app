package ch.bzz.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Slf4j
@RestController
@RequestMapping("/api/demo")
public class DemoController {
    @GetMapping("/logger")
    public ResponseEntity<String> logger() {
        try {
            log.info("Demo hello endpoint was called successfully.");
            return ResponseEntity.ok("Hello from Spring Boot Demo Controller!");
        } catch (Exception e) {
            log.error("Error occurred in DemoController: ", e);
            return ResponseEntity.status(500).body("Ein Fehler ist aufgetreten.");
        }
    }
}