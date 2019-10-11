package ch.niceideas.eskimo.model;

import ch.niceideas.eskimo.services.EditablePropertyType;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class EditableConfiguration {

    private final Service service;
    private final String filename;
    private final EditablePropertyType propertyType;
    private final String propertyFormat;
    private String commentPrefix = "";
    private final List<EditableProperty> properties = new ArrayList<>();

    public EditableConfiguration(Service service, String filename, EditablePropertyType propertyType, String propertyFormat) {
        this.service = service;
        this.filename = filename;
        this.propertyType = propertyType;
        this.propertyFormat = propertyFormat;
    }

    public String getFilename() {
        return filename;
    }

    public String getPropertyFormat() {
        return propertyFormat;
    }

    public String getCommentPrefix() {
        return commentPrefix;
    }

    public void setCommentPrefix(String commentPrefix) {
        this.commentPrefix = commentPrefix;
    }

    public EditablePropertyType getPropertyType() {
        return propertyType;
    }

    public List<EditableProperty> getProperties() {
        return Collections.unmodifiableList(properties);
    }

    public void addProperty (EditableProperty property) {
        properties.add(property);
    }

    public JSONObject toJSON() {
        JSONArray propertiesArray = new JSONArray(properties.stream()
                .map(prop -> prop.toJSON())
                .collect(Collectors.toList())
        );
        return new JSONObject(new HashMap<String, Object>() {{
            put("service", service.getName());
            put("filename", getFilename());
            put("propertyType", getPropertyType());
            put("propertyFormat", getPropertyFormat());
            put("commentPrefix", getCommentPrefix());
            put("properties", propertiesArray);
        }});
    }
}
