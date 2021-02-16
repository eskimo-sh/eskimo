package ch.niceideas.eskimo.model;

import ch.niceideas.common.utils.StringUtils;

import java.io.Serializable;

public interface OperationId extends Serializable {

    String getMessage();

    String getService();

    boolean isOnNode(String node);

    boolean isSameNode(OperationId other);
}
