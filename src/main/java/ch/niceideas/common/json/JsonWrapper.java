/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */

package ch.niceideas.common.json;

import ch.niceideas.common.utils.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;

/**
 * This is a wrapper around a JSON string which enables one to get the value at a specific node, change it, test if
 * a specific node exists, etc.
 * <p />
 * 
 * The syntax usesd for node identification follows the same principle than Java properties paths. <br />
 * For instance. If the root node contains an object "aaa" which contains an array "bbb", one can get the value at
 * the third position of this array by using the following path: <code>aaa.bbb.3</code>.
 */
public class JsonWrapper implements Serializable {

    private static final Logger logger = Logger.getLogger(JsonWrapper.class);

    public static final String NOT_FOUND = "not found.";
    public static final String ELEMENT_NOT_ARRAY = " contains contains an element which is not an array but asked next path is an integer";
    public static final String PATH_NOT_INTEGER = " contains an array but the concerned path is not an integer";


    private transient JSONObject json;

    public static JsonWrapper empty() {
        return new JsonWrapper("{}");
    }

    public JsonWrapper(JSONObject json)  {
        this.json = json;
    }

    /**
     * Build a new JsonWrapper around the given JSON string.
     * 
     * @param jsonString the string to wrap.
     */
    public JsonWrapper(String jsonString)  {
        super();
        json = new JSONObject(jsonString);
    }

    private void readObject(ObjectInputStream aInputStream) throws ClassNotFoundException, IOException {
        String serializedString = Optional.ofNullable(aInputStream)
                .orElseThrow(() -> new IllegalArgumentException("passed inputstream cannot be null"))
                .readUTF();
        try {
            json = new JSONObject(serializedString);
        } catch (JSONException e) {
            logger.error(e, e);
            throw new IOException (e.getMessage(), e);
        }
    }

    private void writeObject(ObjectOutputStream aOutputStream) throws IOException {
        Optional.ofNullable(aOutputStream)
            .orElseThrow(() -> new IllegalArgumentException("passed outputstream cannot be null"))
            .writeUTF(getFormattedValue());
    }

    public List<String> getRootKeys() {
        List<String> list = new ArrayList<>();
        json.keys().forEachRemaining(list::add);
        list.sort(String::compareTo);
        return list;
    }

    public void removeRootKey (String rootKey) {
        json.remove(rootKey);
    }

    public JSONObject getJSONObject() {
        return json;
    }

    public JSONObject getSubJSONObject(String key)  {
        return json.getJSONObject(key);
    }

    public JSONArray getSubJSONArray(String key)  {
        return json.getJSONArray(key);
    }

    public boolean isEmpty() {
        try {
            return getFormattedValue().trim().length() <= 3;
        } catch (JSONException e) {
            logger.error (e, e);
            return true;
        }
    }

    /**
     * Get the value at the desired path as a string.
     * 
     * @param path the java-property-style path to follow to get the value
     * @return the found value or null if none
     * @ whenever anything goes wrong
     */
    public String getValueForPathAsString(String path)  {
        Object value = getValueForPath(path);
        if (value != null) {
            return value.toString();
        }
        return null;
    }

    /**
     * Get the value at the desired path as its native type.
     * 
     * @param path the java-property-style path to follow to get the value
     * @return the found value or null if none
     * @ whenever anything goes wrong
     */
    public Object getValueForPath(String path)  {
        String[] splittedPath = path.split("\\.");
        Object current = json;
        int i;
        for (i = 0; i < splittedPath.length; i++) {
            String nextPath = splittedPath[i];

            if (current == null) {
                return null;
            }

            try {
                current = handleArray(path, current, nextPath);

            } catch (JSONException e) {

                if (exceptionsIsNotFound(e)) {
                    return null;
                }
                logger.error(e, e);
                throw new JSONException (e.getMessage(), e);
            }
        }

        return current;
    }

    boolean exceptionsIsNotFound(JSONException e) {
        return e.getMessage().contains(NOT_FOUND);
    }

    /**
     * Set the desired value at the desired path. Each intermediate property that could be missing is created in
     * order to ensure the success of the operation.
     * 
     * @param path the java-property-style path to follow to get the value
     * @param value the value to set at the desired path
     * @ whenever anything goes wrong
     */
    public void setValueForPath(String path, Object value)  {
        String[] splittedPath = path.split("\\.");
        Object current = json;
        Object parent = null;
        String parentPath = null;
        int i;
        for (i = 0; i < splittedPath.length - 1; i++) {
            String nextPath = splittedPath[i];

            if (current == null) {
                current = createMissingCurrent(parent, parentPath, nextPath);
            }

            parent = current;

            if (current instanceof JSONArray) {
                current = setValueJSONArray(path, current, nextPath);

            } else if (current instanceof JSONObject) {
                current = setValueJSONObject(path, current, nextPath);
            }

            parentPath = nextPath;
        }

        // I know current now points on the parent of the node I have to overwrite
        // Condition : current has not to be null !!
        String lastPath = splittedPath[splittedPath.length - 1];
        if (current == null) {
            current = createMissingCurrent(parent, parentPath, lastPath);
        }
        setValueOnPath(value, current, lastPath);
    }

    private Object setValueJSONObject(String path, Object current, String nextPath) {
        if (Character.isDigit(nextPath.charAt(0))) {
            throw new JSONException(path + ELEMENT_NOT_ARRAY);
        } else {
            try {
                current = ((JSONObject) current).get(nextPath);
            } catch (JSONException e) {
                current = null;
                if (!exceptionsIsNotFound(e)) {
                    logger.error(e, e);
                    throw new JSONException (e.getMessage(), e);
                }
            }
        }
        return current;
    }

    private Object setValueJSONArray(String path, Object current, String nextPath) {
        int index;
        if (Character.isDigit(nextPath.charAt(0))) {
            index = Integer.parseInt(nextPath);
        } else {
            throw new JSONException(path + PATH_NOT_INTEGER);
        }

        try {
            current = ((JSONArray) current).get(index);
        } catch (JSONException e) {
            current = null;
            if (!exceptionsIsNotFound(e)) {
                logger.error(e, e);
                throw new JSONException (e.getMessage(), e);
            }
        }
        return current;
    }

    private Object createMissingCurrent(Object parent, String parentPath, String nextPath)  {
        Object current;
        if (Character.isDigit(nextPath.charAt(0))) {
            current = new JSONArray();
        } else {
            current = new JSONObject();
        }
        if (Character.isDigit(parentPath.charAt(0))) {
            int index = Integer.parseInt(parentPath);
            ((JSONArray) parent).put(index, current);
        } else {
            ((JSONObject) parent).put(parentPath, current);
        }
        return current;
    }

    private void setValueOnPath(Object value, Object current, String path) {
        if (current instanceof JSONObject) {
            if (Character.isDigit(path.charAt(0))) {
                throw new JSONException (path + ELEMENT_NOT_ARRAY);
            } else {
                ((JSONObject) current).put(path, value);
            }

        } else if (current instanceof JSONArray) {

            if (Character.isDigit(path.charAt(0))) {
                int index = Integer.parseInt(path);
                ((JSONArray) current).put(index, value);
            } else {
                throw new JSONException (path + PATH_NOT_INTEGER);
            }

        }
    }

    /**
     * @return the underlying JSON string.
     * @ whenever anything goes wrong
     */
    public String getFormattedValue()  {
        return json.toString(4);
    }

    /**
     * Test for the existence of the given path in the JSON structure.
     * 
     * @param path the java-property-style path to follow to get the value
     * @return true if the target path exists in the JSON structure or false if it doesn't.
     * @ whenever anything goes wrong
     */
    public boolean hasPath(String path)  {
        String[] splittedPath = path.split("\\.");
        Object current = json;
        int i;
        for (i = 0; i < splittedPath.length; i++) {
            String nextPath = splittedPath[i];

            if (current == null) {
                return false;
            }

            try {

                current = handleArray(path, current, nextPath);

            } catch (JSONException e) {

                if (exceptionsIsNotFound(e)) {
                    return false;
                }
                logger.error(e, e);
                throw new JSONException (e.getMessage(), e);
            }
        }

        return true;
    }

    Object handleArray(String path, Object current, String nextPath)  {
        if (current instanceof JSONArray) {

            int index;
            if (Character.isDigit(nextPath.charAt(0))) {
                index = Integer.parseInt(nextPath);
            } else {
                throw new JSONException (path + PATH_NOT_INTEGER);
            }

            current = ((JSONArray) current).get(index);

        } else if (current instanceof JSONObject) {

            if (Character.isDigit(nextPath.charAt(0))) {
                throw new JSONException (path + ELEMENT_NOT_ARRAY);
            } else {
                current = ((JSONObject) current).get(nextPath);
            }

        }
        return current;
    }

    /**
     * 
     * @return a map representation of the same JSON data
     * @ in case anything goes wrong
     */
    public Map<String, Object> toMap()  {
        Map<String, Object> retMap = new HashMap<>();
        toMap(retMap, "", this.json);
        return retMap;
    }

    public Set<String> keySet()  {
        return toMap().keySet();
    }

    private void toMap(Map<String, Object> retMap, String prefix, JSONObject obj)
             {
        for (Iterator<String> keyIt = obj.keys(); keyIt.hasNext(); ) { 
            String key = keyIt.next();
            Object keyVal;
            keyVal = obj.get(key);
            String curPrefix = !StringUtils.isEmpty(prefix) ? prefix + "." + key : key;

            handleValue(retMap, keyVal, curPrefix);
        }
    }

    private void handleValue(Map<String, Object> retMap, Object keyVal, String curPrefix)  {
        if (keyVal instanceof JSONObject) {
            // recursive call
            toMap (retMap, curPrefix, (JSONObject) keyVal);
        } else if (keyVal instanceof JSONArray) {
            // recursive call
            toMap (retMap, curPrefix, (JSONArray) keyVal);
        } else {
            retMap.put(curPrefix, keyVal);
        }
    }

    private void toMap(Map<String, Object> retMap, String prefix, JSONArray arr)
             {
        for (int i = 0; i < arr.length(); i++) {
            Object keyVal;
            keyVal = arr.get(i);
            String curPrefix = !StringUtils.isEmpty(prefix) ? prefix + "." + i : "" + i;

            handleValue(retMap, keyVal, curPrefix);
        }
    }


}
