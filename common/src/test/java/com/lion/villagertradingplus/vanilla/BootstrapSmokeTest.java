package com.lion.villagertradingplus.vanilla;

import org.junit.jupiter.api.Test;

class BootstrapSmokeTest {

    @Test
    void bootstrapRuns() throws Throwable {
        KnotHarness.run(BootstrapSmokeLogic.class, "run");
    }
}
