package dev.ronaldofojr.monitor.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ronaldofojr.monitor.service.Exceptions.BusinessRuleException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"http://exemplo.com", "https://exemplo.com/health", "HTTPS://EXEMPLO.COM:8443/a?b=c", "http://localhost:8080/actuator/health"})
    void acceptsHttpAndHttpsUrls(String url) {
        assertThatCode(() -> UrlValidator.validate(url)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://exemplo.com", "file:///etc/passwd", "javascript:alert(1)", "exemplo.com", "http://", "https:///caminho", "http://exe mplo.com", ""})
    void rejectsEverythingElse(String url) {
        assertThatThrownBy(() -> UrlValidator.validate(url)).isInstanceOf(BusinessRuleException.class);
    }

    @org.junit.jupiter.api.Test
    void rejectsNull() {
        assertThatThrownBy(() -> UrlValidator.validate(null)).isInstanceOf(BusinessRuleException.class);
    }
}
