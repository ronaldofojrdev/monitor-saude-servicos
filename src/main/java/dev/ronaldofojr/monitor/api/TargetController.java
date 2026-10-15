package dev.ronaldofojr.monitor.api;

import dev.ronaldofojr.monitor.api.Dtos.AlertResponse;
import dev.ronaldofojr.monitor.api.Dtos.CheckResultResponse;
import dev.ronaldofojr.monitor.api.Dtos.StatsResponse;
import dev.ronaldofojr.monitor.api.Dtos.TargetRequest;
import dev.ronaldofojr.monitor.api.Dtos.TargetResponse;
import dev.ronaldofojr.monitor.service.TargetService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TargetController {

    private final TargetService service;

    public TargetController(TargetService service) {
        this.service = service;
    }

    @PostMapping("/targets")
    public ResponseEntity<TargetResponse> create(@Valid @RequestBody TargetRequest request) {
        TargetResponse body = TargetResponse.from(service.create(request.toSettings()));
        return ResponseEntity.created(URI.create("/targets/" + body.id())).body(body);
    }

    @GetMapping("/targets")
    public List<TargetResponse> list() {
        return service.list().stream().map(TargetResponse::from).toList();
    }

    @GetMapping("/targets/{id}")
    public TargetResponse get(@PathVariable UUID id) {
        return TargetResponse.from(service.get(id));
    }

    @PutMapping("/targets/{id}")
    public TargetResponse update(@PathVariable UUID id, @Valid @RequestBody TargetRequest request) {
        return TargetResponse.from(service.update(id, request.toSettings(), request.activeOrDefault()));
    }

    @PostMapping("/targets/{id}/pause")
    public TargetResponse pause(@PathVariable UUID id) {
        return TargetResponse.from(service.pause(id));
    }

    @PostMapping("/targets/{id}/resume")
    public TargetResponse resume(@PathVariable UUID id) {
        return TargetResponse.from(service.resume(id));
    }

    @DeleteMapping("/targets/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Dispara uma checagem agora e devolve o resultado. */
    @PostMapping("/targets/{id}/check")
    public CheckResultResponse checkNow(@PathVariable UUID id) {
        return CheckResultResponse.from(service.checkNow(id));
    }

    @GetMapping("/targets/{id}/history")
    public Page<CheckResultResponse> history(@PathVariable UUID id,
                                             @RequestParam(defaultValue = "false") boolean failuresOnly,
                                             @PageableDefault(size = 50) Pageable pageable) {
        return service.history(id, failuresOnly, pageable).map(CheckResultResponse::from);
    }

    /** Uptime e latencia (media e p95, so das checagens bem-sucedidas) nas ultimas N horas. */
    @GetMapping("/targets/{id}/stats")
    public StatsResponse stats(@PathVariable UUID id, @RequestParam(defaultValue = "24") int hours) {
        return StatsResponse.from(service.stats(id, hours));
    }

    @GetMapping("/alerts")
    public Page<AlertResponse> alerts(@RequestParam(required = false) UUID targetId,
                                      @PageableDefault(size = 20) Pageable pageable) {
        return service.alerts(targetId, pageable).map(AlertResponse::from);
    }
}
