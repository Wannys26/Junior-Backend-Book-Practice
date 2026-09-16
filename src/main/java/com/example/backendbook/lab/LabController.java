package com.example.backendbook.lab;

import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("connection-leak")
@RequestMapping("/lab")
@RequiredArgsConstructor
public class LabController {
    private final LabService labService;

    @GetMapping("/connection")
    public ConnectionResult checkConnection() throws SQLException {
        return labService.checkConnection();
    }
}
