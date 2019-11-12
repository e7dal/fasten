package eu.fasten.analyzer;

import org.junit.Test;

import static eu.fasten.analyzer.CallGraphGenerator.generateCallGraph;
import static org.junit.Assert.assertNull;

public class WalaCGTest {

    @Test
    public void testCreation() {
        var cg = generateCallGraph("org.slf4j:slf4j-api:1.7.29");
        assertNull(cg);
    }
}
