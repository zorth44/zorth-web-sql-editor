package com.bocsoft.sqleditor.engine;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/engines")
public class EngineCatalogController {
    private final EngineRegistry registry;
    public EngineCatalogController(EngineRegistry registry) { this.registry = registry; }

    @GetMapping
    public EngineCatalogResponse list() {
        return new EngineCatalogResponse(registry.descriptors());
    }
}
