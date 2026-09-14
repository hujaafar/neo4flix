package io.neo4flix.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;

class OgmBootstrapTest {

    @Test
    void driverAndMappedEntitiesInitializeWithThePackagedDependencies() {
        // Driver construction is lazy: validate dependencies and OGM metadata without a live database.
        try (var driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())) {
            assertDoesNotThrow(() -> new Ogm(driver));
        }
    }
}
