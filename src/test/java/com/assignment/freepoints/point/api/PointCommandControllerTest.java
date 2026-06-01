package com.assignment.freepoints.point.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assignment.freepoints.common.api.ApiResponse;
import com.assignment.freepoints.point.grant.GrantType;
import com.assignment.freepoints.point.use.PointUse;
import com.assignment.freepoints.point.use.PointUseRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PointCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PointUseRepository pointUseRepository;

    @Test
    void grant_is_idempotent_for_same_transaction_no() throws Exception {
        PointGrantRequest request = new PointGrantRequest("user-grant-idem", "grant-tx-1", 1000, false, null, 365);

        PointCommandResponse first = call("/api/points/grants", request);
        PointCommandResponse second = call("/api/points/grants", request);
        Integer grantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from point_grant g
                join point_account a on a.id = g.account_id
                where a.user_id = ?
                """, Integer.class, "user-grant-idem");

        assertThat(second.pointKey()).isEqualTo(first.pointKey());
        assertThat(second.balance()).isEqualTo(first.balance());
        assertThat(grantCount).isEqualTo(1);
    }

    @Test
    void use_cancel_regrants_when_original_grant_is_already_expired() throws Exception {
        PointGrantRequest grantRequest = new PointGrantRequest("user-expired-cancel", "grant-tx-2", 1000, false, null, 365);
        PointCommandResponse grantResponse = call("/api/points/grants", grantRequest);

        PointUseRequest useRequest = new PointUseRequest("user-expired-cancel", "use-tx-2", "ORDER-1", 700);
        PointCommandResponse useResponse = call("/api/points/uses", useRequest);

        jdbcTemplate.update(
                """
                update point_grant
                   set created_at = DATEADD('DAY', -2, CURRENT_TIMESTAMP),
                       updated_at = CURRENT_TIMESTAMP,
                       expires_at = DATEADD('DAY', -1, CURRENT_TIMESTAMP),
                       status = 'EXPIRED'
                 where point_key = ?
                """,
                grantResponse.pointKey()
        );

        PointUseCancelRequest cancelRequest = new PointUseCancelRequest(
                "user-expired-cancel",
                "use-cancel-tx-2",
                useResponse.pointKey(),
                400,
                "partial cancel"
        );
        PointCommandResponse cancelResponse = call("/api/points/use-cancels", cancelRequest);

        PointUse pointUse = pointUseRepository.findByPointKey(useResponse.pointKey()).orElseThrow();
        Integer grantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from point_grant g
                join point_account a on a.id = g.account_id
                where a.user_id = ?
                """, Integer.class, "user-expired-cancel");
        String regrantType = jdbcTemplate.queryForObject("""
                select grant_type
                from point_grant
                where source_ref = ?
                order by id desc
                limit 1
                """, String.class, grantResponse.pointKey());

        assertThat(cancelResponse.balance()).isEqualTo(700);
        assertThat(pointUse.getCancelableAmount()).isEqualTo(300);
        assertThat(grantCount).isEqualTo(2);
        assertThat(regrantType).isEqualTo(GrantType.REGRANT_FROM_USE_CANCEL.name());
    }

    @Test
    void balance_and_ledger_queries_return_expected_result() throws Exception {
        call("/api/points/grants", new PointGrantRequest("user-query", "grant-tx-q1", 1000, true, "ADMIN", 365));
        call("/api/points/grants", new PointGrantRequest("user-query", "grant-tx-q2", 500, false, null, 365));
        PointCommandResponse useResponse = call("/api/points/uses", new PointUseRequest("user-query", "use-tx-q1", "ORDER-Q1", 1200));
        call("/api/points/use-cancels", new PointUseCancelRequest("user-query", "use-cancel-tx-q1", useResponse.pointKey(), 200, "partial"));

        PointBalanceResponse balance = getResponse("/api/points/balance?userId=user-query", new TypeReference<ApiResponse<PointBalanceResponse>>() {
        }).data();
        PointLedgerResponse ledger = getResponse("/api/points/ledger?userId=user-query", new TypeReference<ApiResponse<PointLedgerResponse>>() {
        }).data();

        assertThat(balance.balance()).isEqualTo(500);
        assertThat(ledger.balance()).isEqualTo(500);
        assertThat(ledger.entries()).hasSize(4);
        assertThat(ledger.entries()).extracting(PointLedgerEntryResponse::type)
                .containsExactlyInAnyOrder("GRANT", "GRANT", "USE", "USE_CANCEL");
        assertThat(ledger.entries()).anyMatch(entry -> entry.type().equals("USE") && entry.remainingAmount() == 1000L);
        assertThat(ledger.entries()).anyMatch(entry -> entry.type().equals("USE_CANCEL") && entry.amount() == 200L);
    }

    @Test
    void idempotency_conflict_returns_409_for_different_payload() throws Exception {
        mockMvc.perform(post("/api/points/grants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PointGrantRequest("user-conflict", "grant-conflict-1", 1000, false, null, 365))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/points/grants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PointGrantRequest("user-conflict", "grant-conflict-1", 900, false, null, 365))))
                .andExpect(status().isConflict());
    }

    @Test
    void use_consumes_grants_by_manual_then_expiry_priority() throws Exception {
        PointCommandResponse manualGrant = call("/api/points/grants",
                new PointGrantRequest("user-priority", "grant-pri-manual", 100, true, "ADMIN", 50));
        PointCommandResponse earlyGrant = call("/api/points/grants",
                new PointGrantRequest("user-priority", "grant-pri-early", 100, false, null, 10));
        PointCommandResponse midGrant = call("/api/points/grants",
                new PointGrantRequest("user-priority", "grant-pri-mid", 100, false, null, 20));

        call("/api/points/uses", new PointUseRequest("user-priority", "use-pri", "ORDER-PRI", 250));

        assertThat(remainingOf(manualGrant.pointKey())).isEqualTo(0L);
        assertThat(remainingOf(earlyGrant.pointKey())).isEqualTo(0L);
        assertThat(remainingOf(midGrant.pointKey())).isEqualTo(50L);
    }

    @Test
    void use_fails_with_insufficient_balance() throws Exception {
        call("/api/points/grants", new PointGrantRequest("user-insufficient", "grant-insuf", 1000, false, null, 365));

        postExpectingError(
                "/api/points/uses",
                new PointUseRequest("user-insufficient", "use-insuf", "ORDER-I", 2000),
                HttpStatus.BAD_REQUEST,
                "INSUFFICIENT_BALANCE"
        );
    }

    @Test
    void use_for_unknown_account_returns_account_not_found() throws Exception {
        postExpectingError(
                "/api/points/uses",
                new PointUseRequest("user-unknown", "use-unknown", "ORDER-X", 100),
                HttpStatus.NOT_FOUND,
                "ACCOUNT_NOT_FOUND"
        );
    }

    @Test
    void grant_fails_when_balance_limit_exceeded() throws Exception {
        for (int i = 0; i < 10; i++) {
            call("/api/points/grants", new PointGrantRequest("user-balance-limit", "grant-bl-" + i, 100000, false, null, 365));
        }

        postExpectingError(
                "/api/points/grants",
                new PointGrantRequest("user-balance-limit", "grant-bl-over", 1, false, null, 365),
                HttpStatus.BAD_REQUEST,
                "BALANCE_LIMIT_EXCEEDED"
        );
    }

    @Test
    void cancel_grant_succeeds_then_fails_on_second_attempt() throws Exception {
        PointCommandResponse grant = call("/api/points/grants",
                new PointGrantRequest("user-gc", "grant-gc", 1000, false, null, 365));

        PointCommandResponse cancel = call("/api/points/grant-cancels",
                new PointGrantCancelRequest("user-gc", "grant-cancel-gc", grant.pointKey(), "admin cancel"));
        assertThat(cancel.balance()).isEqualTo(0);

        postExpectingError(
                "/api/points/grant-cancels",
                new PointGrantCancelRequest("user-gc", "grant-cancel-gc-2", grant.pointKey(), "again"),
                HttpStatus.BAD_REQUEST,
                "GRANT_ALREADY_CANCELED"
        );
    }

    @Test
    void cancel_grant_fails_when_grant_already_used() throws Exception {
        PointCommandResponse grant = call("/api/points/grants",
                new PointGrantRequest("user-used", "grant-used", 1000, false, null, 365));
        call("/api/points/uses", new PointUseRequest("user-used", "use-used", "ORDER-U", 300));

        postExpectingError(
                "/api/points/grant-cancels",
                new PointGrantCancelRequest("user-used", "grant-cancel-used", grant.pointKey(), null),
                HttpStatus.BAD_REQUEST,
                "GRANT_ALREADY_USED"
        );
    }

    @Test
    void cancel_grant_fails_when_owner_mismatch() throws Exception {
        PointCommandResponse ownerGrant = call("/api/points/grants",
                new PointGrantRequest("user-owner", "grant-owner", 1000, false, null, 365));
        call("/api/points/grants", new PointGrantRequest("user-intruder", "grant-intruder", 500, false, null, 365));

        postExpectingError(
                "/api/points/grant-cancels",
                new PointGrantCancelRequest("user-intruder", "grant-cancel-intruder", ownerGrant.pointKey(), "steal"),
                HttpStatus.BAD_REQUEST,
                "POINT_OWNER_MISMATCH"
        );
    }

    @Test
    void cancel_use_fails_when_amount_exceeds_cancelable() throws Exception {
        call("/api/points/grants", new PointGrantRequest("user-ica", "grant-ica", 1000, false, null, 365));
        PointCommandResponse use = call("/api/points/uses", new PointUseRequest("user-ica", "use-ica", "ORDER-ICA", 700));

        postExpectingError(
                "/api/points/use-cancels",
                new PointUseCancelRequest("user-ica", "use-cancel-ica", use.pointKey(), 800, "too much"),
                HttpStatus.BAD_REQUEST,
                "INVALID_CANCEL_AMOUNT"
        );
    }

    @Test
    void cancel_use_fails_with_point_not_found() throws Exception {
        call("/api/points/grants", new PointGrantRequest("user-pnf", "grant-pnf", 1000, false, null, 365));

        postExpectingError(
                "/api/points/use-cancels",
                new PointUseCancelRequest("user-pnf", "use-cancel-pnf", "nonexistent-point-key", 100, null),
                HttpStatus.NOT_FOUND,
                "POINT_NOT_FOUND"
        );
    }

    @Test
    void grant_rejects_invalid_request() throws Exception {
        postExpectingError(
                "/api/points/grants",
                new PointGrantRequest("", "grant-blank", 1000, false, null, 365),
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST"
        );
        postExpectingError(
                "/api/points/grants",
                new PointGrantRequest("user-validation", "grant-zero", 0, false, null, 365),
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST"
        );
        postExpectingError(
                "/api/points/grants",
                new PointGrantRequest("a".repeat(65), "grant-long-user", 1000, false, null, 365),
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST"
        );
    }

    private Long remainingOf(String pointKey) {
        return jdbcTemplate.queryForObject(
                "select remaining_amount from point_grant where point_key = ?", Long.class, pointKey);
    }

    private <T> void postExpectingError(String path, T request, HttpStatus expectedStatus, String expectedCode) throws Exception {
        String body = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus.value()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        ApiResponse<Void> response = objectMapper.readValue(body, new TypeReference<ApiResponse<Void>>() {
        });
        assertThat(response.success()).isFalse();
        assertThat(response.error().code()).isEqualTo(expectedCode);
    }

    private <T> PointCommandResponse call(String path, T request) throws Exception {
        String body = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ApiResponse<PointCommandResponse> response = objectMapper.readValue(
                body,
                new TypeReference<ApiResponse<PointCommandResponse>>() {
                }
        );
        return response.data();
    }

    private <T> T getResponse(String path, TypeReference<T> typeReference) throws Exception {
        String body = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(body, typeReference);
    }
}
