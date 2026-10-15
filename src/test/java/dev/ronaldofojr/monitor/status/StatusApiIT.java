package dev.ronaldofojr.monitor.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ronaldofojr.monitor.IntegrationTestBase;
import dev.ronaldofojr.monitor.domain.Target;
import dev.ronaldofojr.monitor.service.CheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class StatusApiIT extends IntegrationTestBase {

    @Autowired
    CheckService checkService;

    @Test
    void withoutTargetsTheStatusIsNoData() throws Exception {
        mvc.perform(get("/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_DATA"))
                .andExpect(jsonPath("$.services.length()").value(0))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void allServicesUpIsOperational() throws Exception {
        checkService.runCheck(newTarget("api", 1).getId());
        checkService.runCheck(newTarget("site", 1).getId());

        mvc.perform(get("/status"))
                .andExpect(jsonPath("$.status").value("OPERATIONAL"))
                .andExpect(jsonPath("$.services.length()").value(2))
                .andExpect(jsonPath("$.services[0].name").value("api"))
                .andExpect(jsonPath("$.services[0].status").value("UP"));
    }

    @Test
    void oneServiceDownMakesTheOverallStatusDegraded() throws Exception {
        Target healthy = newTarget("a-saudavel", 1);
        checkService.runCheck(healthy.getId());
        Target broken = newTarget("b-quebrado", 1);
        // o alvo quebrado aponta para um caminho que o servidor de teste responde com 500
        jdbc.update("UPDATE targets SET expected_status = 201 WHERE id = ?", broken.getId());
        checkService.runCheck(broken.getId());

        mvc.perform(get("/status"))
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.services[1].status").value("DOWN"));
    }

    @Test
    void allServicesDownIsMajorOutage() throws Exception {
        TARGET.status = 503;
        checkService.runCheck(newTarget("um", 1).getId());
        checkService.runCheck(newTarget("dois", 1).getId());

        mvc.perform(get("/status")).andExpect(jsonPath("$.status").value("MAJOR_OUTAGE"));
    }

    @Test
    void pausedTargetsDoNotAppearNorAffectTheOverallStatus() throws Exception {
        Target healthy = newTarget("ativo", 1);
        checkService.runCheck(healthy.getId());
        TARGET.status = 500;
        Target broken = newTarget("pausado-e-quebrado", 1);
        checkService.runCheck(broken.getId());
        mvc.perform(post("/targets/" + broken.getId() + "/pause")).andExpect(status().isOk());

        mvc.perform(get("/status"))
                .andExpect(jsonPath("$.status").value("OPERATIONAL"))
                .andExpect(jsonPath("$.services.length()").value(1))
                .andExpect(jsonPath("$.services[0].name").value("ativo"));
    }

    @Test
    void neverCheckedTargetsAppearAsUnknownWithoutUptime() throws Exception {
        newTarget("novo", 3);

        mvc.perform(get("/status"))
                .andExpect(jsonPath("$.status").value("NO_DATA"))
                .andExpect(jsonPath("$.services[0].status").value("UNKNOWN"))
                .andExpect(jsonPath("$.services[0].uptime24h").isEmpty());
    }

    @Test
    void uptimeIsTheShareOfSuccessfulChecksInTheLast24Hours() throws Exception {
        Target target = newTarget("uptime", 10);
        checkService.runCheck(target.getId());
        TARGET.status = 500;
        checkService.runCheck(target.getId());
        checkService.runCheck(target.getId());
        TARGET.status = 200;
        checkService.runCheck(target.getId());

        mvc.perform(get("/status")).andExpect(jsonPath("$.services[0].uptime24h").value(50.0));
    }

    @Test
    void thePublicStatusNeverExposesTheMonitoredUrls() throws Exception {
        checkService.runCheck(newTarget("interno", 1).getId());

        String body = mvc.perform(get("/status")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("127.0.0.1").doesNotContain("http://").doesNotContain("url");
    }

    @Test
    void theStatusPageIsServedAsStaticHtml() throws Exception {
        mvc.perform(get("/status.html"))
                .andExpect(status().isOk());
    }
}
