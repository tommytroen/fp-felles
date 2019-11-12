package no.nav.vedtak.konfig;

import java.util.Properties;

import javax.enterprise.context.ApplicationScoped;

/** Henter properties fra {@link System#getProperties}. */
@ApplicationScoped
public class EnvPropertiesKonfigVerdiProvider extends PropertiesKonfigVerdiProvider {
    
    public EnvPropertiesKonfigVerdiProvider() {
        super(getEnv());
    }

    private static Properties getEnv() {
        var p = new Properties();
        p.putAll(System.getenv());
        return p;
    }

    @Override
    public int getPrioritet() {
        // Et hakk lavere prioritet enn SystemPropertiesKonfigVerdiProvider
        return 11; // NOSONAR
    }
}