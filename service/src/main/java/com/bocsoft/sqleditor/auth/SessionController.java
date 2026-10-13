package com.bocsoft.sqleditor.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/session")
public class SessionController {
    private final com.bocsoft.sqleditor.optimization.OptimizationProperties optimization;
    public SessionController(com.bocsoft.sqleditor.optimization.OptimizationProperties optimization) { this.optimization = optimization; }
    @GetMapping
    public SessionResponse current() {
        AuthContext auth = CurrentAuth.get();
        return new SessionResponse(auth, optimization.permits(auth));
    }
}
