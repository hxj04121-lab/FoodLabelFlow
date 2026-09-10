package com.spectrace.catalog.interfaces.web;

import com.spectrace.catalog.application.CatalogService;
import com.spectrace.catalog.domain.CatalogCommands.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.spectrace.catalog.infrastructure.CatalogStore.Kind.*;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final CatalogService service;
    public CatalogController(CatalogService service) { this.service = service; }

    @GetMapping("/suppliers")
    public List<Map<String, Object>> suppliers(@RequestParam(defaultValue="50") int limit, @RequestParam(defaultValue="0") int offset) {
        return service.list(SUPPLIER, limit, offset);
    }
    @GetMapping("/suppliers/{id}") public Map<String, Object> supplier(@PathVariable String id) { return service.get(SUPPLIER, id); }
    @PostMapping("/suppliers") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> supplier(@RequestBody Supplier body) { return service.create(body); }

    @GetMapping("/materials")
    public List<Map<String, Object>> materials(@RequestParam(defaultValue="50") int limit, @RequestParam(defaultValue="0") int offset) {
        return service.list(MATERIAL, limit, offset);
    }
    @GetMapping("/materials/{id}") public Map<String, Object> material(@PathVariable String id) { return service.get(MATERIAL, id); }
    @PostMapping("/materials") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> material(@RequestBody Material body) { return service.create(body); }

    @GetMapping("/specifications")
    public List<Map<String, Object>> specifications(@RequestParam(defaultValue="50") int limit, @RequestParam(defaultValue="0") int offset) {
        return service.list(SPECIFICATION, limit, offset);
    }
    @GetMapping("/specifications/{id}") public Map<String, Object> specification(@PathVariable String id) { return service.get(SPECIFICATION, id); }
    @PostMapping("/specifications") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> specification(@RequestBody Specification body) { return service.create(body); }
    @PostMapping("/specifications/{id}/release")
    public Map<String, Object> releaseSpecification(@PathVariable String id) { return service.releaseSpecification(id); }

    @GetMapping("/products")
    public List<Map<String, Object>> products(@RequestParam(defaultValue="50") int limit, @RequestParam(defaultValue="0") int offset) {
        return service.list(PRODUCT, limit, offset);
    }
    @GetMapping("/products/{id}") public Map<String, Object> product(@PathVariable String id) { return service.get(PRODUCT, id); }
    @GetMapping("/products/{id}/formulas")
    public List<Map<String, Object>> versions(@PathVariable String id) { return service.versions(id); }
    @GetMapping("/formulas/{id}") public Map<String, Object> formula(@PathVariable String id) { return service.get(FORMULA, id); }
    @GetMapping("/formulas/{id}/trace") public Map<String, Object> trace(@PathVariable String id) { return service.trace(id); }
    @PostMapping("/formulas") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> formula(@RequestBody Formula body) { return service.create(body); }
    @PostMapping("/formulas/{id}/release")
    public Map<String, Object> releaseFormula(@PathVariable String id, @RequestBody Release body) { return service.releaseFormula(id, body); }
}
