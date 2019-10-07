package ch.niceideas.eskimo.model;

import ch.niceideas.common.utils.StringUtils;
import org.json.JSONObject;

import java.util.HashMap;

public class EditableProperty {

    private final String name;
    private final String comment;
    private final String defaultValue;

    public EditableProperty(String name, String comment, String defaultValue) {
        this.name = name;
        this.comment = comment;
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return name;
    }

    public String getComment() {
        return comment;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public JSONObject toJSON() {
        return new JSONObject(new HashMap<String, Object>() {{
            put("name", getName());
            put("comment", getComment());
            put("defaultValue", getDefaultValue());
        }});
    }
}
