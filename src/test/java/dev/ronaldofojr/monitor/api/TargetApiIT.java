package dev.ronaldofojr.monitor.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.ronaldofojr.monitor.IntegrationTestBase;
import dev.ronaldofojr.monitor.domain.Target;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class TargetApiIT extends IntegrationTestBase {

    private String json(String name, String url, String extra) {
        return "{\"name\":\"%s\",\"url\":\"%s\"%s}".formatted(name, url, extra);
    }

    private String create(String body) throws Exception {
        String response = mvc.perform(post("/targets").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    @Test
    void createsATargetApplyingTheDefaults() throws Exception {
        mvc.perform(post("/targets").contentType(MediaType.APPLICATION_JSON)
                        .content(json("minha-api", "https://exemplo.com/health", "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("GET"))
                .andExpect(jsonPath("$.intervalSeconds").value(60))
                .andExpect(jsonPath("$.timeoutMs").value(5000))
                .andExpect(jsonPath("$.expectedStatus").value(200))
                .andExpect(jsonPath("$.failureThreshold").value(3))
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void validationErrorsReturn400() throws Exception {
        mvc.perform(post("/targets").contentType(MediaType.APPLICATION_JSON).content(json("", "https://a.com", "")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/targets").contentType(MediaType.APPLICATION_JSON)
                        .content(json("x", "https://a.com", ",\"intervalSeconds\":5")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/targets").contentType(MediaType.APPLICATION_JSON)
                        .content(json("x", "https://a.com", ",\"failureThreshold\":0")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/targets").contentType(MediaType.APPLICATION_JSON)
                        .content(json("x", "https://a.com", ",\"method\":\"DELETE\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void businessRulesReturn422() throws Exception {
        mvc.perform(post("/targets").contentType(MediaType.APPLICATION_JSON).content(json("x", "ftp://a.com", "")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/targets").contentType(MediaType.APPLICATION_JSON)
                        .content(json("x", "https://a.com", ",\"method\":\"HEAD\",\"expectedBodyContains\":\"ok\"")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("HEAD nao tem corpo: nao da para conferir expectedBodyContains"));
    }

    @Test
    void duplicatedNameReturns409() throws Exception {
        create(json("duplicado", "https://a.com", ""));

        mvc.perform(post("/targets").contentType(MediaType.APPLICATION_JSON).content(json("duplicado", "https://b.com", "")))
                .andExpect(status().isConflict());
    }

    @Test
    void listsTargetsByNameAndGetsOneById() throws Exception {
        create(json("b-servico", "https://b.com", ""));
        String idA = create(json("a-servico", "https://a.com", ""));

        mvc.perform(get("/targets"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("a-servico"));
        mvc.perform(get("/targets/" + idA)).andExpect(status().isOk()).andExpect(jsonPath("$.url").value("https://a.com"));
        mvc.perform(get("/targets/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void updatesSettingsRenamesAndCanDeactivate() throws Exception {
        String id = create(json("antigo", "https://a.com", ""));

        mvc.perform(put("/targets/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json("novo", "https://novo.com", ",\"intervalSeconds\":30,\"active\":false")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("novo"))
                .andExpect(jsonPath("$.intervalSeconds").value(30))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void renamingToAnExistingNameReturns409ButKeepingYourOwnNameIsFine() throws Exception {
        create(json("um", "https://a.com", ""));
        String two = create(json("dois", "https://b.com", ""));

        mvc.perform(put("/targets/" + two).contentType(MediaType.APPLICATION_JSON).content(json("um", "https://b.com", "")))
                .andExpect(status().isConflict());
        mvc.perform(put("/targets/" + two).contentType(MediaType.APPLICATION_JSON).content(json("dois", "https://c.com", "")))
                .andExpect(status().isOk());
    }

    @Test
    void deleteRemovesTheTargetItsHistoryAndItsAlerts() throws Exception {
        Target target = newTarget("para-apagar", 1);
        TARGET.status = 500;
        mvc.perform(post("/targets/" + target.getId() + "/check")).andExpect(status().isOk());
        assertThat(count("check_results")).isEqualTo(1);
        assertThat(count("alerts")).isEqualTo(1);

        mvc.perform(delete("/targets/" + target.getId())).andExpect(status().isNoContent());

        assertThat(count("targets")).isZero();
        assertThat(count("check_results")).isZero();
        assertThat(count("alerts")).isZero();
        mvc.perform(delete("/targets/" + target.getId())).andExpect(status().isNotFound());
    }

    @Test
    void manualCheckReturnsTheResultAndUpdatesTheTarget() throws Exception {
        Target target = newTarget("manual", 3);

        mvc.perform(post("/targets/" + target.getId() + "/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.httpStatus").value(200));
        mvc.perform(get("/targets/" + target.getId())).andExpect(jsonPath("$.status").value("UP"));

        TARGET.status = 500;
        mvc.perform(post("/targets/" + target.getId() + "/check"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Status 500 (esperado 200)"));
        mvc.perform(post("/targets/" + UUID.randomUUID() + "/check")).andExpect(status().isNotFound());
    }

    @Test
    void historyIsPaginatedNewestFirstAndCanShowOnlyFailures() throws Exception {
        Target target = newTarget("historico", 10);
        mvc.perform(post("/targets/" + target.getId() + "/check"));
        TARGET.status = 500;
        mvc.perform(post("/targets/" + target.getId() + "/check"));
        mvc.perform(post("/targets/" + target.getId() + "/check"));
        TARGET.status = 200;
        mvc.perform(post("/targets/" + target.getId() + "/check"));

        mvc.perform(get("/targets/" + target.getId() + "/history"))
                .andExpect(jsonPath("$.content.length()").value(4))
                .andExpect(jsonPath("$.content[0].success").value(true))
                .andExpect(jsonPath("$.page.totalElements").value(4));
        mvc.perform(get("/targets/" + target.getId() + "/history?size=3&page=1"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(2));
        mvc.perform(get("/targets/" + target.getId() + "/history?failuresOnly=true"))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].httpStatus").value(500));
        mvc.perform(get("/targets/" + UUID.randomUUID() + "/history")).andExpect(status().isNotFound());
    }

    @Test
    void statsComputeUptimeAndLatencyOnlyFromSuccessfulChecks() throws Exception {
        Target target = newTarget("estatisticas", 10);
        Instant now = Instant.now();
        insert(target, now.minusSeconds(60), true, 100);
        insert(target, now.minusSeconds(50), true, 200);
        insert(target, now.minusSeconds(40), true, 300);
        insert(target, now.minusSeconds(30), false, 5000); // timeout: nao entra na latencia
        insert(target, now.minusSeconds(3600 * 30L), true, 9999); // fora da janela de 24h

        mvc.perform(get("/targets/" + target.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hours").value(24))
                .andExpect(jsonPath("$.totalChecks").value(4))
                .andExpect(jsonPath("$.successfulChecks").value(3))
                .andExpect(jsonPath("$.uptimePercent").value(75.0))
                .andExpect(jsonPath("$.avgLatencyMs").value(200.0))
                .andExpect(jsonPath("$.p95LatencyMs").value(290.0));
        mvc.perform(get("/targets/" + target.getId() + "/stats?hours=48"))
                .andExpect(jsonPath("$.totalChecks").value(5));
    }

    @Test
    void statsWithoutDataReturnNullsAndInvalidWindowReturns422() throws Exception {
        Target target = newTarget("sem-dados", 3);

        mvc.perform(get("/targets/" + target.getId() + "/stats"))
                .andExpect(jsonPath("$.totalChecks").value(0))
                .andExpect(jsonPath("$.uptimePercent").isEmpty())
                .andExpect(jsonPath("$.avgLatencyMs").isEmpty());
        mvc.perform(get("/targets/" + target.getId() + "/stats?hours=0")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/targets/" + target.getId() + "/stats?hours=100000")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void alertsCanBeListedGloballyOrPerTarget() throws Exception {
        Target one = newTarget("alerta-um", 1);
        Target two = newTarget("alerta-dois", 1);
        TARGET.status = 500;
        mvc.perform(post("/targets/" + one.getId() + "/check"));
        mvc.perform(post("/targets/" + two.getId() + "/check"));

        mvc.perform(get("/alerts"))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].type").value("DOWN"))
                .andExpect(jsonPath("$.content[0].notifiedAt").isNotEmpty());
        mvc.perform(get("/alerts?targetId=" + one.getId()))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].targetId").value(one.getId().toString()));
        mvc.perform(get("/alerts?targetId=" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    private void insert(Target target, Instant at, boolean success, long latency) {
        jdbc.update("INSERT INTO check_results (id, target_id, checked_at, success, http_status, latency_ms) "
                        + "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?)",
                target.getId(), Timestamp.from(at), success, success ? 200 : null, latency);
    }
}
