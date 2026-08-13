package com.bocsoft.sqleditor.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/session")
public class SessionController {
    @GetMapping
    public SessionResponse current() {
        return new SessionResponse(CurrentAuth.get());
    }
}
