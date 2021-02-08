package ch.niceideas.eskimo.model;

import ch.niceideas.common.utils.Pair;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@RequiredArgsConstructor
public class SimpleOperation implements JSONOpCommand {

    private final String operationName;

    @Override
    public JSONObject toJSON() {
        return new JSONObject(new HashMap<String, Object>() {{
            put("operationName", operationName);
        }});
    }

    @Override
    public boolean hasChanges() {
        return true;
    }

    @Override
    public List<Pair<String, String>> getAllOperationsInOrder(OperationsContext context) {
        return new ArrayList<>() {{
            add(new Pair<>(operationName, "OPERATION"));
        }};

    }
}
