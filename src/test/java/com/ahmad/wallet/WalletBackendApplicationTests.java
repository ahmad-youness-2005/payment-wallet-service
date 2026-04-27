package com.ahmad.wallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class WalletBackendApplicationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void contextLoads() {
    }

    @Test
    void onlyTheTransferOwnerCanReadIt() throws Exception {
        var alice = signUp("alice");
        var bob = signUp("bob");
        var eve = signUp("eve");

        topUp(alice.token, "500.00");
        var transferId = send(alice.token, bob.userId, "120.00")
                .get("transferId").asText();

        mvc.perform(get("/api/v1/transfers/{id}", transferId)
                        .header("Authorization", bearer(eve.token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void freezingAWalletMigratesItsBalanceToANewActiveOne() throws Exception {
        var u = signUp("freeze");
        topUp(u.token, "300.00");

        String freezeBody = mvc.perform(post("/api/v1/wallet/me/freeze")
                        .header("Authorization", bearer(u.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousWalletStatus").value("FROZEN"))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(freezeBody).get("migratedBalance").decimalValue())
                .isEqualByComparingTo("300.00");

        mvc.perform(get("/api/v1/wallet/me").header("Authorization", bearer(u.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.availableBalance").value(300.00));

        String all = mvc.perform(get("/api/v1/wallet/me/all").header("Authorization", bearer(u.token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(all).get("wallets")).hasSize(2);
    }

    @Test
    void insufficientBalanceReturns400WithBusinessMessage() throws Exception {
        var sender = signUp("poor");
        var receiver = signUp("rich");

        String body = """
                {"receiverUserId":"%s","amount":900.00}
                """.formatted(receiver.userId);

        mvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(sender.token))
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Insufficient balance"));
    }

    @Test
    void historyRejectsAnUnknownTypeFilter() throws Exception {
        var u = signUp("filter");
        mvc.perform(get("/api/v1/wallet/me/history?type=NOT_A_TYPE")
                        .header("Authorization", bearer(u.token)))
                .andExpect(status().isBadRequest());
    }

    private User signUp(String prefix) throws Exception {
        String email = prefix + "+" + UUID.randomUUID() + "@example.com";
        String body = "{\"fullName\":\"" + prefix + "\",\"email\":\"" + email
                + "\",\"password\":\"Password123\"}";
        String resp = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode n = json.readTree(resp);
        return new User(n.get("token").asText(), UUID.fromString(n.get("userId").asText()));
    }

    private void topUp(String token, String amount) throws Exception {
        mvc.perform(post("/api/v1/wallet/me/topup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(token))
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().isOk());
    }

    private JsonNode send(String token, UUID receiverId, String amount) throws Exception {
        String body = "{\"receiverUserId\":\"" + receiverId + "\",\"amount\":" + amount + "}";
        String resp = mvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(token))
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resp);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static final class User {
        final String token;
        final UUID userId;

        User(String token, UUID userId) {
            this.token = token;
            this.userId = userId;
        }
    }
}
