package ch.niceideas.eskimo.model;

public class TopologyException extends RuntimeException {

    static final long serialVersionUID = -3311512111124119248L;

    public TopologyException() {
    }

    public TopologyException(String message) {
        super(message);
    }

    public TopologyException(String message, Throwable cause) {
        super(message, cause);
    }

    public TopologyException(Throwable cause) {
        super(cause);
    }

}
