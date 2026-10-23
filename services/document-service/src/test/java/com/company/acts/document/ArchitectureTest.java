package com.company.acts.document;

import com.company.acts.common.test.HexagonalArchitectureRules;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    @Test
    void layersRespectHexagonalArchitecture() {
        HexagonalArchitectureRules.check("com.company.acts.document");
    }
}
