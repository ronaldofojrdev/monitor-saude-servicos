package dev.ronaldofojr.monitor.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.ronaldofojr.monitor.IntegrationTestBase;
import dev.ronaldofojr.monitor.domain.Target;
import dev.ronaldofojr.monitor.repository.TargetRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CheckDispatcherIT extends IntegrationTestBase {

    @Autowired
    CheckDispatcher dispatcher;
    @Autowired
    TargetRepository targets;
    @Autowired
    SchedulerHeartbeat heartbeat;

    private void makeDue(Target target) {
        jdbc.update("UPDATE targets SET next_check_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(5)), target.getId());
    }

    private void makeNotDue(Target target) {
        jdbc.update("UPDATE targets SET next_check_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().plus(Duration.ofHours(1))), target.getId());
    }

    private int resultsOf(Target target) {
        return jdbc.queryForObject("SELECT count(*) FROM check_results WHERE target_id = ?", Integer.class, target.getId());
    }

    @Test
    void newTargetsAreCheckedOnTheFirstTick() {
        Target target = newTarget("recem-criado", 3);

        assertThat(dispatcher.tick()).isEqualTo(1);

        await().atMost(Duration.ofSeconds(10)).until(() -> resultsOf(target) == 1);
    }

    @Test
    void tickOnlyChecksDueActiveTargetsAndSchedulesTheNextCheck() {
        Target due = newTarget("vencido", 3);
        Target notDue = newTarget("no-futuro", 3);
        Target inactive = newTarget("inativo", 3);
        makeDue(due);
        makeNotDue(notDue);
        jdbc.update("UPDATE targets SET active = FALSE, next_check_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(5)), inactive.getId());

        assertThat(dispatcher.tick()).isEqualTo(1);

        await().atMost(Duration.ofSeconds(10)).until(() -> resultsOf(due) == 1);
        assertThat(resultsOf(notDue)).isZero();
        assertThat(resultsOf(inactive)).isZero();
        Instant next = targets.findById(due.getId()).orElseThrow().getNextCheckAt();
        assertThat(next).isAfter(Instant.now().plusSeconds(500)); // intervalo de 600s
        assertThat(heartbeat.lastTick()).isNotNull();
    }

    @Test
    void secondTickRightAfterDoesNotCheckTheSameTargetAgain() {
        Target target = newTarget("sem-duplicar", 3);
        makeDue(target);

        assertThat(dispatcher.tick()).isEqualTo(1);
        assertThat(dispatcher.tick()).isZero();

        await().atMost(Duration.ofSeconds(10)).until(() -> resultsOf(target) == 1);
        assertThat(TARGET.hits.get()).isEqualTo(1);
    }

    @Test
    void claimOnlySucceedsOnceEvenWhenCalledAtTheSameTime() throws Exception {
        Target target = newTarget("reserva", 3);
        makeDue(target);
        Instant now = Instant.now();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        int claimed = 0;
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> targets.claimDue(target.getId(), now)));
            }
            for (Future<Integer> f : futures) {
                claimed += f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(claimed).isEqualTo(1);
    }

    @Test
    void severalTicksAtTheSameTimeCheckEachTargetOnce() throws Exception {
        List<Target> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Target t = newTarget("paralelo-" + i, 3);
            makeDue(t);
            created.add(t);
        }

        ExecutorService pool = Executors.newFixedThreadPool(4);
        int started = 0;
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(dispatcher::tick));
            }
            for (Future<Integer> f : futures) {
                started += f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(started).isEqualTo(5);
        await().atMost(Duration.ofSeconds(15)).until(() -> count("check_results") == 5);
        for (Target t : created) {
            assertThat(resultsOf(t)).isEqualTo(1);
        }
    }

    @Test
    void aFailingTargetDoesNotPreventTheOthersFromBeingChecked() {
        Target broken = newTarget("quebrado", 3);
        Target healthy = newTarget("saudavel", 3);
        jdbc.update("UPDATE targets SET url = 'http://host-que-nao-existe.invalid/' WHERE id = ?", broken.getId());
        makeDue(broken);
        makeDue(healthy);

        dispatcher.tick();

        await().atMost(Duration.ofSeconds(15)).until(() -> resultsOf(broken) == 1 && resultsOf(healthy) == 1);
    }

    @Test
    void purgeRemovesOnlyHistoryOlderThanTheRetention() {
        Target target = newTarget("retencao", 3);
        UUID id = target.getId();
        insertResult(id, Instant.now().minus(Duration.ofDays(45)));
        insertResult(id, Instant.now().minus(Duration.ofDays(31)));
        insertResult(id, Instant.now().minus(Duration.ofDays(29)));
        insertResult(id, Instant.now().minus(Duration.ofHours(1)));

        assertThat(dispatcher.purgeOldHistory()).isEqualTo(2);

        assertThat(count("check_results")).isEqualTo(2);
    }

    private void insertResult(UUID targetId, Instant at) {
        jdbc.update("INSERT INTO check_results (id, target_id, checked_at, success, http_status, latency_ms) "
                + "VALUES (gen_random_uuid(), ?, ?, TRUE, 200, 10)", targetId, Timestamp.from(at));
    }
}
