package ch.niceideas.eskimo.model;

import java.io.Serializable;

public interface OperationId extends Serializable {

    String getMessage();

    String getService();

    boolean isOnNode(String node);

    boolean isSameNode(OperationId other);
}
