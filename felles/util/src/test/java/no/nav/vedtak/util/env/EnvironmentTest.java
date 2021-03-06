package no.nav.vedtak.util.env;

import static no.nav.vedtak.util.env.Cluster.PROD_FSS;
import static no.nav.vedtak.util.env.ConfidentialMarkerFilter.CONFIDENTIAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.nav.vedtak.konfig.StandardPropertySource;
import no.nav.vedtak.konfig.Tid;

public class EnvironmentTest {

    private static final Logger LOG = LoggerFactory.getLogger(EnvironmentTest.class);
    private static Environment ENV = Environment.current();
    private static PrintStream SYSOUT = System.out;

    @AfterEach
    public void after() throws Exception {
        // reset
        System.setOut(SYSOUT);
    }

    @Test
    // Denne testen må kjøres fra maven, ettersom vi ikke enkelt kan sette env
    // properties i kode.
    public void testEnvironment() {
        assertEquals(ENV.getCluster(), PROD_FSS);
        assertEquals("jalla", ENV.namespace());
        assertTrue(ENV.isProd());
    }

    public void testURI() {
        assertEquals(ENV.getRequiredProperty("VG", URI.class), URI.create("http://www.vg.no"));
    }

    public void testUppercase() {
        assertEquals(PROD_FSS.clusterName(), ENV.getProperty("nais.cluster.name"));
    }

    // @Test
    public void testTurboFilterMedMarkerIProd() {
        var stdout = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdout));
        LOG.info(CONFIDENTIAL, "Dette er konfidensielt, OK i dev men ikke i prod, denne testen kjører i pseudo-prod");
        assertEquals(0, stdout.size());
    }

    @Test
    public void testTurboFilterUtenMarkerIProd() {
        var stdout = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdout));
        LOG.info("Dette er ikke konfidensielt");
        assertTrue(stdout.size() > 0);
    }

    @Test
    public void testDuration() {
        assertEquals(Duration.ofDays(42), ENV.getProperty("duration.property", Duration.class));
        assertEquals(Duration.ofDays(2),
                ENV.getProperty("ikke.funnet", Duration.class, Duration.ofDays(2)));
    }

    @Test
    public void testString() {
        assertEquals("42", ENV.getProperty("finnes.ikke", "42"));
        assertNull(ENV.getProperty("finnes.ikke"));
    }

    @Test
    public void testBoolean() {
        assertTrue(ENV.getProperty("test4.boolean", boolean.class));
        assertTrue(ENV.getProperty("test4.boolean", Boolean.class));
    }

    @Test
    public void testInt() {
        LOG.info("Application property verdier {}", ENV.getProperties(StandardPropertySource.APP_PROPERTIES));
        assertEquals(Integer.valueOf(10), ENV.getProperty("test2.intproperty", Integer.class));
        assertEquals(Integer.valueOf(10), ENV.getProperty("test2.intproperty", int.class));
    }

    @Test
    public void testPropertiesFraEnvUkjentConverter() {
        assertThrows(IllegalArgumentException.class, () -> ENV.getProperty("finnes.ikke", Tid.class));
    }

    @Test
    public void testPropertiesIkkeFunnet() {
        assertThrows(IllegalStateException.class, () -> ENV.getRequiredProperty("finnes.ikke"));
    }

}
