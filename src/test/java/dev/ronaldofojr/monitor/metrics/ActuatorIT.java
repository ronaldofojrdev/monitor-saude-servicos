package dev.ronaldofojr.monitor.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ronaldofojr.monitor.IntegrationTestBase;
import dev.ronaldofojr.monitor.domain.Target;
import org.junit.jupiter.api.Test;

class ActuatorIT extends IntegrationTestBase {

    private String prometheus() throws Exception {
        return mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void check(Target target) throws Exception {
        mvc.perform(post("/targets/" + target.getId() + "/check")).andExpect(status().isOk());
    }

    @Test
    void healthIncludesTheMonitorComponentAndTheDatabase() throws Exception {
        newTarget("saude", 3);

        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.monitor.status").value("UP"))
                .andExpect(jsonPath("$.components.monitor.details.activeTargets").value(1))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    void targetsDownDoNotMakeTheMonitorItselfUnhealthy() throws Exception {
        Target target = newTarget("caido", 1);
        TARGET.status = 500;
        check(target);

        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.monitor.details.targetsDown").value(1));
    }

    @Test
    void prometheusExposesUpGaugeCheckCountersAndLatencyPerTarget() throws Exception {
        Target target = newTarget("metrica-a", 1);
        check(target);

        String body = prometheus();
        assertThat(body).contains("monitor_target_up{application=\"monitor-saude-servicos\",target=\"metrica-a\"} 1.0");
        assertThat(body).contains("monitor_checks_total{application=\"monitor-saude-servicos\",result=\"success\",target=\"metrica-a\"} 1.0");
        assertThat(body).contains("monitor_check_latency_seconds_count{application=\"monitor-saude-servicos\",target=\"metrica-a\"} 1");
    }

    @Test
    void gaugeGoesToZeroWhenTheTargetIsDownAndFailuresAreCountedSeparately() throws Exception {
        Target target = newTarget("metrica-b", 1);
        TARGET.status = 500;
        check(target);

        String body = prometheus();
        assertThat(body).contains("monitor_target_up{application=\"monitor-saude-servicos\",target=\"metrica-b\"} 0.0");
        assertThat(body).contains("result=\"failure\",target=\"metrica-b\"} 1.0");
        assertThat(body).as("falha nao entra na latencia").doesNotContain("monitor_check_latency_seconds_count{application=\"monitor-saude-servicos\",target=\"metrica-b\"}");
        assertThat(body).containsPattern("monitor_targets_down\\{[^}]*\\} [1-9]");
    }

    @Test
    void metricsOfADeletedTargetDisappear() throws Exception {
        Target target = newTarget("metrica-c", 1);
        check(target);
        assertThat(prometheus()).contains("target=\"metrica-c\"");

        mvc.perform(delete("/targets/" + target.getId())).andExpect(status().isNoContent());

        assertThat(prometheus()).doesNotContain("target=\"metrica-c\"");
    }

    @Test
    void metricsFollowARenamedTarget() throws Exception {
        Target target = newTarget("nome-velho", 1);
        check(target);

        targetService.update(target.getId(), settings("nome-novo", 1), true);

        String body = prometheus();
        assertThat(body).doesNotContain("target=\"nome-velho\"");
        assertThat(body).contains("monitor_target_up{application=\"monitor-saude-servicos\",target=\"nome-novo\"}");
    }
}
