package ch.niceideas.eskimo.proxy;

public class ProxyException extends RuntimeException {

    static final long serialVersionUID = -3311511111124119248L;

    public ProxyException() {
    }

    public ProxyException(String message) {
        super(message);
    }

    public ProxyException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProxyException(Throwable cause) {
        super(cause);
    }

}
