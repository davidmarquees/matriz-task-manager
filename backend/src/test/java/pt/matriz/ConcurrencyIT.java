package pt.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.matriz.ApiTestSupport.PASSWORD;
import static pt.matriz.ApiTestSupport.createUser;
import static pt.matriz.ApiTestSupport.login;
import static pt.matriz.ApiTestSupport.userId;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Rules that must hold when two users act at the same instant (docs/database.md D5, D16, D21): the
 * requests are released together from separate threads, repeated a few times.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class ConcurrencyIT {

    static final int ROUNDS = 5;

    @Container
    static final PostgreSQLContainer<?> DB = PostgresTestDatabase.newContainer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, DB);
        registry.add("matriz.login.rate-limit.max-attempts", () -> "10000");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    ObjectMapper json;

    @Test
    void twoManagersDemotingEachOtherAtOnceLeaveOneManager() throws Exception { // D16, E6
        for (int round = 0; round < ROUNDS; round++) {
            String aEmail = createUser(jdbc, encoder, "MANAGER", "lead-a");
            String bEmail = createUser(jdbc, encoder, "MANAGER", "lead-b");
            MockHttpSession a = login(mvc, aEmail, PASSWORD);
            MockHttpSession b = login(mvc, bEmail, PASSWORD);
            long project = createdId(send(post("/projects"), a, "{\"name\":\"Race " + round + "\"}"));
            send(post("/projects/" + project + "/members"), a, "{\"email\":\"" + bEmail + "\",\"projectRole\":\"MANAGER\"}");

            List<Integer> statuses = atTheSameTime(
                    () -> code(send(patch("/projects/" + project + "/members/" + userId(jdbc, bEmail)), a,
                            "{\"projectRole\":\"MEMBER\"}")),
                    () -> code(send(patch("/projects/" + project + "/members/" + userId(jdbc, aEmail)), b,
                            "{\"projectRole\":\"MEMBER\"}")));

            // One demotion wins (200); the other is refused: 409 LAST_MANAGER when both passed the Manager check
            // before the first one was saved, 403 when the first one was already saved (no longer a Manager, D10).
            // Which one happens depends on timing (seen in CI: [200, 403]).
            assertThat(statuses).as("statuses in round %d", round).hasSize(2).contains(200);
            assertThat(statuses).filteredOn(s -> s != 200).singleElement().isIn(403, 409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM project_members WHERE project_id = ? "
                    + "AND project_role = 'MANAGER'", Integer.class, project)).isEqualTo(1);
        }
    }

    @Test
    void theSamePersonAssignedTwiceAtOnceIsSavedOnce() throws Exception { // D21
        for (int round = 0; round < ROUNDS; round++) {
            String managerEmail = createUser(jdbc, encoder, "MANAGER", "owner");
            MockHttpSession manager = login(mvc, managerEmail, PASSWORD);
            MockHttpSession sameUserOtherTab = login(mvc, managerEmail, PASSWORD);
            long project = createdId(send(post("/projects"), manager, "{\"name\":\"Assign race " + round + "\"}"));
            long task = createdId(send(post("/projects/" + project + "/tasks"), manager, "{\"title\":\"Shared task\"}"));
            String body = "{\"userId\":" + userId(jdbc, managerEmail) + "}";

            List<Integer> statuses = atTheSameTime(
                    () -> code(send(post("/tasks/" + task + "/assignees"), manager, body)),
                    () -> code(send(post("/tasks/" + task + "/assignees"), sameUserOtherTab, body)));

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM task_assignees WHERE task_id = ?", Integer.class, task))
                    .isEqualTo(1);
        }
    }

    @Test
    void deactivatingAManagerWhileTheOtherIsDemotedNeverLeavesAProjectWithoutManager() throws Exception { // D5, D16
        jdbc.update("UPDATE users SET must_change_password = false WHERE account_role = 'ADMIN'");
        for (int round = 0; round < ROUNDS * 2; round++) {
            MockHttpSession admin = login(mvc, "admin@example.test", "test-admin-password");
            String aEmail = createUser(jdbc, encoder, "MANAGER", "stay-a");
            String bEmail = createUser(jdbc, encoder, "MANAGER", "stay-b");
            MockHttpSession a = login(mvc, aEmail, PASSWORD);
            long project = createdId(send(post("/projects"), a, "{\"name\":\"Keep a Manager " + round + "\"}"));
            send(post("/projects/" + project + "/members"), a, "{\"email\":\"" + bEmail + "\",\"projectRole\":\"MANAGER\"}");

            List<Integer> statuses = atTheSameTime(
                    () -> code(send(post("/admin/users/" + userId(jdbc, aEmail) + "/deactivate"), admin, "{}")),
                    () -> code(send(patch("/projects/" + project + "/members/" + userId(jdbc, bEmail)), a,
                            "{\"projectRole\":\"MEMBER\"}")));

            assertThat(jdbc.queryForObject("SELECT count(*) FROM project_members WHERE project_id = ? "
                    + "AND project_role = 'MANAGER'", Integer.class, project))
                    .as("Managers left in round %d, statuses %s", round, statuses).isGreaterThanOrEqualTo(1);
        }
    }

    /** Runs both actions from separate threads, released together. */
    @SafeVarargs
    private static List<Integer> atTheSameTime(Callable<Integer>... actions) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(actions.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> action : actions) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return action.call();
                }));
            }
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private int code(org.springframework.test.web.servlet.ResultActions result) {
        return result.andReturn().getResponse().getStatus();
    }

    private org.springframework.test.web.servlet.ResultActions send(MockHttpServletRequestBuilder request,
                                                                   MockHttpSession session, String body) throws Exception {
        return mvc.perform(request.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long createdId(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }
}
