package com.ververica.cep.demo;

/** Exception that happened during release process. */
public class CepDemoException extends RuntimeException {
    private static final long serialVersionUID = -2300756271710369062L;

    /**
     * Creates a new instance of {@link CepDemoException}.
     *
     * @param cause exception cause
     */
    public CepDemoException(final Throwable cause) {
        super(cause);
    }

    /**
     * Creates a new instance of {@link CepDemoException}.
     *
     * @param message message
     * @param cause exception cause
     */
    public CepDemoException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new instance of {@link CepDemoException}.
     *
     * @param message exception message
     */
    public CepDemoException(final String message) {
        super(message);
    }
}
