package ar.edu.utn.frc.siga.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class TrustedProxiesStartupCheckTest {

    private static MockEnvironment profile(String name) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(name);
        return env;
    }

    @Test
    void isMisconfigured_emptyProxiesInDeployedProfile_isTrue() {
        assertThat(new TrustedProxiesStartupCheck("", profile("dev")).isMisconfigured()).isTrue();
        assertThat(new TrustedProxiesStartupCheck("", profile("test")).isMisconfigured()).isTrue();
        assertThat(new TrustedProxiesStartupCheck("", profile("prod")).isMisconfigured()).isTrue();
    }

    @Test
    void isMisconfigured_proxiesSet_isFalse() {
        assertThat(new TrustedProxiesStartupCheck("10\\.0\\.0\\.1", profile("dev")).isMisconfigured()).isFalse();
    }

    @Test
    void isMisconfigured_localProfile_isFalse() {
        assertThat(new TrustedProxiesStartupCheck("", profile("dev-local")).isMisconfigured()).isFalse();
        assertThat(new TrustedProxiesStartupCheck("", profile("integration")).isMisconfigured()).isFalse();
    }
}
