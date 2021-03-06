package no.nav.vedtak.exception;

import org.slf4j.Logger;

import no.nav.vedtak.feil.Feil;

public abstract class VLException extends RuntimeException {

    private final Feil feil;

    VLException(Feil feil) {
        super(feil.toLogString(), feil.getCause());
        this.feil = feil;
    }

    public Feil getFeil() {
        return feil;
    }

    public String getKode() {
        return feil.getKode();
    }

    protected static String format(String msg, Object... args) {
        return String.format(msg, args);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ":" + getFeil();
    }

    public void log(Logger logger) {
        String text = feil.toLogString();
        switch (feil.getLogLevel()) {
            case ERROR:
                logger.error(text, this);
                break;
            case WARN:
                logger.warn(text, this);
                break;
            case INFO:
                logger.info(text);
                break;
            default:
                throw new IllegalArgumentException("Ikke-støttet LogLevel: " + feil.getLogLevel());
        }
    }

}
