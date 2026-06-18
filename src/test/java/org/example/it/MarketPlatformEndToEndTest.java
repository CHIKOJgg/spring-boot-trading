package org.example.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Platform End-to-End Smoke Test")
class MarketPlatformEndToEndTest extends MarketTestSupport {

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        resetData();

        var instrument = saveInstrument("SBER", "Sberbank", "STOCK");
        var buyer = saveUser("e2e-buyer");
        var seller = saveUser("e2e-seller");
        var buyerAccount = saveTradingAccount(buyer, "TA-E2E-1");
        var sellerAccount = saveTradingAccount(seller, "TA-E2E-2");
        var tradeTime = LocalDateTime.now().minusMinutes(3);

        var buyOrder = saveOrder("e2e-buy", buyer, buyerAccount, instrument, "BUY",
                new BigDecimal("98.75"), 15, 0, tradeTime);
        var sellOrder = saveOrder("e2e-sell", seller, sellerAccount, instrument, "SELL",
                new BigDecimal("98.75"), 15, 0, tradeTime);
        saveTrade("e2e-trade", buyOrder, sellOrder, instrument, buyer, seller,
                new BigDecimal("98.75"), 15, tradeTime);

        saveInstrument("OFZ26222", "OFZ 26222", "BOND");
    }

    @Test
    @DisplayName("Home page and market APIs are wired together")
    void homePage_andMarketApis_workTogether() throws Exception {
        ResponseEntity<String> home = restTemplate.getForEntity(url("/"), String.class);
        assertThat(home.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(home.getBody()).contains("id=\"price-chart\"");
        assertThat(home.getBody()).contains("id=\"chart-ticker\"");
        assertThat(home.getBody()).contains("Price chart");

        JsonNode instruments = objectMapper.readTree(
                restTemplate.getForObject(url("/api/market/instruments"), String.class));
        assertThat(instruments.size()).isGreaterThanOrEqualTo(2);

        JsonNode chart = objectMapper.readTree(
                restTemplate.getForObject(url("/api/market/chart/SBER?points=8"), String.class));
        assertThat(chart.get("ticker").asText()).isEqualTo("SBER");
        assertThat(chart.get("points").size()).isGreaterThan(0);

        JsonNode syntheticChart = objectMapper.readTree(
                restTemplate.getForObject(url("/api/market/chart/OFZ26222?points=8"), String.class));
        assertThat(syntheticChart.get("instrumentType").asText()).isEqualTo("BOND");
        assertThat(syntheticChart.get("points").size()).isEqualTo(8);

        JsonNode conversion = objectMapper.readTree(
                restTemplate.getForObject(url("/api/market/currency/convert?amount=10&from=EUR&to=BYN"), String.class));
        assertThat(conversion.get("convertedAmount").decimalValue()).isEqualByComparingTo("35.5000");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
