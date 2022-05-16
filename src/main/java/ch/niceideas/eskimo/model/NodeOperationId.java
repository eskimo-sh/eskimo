package ch.niceideas.eskimo.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Data
@NoArgsConstructor
public abstract class NodeOperationId implements OperationId {

    private String node;

    public NodeOperationId (String node) {
        this.node = node;
    }

    public boolean isOnNode(String node) {
        return this.node.equals(node);
    }

    public boolean isSameNode(OperationId other) {
        return other.isOnNode(this.getNode());
    }

}
