package dev.ronaldofojr.monitor.status;

import dev.ronaldofojr.monitor.status.StatusService.StatusPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

    private final StatusService service;

    public StatusController(StatusService service) {
        this.service = service;
    }

    /** Situacao geral e de cada servico ativo, no formato que uma pagina de status consome. */
    @GetMapping("/status")
    public StatusPage status() {
        return service.current();
    }
}
