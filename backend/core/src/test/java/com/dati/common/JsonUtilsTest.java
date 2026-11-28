package com.dati.common;

import com.dati.base.exception.DatiException;
import com.dati.base.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JsonUtils unit tests")
class JsonUtilsTest {

    @Test
    void invalidJsonThrowsDatiException() {
        assertThatThrownBy(() -> JsonUtils.fromJson("{not json", Object.class))
            .isInstanceOf(DatiException.class)
            .extracting(e -> ((DatiException) e).getCode())
            .isEqualTo(ErrorCode.JSON_CONVERSION_FAILED);
        assertThatThrownBy(() -> JsonUtils.parseJson("{not json"))
            .isInstanceOf(DatiException.class);
        assertThatThrownBy(() -> JsonUtils.toMap("{not json"))
            .isInstanceOf(DatiException.class);
        assertThatThrownBy(() -> JsonUtils.toList("{not json", Object.class))
            .isInstanceOf(DatiException.class);
    }

    @Test
    void roundTripUsesSnakeCase() {
        record Sample(String displayName) {}
        String json = JsonUtils.toJson(new Sample("alice"));
        assertThat(json).contains("display_name");
        assertThat(JsonUtils.fromJson(json, Sample.class).displayName()).isEqualTo("alice");
    }
}
