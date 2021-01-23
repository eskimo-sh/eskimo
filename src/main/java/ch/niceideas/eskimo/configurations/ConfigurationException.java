package ch.niceideas.eskimo.configurations;

import ch.niceideas.common.exceptions.CommonRTException;

public class ConfigurationException extends CommonRTException {

    static final long serialVersionUID = -3311512111124229248L;

    public ConfigurationException() {
    }

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigurationException(Throwable cause) {
        super(cause);
    }

}
