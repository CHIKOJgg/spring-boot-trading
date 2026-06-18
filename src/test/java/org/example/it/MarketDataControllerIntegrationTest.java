package org.example.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.response.ApiResponse.InstrumentChartResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Market Data Integration Tests")
class MarketDataControllerIntegrationTest extends MarketTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        resetData();
    }

    @Test
    @DisplayName("Chart endpoint returns real series for a traded instrument")
    void chartEndpoint_returnsRealSeries() throws Exception {
        var instrument = saveInstrument("SBER", "Sberbank", "STOCK");
        var buyer = saveUser("chart-buyer");
        var seller = saveUser("chart-seller");
        var buyerAccount = saveTradingAccount(buyer, "TA-1001");
        var sellerAccount = saveTradingAccount(seller, "TA-2001");
        var baseTime = LocalDateTime.now().minusMinutes(15);

        var buyOrder1 = saveOrder("buy-1", buyer, buyerAccount, instrument, "BUY",
                new BigDecimal("100.10"), 10, 0, baseTime.minusMinutes(10));
        var sellOrder1 = saveOrder("sell-1", seller, sellerAccount, instrument, "SELL",
                new BigDecimal("100.10"), 10, 0, baseTime.minusMinutes(10));
        saveTrade("trade-1", buyOrder1, sellOrder1, instrument, buyer, seller,
                new BigDecimal("100.10"), 10, baseTime.minusMinutes(10));

        var buyOrder2 = saveOrder("buy-2", buyer, buyerAccount, instrument, "BUY",
                new BigDecimal("101.25"), 8, 0, baseTime.minusMinutes(5));
        var sellOrder2 = saveOrder("sell-2", seller, sellerAccount, instrument, "SELL",
                new BigDecimal("101.25"), 8, 0, baseTime.minusMinutes(5));
        saveTrade("trade-2", buyOrder2, sellOrder2, instrument, buyer, seller,
                new BigDecimal("101.25"), 8, baseTime.minusMinutes(5));

        var result = mockMvc.perform(get("/api/market/chart/SBER")
                        .param("points", "5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        InstrumentChartResponse chart = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                InstrumentChartResponse.class);

        assertThat(chart.ticker()).isEqualTo("SBER");
        assertThat(chart.points()).hasSize(2);
        assertThat(chart.points().get(0).timestamp()).isBefore(chart.points().get(1).timestamp());
        assertThat(chart.openPrice()).isEqualByComparingTo("100.10");
        assertThat(chart.closePrice()).isEqualByComparingTo("101.25");
        assertThat(chart.change()).isEqualByComparingTo("1.15");
    }

    @Test
    @DisplayName("Chart endpoint falls back to synthetic points when no trades exist")
    void chartEndpoint_returnsSyntheticSeries() throws Exception {
        saveInstrument("OFZ26222", "OFZ 26222", "BOND");

        var result = mockMvc.perform(get("/api/market/chart/OFZ26222")
                        .param("points", "12")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        InstrumentChartResponse chart = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                InstrumentChartResponse.class);

        assertThat(chart.instrumentType()).isEqualTo("BOND");
        assertThat(chart.points()).hasSize(12);
        assertThat(chart.highPrice()).isGreaterThanOrEqualTo(chart.lowPrice());
    }

    @Test
    @DisplayName("Fixed currency conversion and demo market endpoints respond correctly")
    void currencyAndDemoEndpoints_work() throws Exception {
        var convertResult = mockMvc.perform(get("/api/market/currency/convert")
                        .param("amount", "100")
                        .param("from", "USD")
                        .param("to", "BYN")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode conversion = objectMapper.readTree(convertResult.getResponse().getContentAsString());
        assertThat(conversion.get("convertedAmount").decimalValue()).isEqualByComparingTo("325.0000");

        var pulseResult = mockMvc.perform(get("/api/market/demo/pulse")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode pulse = objectMapper.readTree(pulseResult.getResponse().getContentAsString());
        assertThat(pulse.get("ticker").asText()).isEqualTo("DEMO-BYN");
        assertThat(pulse.get("lastPrice").decimalValue()).isPositive();
    }
}
