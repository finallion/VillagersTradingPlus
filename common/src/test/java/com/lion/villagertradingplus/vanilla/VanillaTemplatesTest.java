package com.lion.villagertradingplus.vanilla;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VanillaTemplatesTest {

    @Test
    @Order(1)
    void templatesEqualGeneratorOutput() throws Throwable {
        KnotHarness.run(VanillaTemplatesLogic.class, "generator");
    }

    @Test
    @Order(2)
    void everyTemplateHasAllStats() throws Throwable {
        KnotHarness.run(VanillaTemplatesLogic.class, "shape");
    }

    @Test
    @Order(3)
    void templatesMatchVanillaListingByListing() throws Throwable {
        KnotHarness.run(VanillaTemplatesLogic.class, "proof");
    }

    @Test
    @Order(4)
    void randomTradesHaveVanillaResultFrequencies() throws Throwable {
        KnotHarness.run(VanillaTemplatesLogic.class, "distributions");
    }
}
