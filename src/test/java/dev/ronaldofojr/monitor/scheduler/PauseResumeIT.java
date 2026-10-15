package dev.ronaldofojr.monitor.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ronaldofojr.monitor.IntegrationTestBase;
import dev.ronaldofojr.monitor.domain.Target;
import dev.ronaldofojr.monitor.domain.TargetStatus;
import dev.ronaldofojr.monitor.repository.TargetRepository;
import dev.ronaldofojr.monitor.service.CheckService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PauseResumeIT extends IntegrationTestBase {

    @Autowired
    CheckDispatcher dispatcher;
    @Autowired
    CheckService checkService;
    @Autowired
    TargetRepository targets;

    private void makeDue(Target target) {
        jdbc.update("UPDATE targets SET next_check_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(5)), target.getId());
    }

    private int resultsOf(Target target) {
        return jdbc.queryForObject("SELECT count(*) FROM check_results WHERE target_id = ?", Integer.class, target.getId());
    }

    @Test
    void pausedTargetIsNotCheckedEvenWhenItsCheckIsDue() throws Exception {
        Target target = newTarget("pausado", 3);
        makeDue(target);

        mvc.perform(post("/targets/" + target.getId() + "/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(dispatcher.tick()).isZero();
        Thread.sleep(300);
        assertThat(resultsOf(target)).isZero();
        assertThat(TARGET.hits.get()).isZero();
    }

    @Test
    void resumedTargetIsCheckedOnTheNextTickWithoutWaitingForItsInterval() throws Exception {
        Target target = newTarget("retomado", 3); // intervalo de 10 minutos
        checkService.runCheck(target.getId());
        jdbc.update("UPDATE targets SET next_check_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().plus(Duration.ofMinutes(9)).plusSeconds(30)), target.getId());
        mvc.perform(post("/targets/" + target.getId() + "/pause")).andExpect(status().isOk());
        assertThat(dispatcher.tick()).isZero();

        mvc.perform(post("/targets/" + target.getId() + "/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        assertThat(dispatcher.tick()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(10)).until(() -> resultsOf(target) == 2);
    }

    @Test
    void pausingAndResumingTwiceIsNotAnError() throws Exception {
        Target target = newTarget("repetido", 3);

        mvc.perform(post("/targets/" + target.getId() + "/pause")).andExpect(status().isOk());
        mvc.perform(post("/targets/" + target.getId() + "/pause"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(post("/targets/" + target.getId() + "/resume")).andExpect(status().isOk());
        mvc.perform(post("/targets/" + target.getId() + "/resume"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void pausingKeepsTheStatusTheHistoryAndTheAlerts() throws Exception {
        Target target = newTarget("preserva", 1);
        TARGET.status = 500;
        checkService.runCheck(target.getId()); // DOWN, 1 resultado, 1 alerta

        mvc.perform(post("/targets/" + target.getId() + "/pause"))
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.consecutiveFailures").value(1));

        assertThat(targets.findById(target.getId()).orElseThrow().getStatus()).isEqualTo(TargetStatus.DOWN);
        assertThat(count("check_results")).isEqualTo(1);
        assertThat(count("alerts")).isEqualTo(1);
    }

    @Test
    void aPausedTargetDoesNotCountAsActiveInTheMonitorHealth() throws Exception {
        Target paused = newTarget("fora-da-contagem", 3);
        newTarget("ativo", 3);

        mvc.perform(post("/targets/" + paused.getId() + "/pause")).andExpect(status().isOk());

        mvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components.monitor.details.activeTargets").value(1));
    }

    @Test
    void pausingAndResumingAnUnknownTargetReturns404() throws Exception {
        mvc.perform(post("/targets/" + UUID.randomUUID() + "/pause")).andExpect(status().isNotFound());
        mvc.perform(post("/targets/" + UUID.randomUUID() + "/resume")).andExpect(status().isNotFound());
    }

    @Test
    void settingActiveToFalseByUpdateHasTheSameEffectAsPausing() throws Exception {
        Target target = newTarget("via-put", 3);
        makeDue(target);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/targets/" + target.getId())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"via-put\",\"url\":\"%s\",\"active\":false}".formatted(TARGET.url("/health"))))
                .andExpect(status().isOk());

        assertThat(dispatcher.tick()).isZero();
    }
}
