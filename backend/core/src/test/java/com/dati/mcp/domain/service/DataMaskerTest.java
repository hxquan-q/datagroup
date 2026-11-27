package com.dati.mcp.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataMasker tests")
class DataMaskerTest {

    @Test
    @DisplayName("masks phone, email, secret, and partial-name columns")
    void masksSensitiveColumns() {
        assertThat(DataMasker.mask("contact_phone", "13812341234")).isEqualTo("138****1234");
        assertThat(DataMasker.mask("user_email", "zhangsan@example.com")).isEqualTo("z***@example.com");
        assertThat(DataMasker.mask("db_password", "supersecret")).isEqualTo("******");
        assertThat(DataMasker.mask("customer_name", "张三丰")).isEqualTo("张**");
    }

    @Test
    @DisplayName("leaves ordinary columns and nulls untouched")
    void leavesOrdinaryValues() {
        assertThat(DataMasker.mask("fpy", "98.5")).isEqualTo("98.5");
        assertThat(DataMasker.mask("contact_phone", null)).isNull();
        assertThat(DataMasker.isSensitive("station_output")).isFalse();
        assertThat(DataMasker.isSensitive("mobile_no")).isTrue();
    }

    @Test
    @DisplayName("secret columns take precedence over phone-shaped names")
    void secretTakesPrecedence() {
        Object masked = DataMasker.mask("secret_phone", "13812345678");
        org.assertj.core.api.Assertions.assertThat(masked).isEqualTo("******");
    }

    @Test
    @DisplayName("short phone or identity values never leak all characters")
    void shortValuesAreRedacted() {
        org.assertj.core.api.Assertions.assertThat(DataMasker.mask("phone", "1234567")).isEqualTo("12****");
        org.assertj.core.api.Assertions.assertThat(DataMasker.mask("idcard", "1234567")).isEqualTo("******");
        org.assertj.core.api.Assertions.assertThat(DataMasker.mask("phone", "13812345678")).isEqualTo("138****5678");
    }
}
