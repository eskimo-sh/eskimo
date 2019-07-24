/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

import java.util.*;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.util.StringUtils;

/**
 * This is a wrapper around a JSON string which enables one to get the value at a specific node, change it, test if
 * a specific node exists, etc.
 * <p />
 * 
 * The syntax usesd for node identification follows the same principle than Java properties paths. <br />
 * For instance. If the root node contains an object "aaa" which contains an array "bbb", one can get the value at
 * the third position of this array by using the following path: <code>aaa.bbb.3</code>.
 */
public class JsonWrapper {

    private static final Logger logger = Logger.getLogger(JsonWrapper.class);

    private final JSONObject json;

    public JsonWrapper(JSONObject json) throws JSONException {
        this.json = json;
    }

    /**
     * Build a new JsonWrapper around the given JSON string.
     * 
     * @param jsonString the string to wrap.
     * @throws JSONException whenever anything goes wrong
     */
    public JsonWrapper(String jsonString) throws JSONException {
        super();

        try {
            json = new JSONObject(jsonString);
        } catch (JSONException e) {
            logger.error(e, e);
            throw new JSONException (e.getMessage(), e);
        }
    }

    public List<String> getRootKeys() {
        List<String> list = new ArrayList<>();
        json.keys().forEachRemaining(list::add);
        return list.stream().sorted().collect(Collectors.toList());
    }

    public void removeRootKey (String rootKey) {
        json.remove(rootKey);
    }

    public JSONObject getJSONObject() {
        return json;
    }

    public JSONObject getSubJSONObject(String key)throws JSONException  {
        return json.getJSONObject(key);
    }

    public JSONArray getSubJSONArray(String key)throws JSONException  {
        return json.getJSONArray(key);
    }

    /**
     * Get the value at the desired path as a string.
     * 
     * @param path the java-property-style path to follow to get the value
     * @return the found value or null if none
     * @throws JSONException whenever anything goes wrong
     */
    public String getValueForPathAsString(String path) throws JSONException {
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
     * @throws JSONException whenever anything goes wrong
     */
    public Object getValueForPath(String path) throws JSONException {
        String[] splittedPath = path.split("\\.");
        Object current = json;
        int i = 0;
        for (i = 0; i < splittedPath.length; i++) {
            String nextPath = splittedPath[i];

            if (current == null) {
                return null;
            }

            try {

                current = handleArray(path, current, nextPath);

            } catch (JSONException e) {

                if (e.getMessage().contains("not found.")) {
                    //logger.debug(e, e);
                    return null;
                }
                logger.error(e, e);
                throw new JSONException (e.getMessage(), e);
            }
        }

        return current;
    }

    /**
     * Set the desired value at the desired path. Each intermediate property that could be missing is created in
     * order to ensure the success of the operation.
     * 
     * @param path the java-property-style path to follow to get the value
     * @param value the value to set at the desired path
     * @throws JSONException whenever anything goes wrong
     */
    public void setValueForPath(String path, Object value) throws JSONException {
        String[] splittedPath = path.split("\\.");
        Object current = json;
        Object parent = null;
        String parentPath = null;
        int i = 0;
        for (i = 0; i < splittedPath.length - 1; i++) {
            String nextPath = splittedPath[i];

            try {

                if (current == null) {
                    current = createMissingCurrent(parent, parentPath, nextPath);
                }

                parent = current;

                if (current instanceof JSONArray) {

                    int index = -1;
                    if (Character.isDigit(nextPath.charAt(0))) {
                        index = Integer.valueOf(nextPath);
                    } else {
                        throw new JSONException ("Path " + path
                                + " contains an array but the concerned path is not an integer");
                    }

                    try {
                        current = ((JSONArray) current).get(index);
                    } catch (JSONException e) {
                        current = null;
                        if (!e.getMessage().contains("not found.")) {
                            logger.error(e, e);
                            throw new JSONException (e.getMessage(), e);
                        }
                    }

                } else if (current instanceof JSONObject) {

                    if (Character.isDigit(nextPath.charAt(0))) {
                        throw new JSONException (
                                "Path "
                                        + path
                                        + " contains contains an element which is not an array but asked next path is an integer");
                    } else {
                        try {
                            current = ((JSONObject) current).get(nextPath);
                        } catch (JSONException e) {
                            current = null;
                            if (!e.getMessage().contains("not found.")) {
                                logger.error(e, e);
                                throw new JSONException (e.getMessage(), e);
                            }
                        }
                    }

                }

            } catch (JSONException e) {
                logger.error(e, e);
                throw new JSONException (e.getMessage(), e);
            }

            parentPath = nextPath;
        }

        try {
            // I know current now points on the parent of the node I have to overwrite
            // Condition : current has not to be null !!
            String lastPath = splittedPath[splittedPath.length - 1];
            if (current == null) {
                current = createMissingCurrent(parent, parentPath, lastPath);
            }
            setValueOnPath(value, current, lastPath);
        } catch (JSONException e) {
            logger.error(e, e);
            throw new JSONException (e.getMessage(), e);
        }
    }

    private Object createMissingCurrent(Object parent, String parentPath, String nextPath) throws JSONException {
        Object current;
        if (Character.isDigit(nextPath.charAt(0))) {
            current = new JSONArray();
        } else {
            current = new JSONObject();
        }
        if (Character.isDigit(parentPath.charAt(0))) {
            int index = Integer.valueOf(parentPath);
            ((JSONArray) parent).put(index, current);
        } else {
            ((JSONObject) parent).put(parentPath, current);
        }
        return current;
    }

    private void setValueOnPath(Object value, Object current, String path) throws JSONException,
            JSONException {
        if (current instanceof JSONObject) {
            if (Character.isDigit(path.charAt(0))) {
                throw new JSONException ("Path element " + path
                        + " contains contains an element which is not an array but asked next path is an integer");
            } else {
                ((JSONObject) current).put(path, value);
            }

        } else if (current instanceof JSONArray) {

            if (Character.isDigit(path.charAt(0))) {
                int index = Integer.valueOf(path);
                ((JSONArray) current).put(index, value);
            } else {
                throw new JSONException ("Path element " + path
                        + " contains an array but the concerned path is not an integer");
            }

        }
    }

    /**
     * @return the underlying JSON string.
     * @throws JSONException whenever anything goes wrong
     */
    public String getFormattedValue() throws JSONException {
        try {
            return json.toString(4);
        } catch (JSONException e) {
            logger.error(e, e);
            throw new JSONException (e.getMessage(), e);

        }
    }

    /**
     * Test for the existence of the given path in the JSON structure.
     * 
     * @param path the java-property-style path to follow to get the value
     * @return true if the target path exists in the JSON structure or false if it doesn't.
     * @throws JSONException whenever anything goes wrong
     */
    public boolean hasPath(String path) throws JSONException {
        String[] splittedPath = path.split("\\.");
        Object current = json;
        int i = 0;
        for (i = 0; i < splittedPath.length; i++) {
            String nextPath = splittedPath[i];

            if (current == null) {
                return false;
            }

            try {

                current = handleArray(path, current, nextPath);

            } catch (JSONException e) {

                if (e.getMessage().contains("not found.")) {
                    //logger.debug(e, e);
                    return false;
                }
                logger.error(e, e);
                throw new JSONException (e.getMessage(), e);
            }
        }

        return true;
    }

    Object handleArray(String path, Object current, String nextPath) throws JSONException {
        if (current instanceof JSONArray) {

            int index = -1;
            if (Character.isDigit(nextPath.charAt(0))) {
                index = Integer.valueOf(nextPath);
            } else {
                throw new JSONException ("Path " + path
                        + " contains an array but the concerned path is not an integer");
            }

            current = ((JSONArray) current).get(index);

        } else if (current instanceof JSONObject) {

            if (Character.isDigit(nextPath.charAt(0))) {
                throw new JSONException (
                        "Path "
                                + path
                                + " contains contains an element which is not an array but asked next path is an integer");
            } else {
                current = ((JSONObject) current).get(nextPath);
            }

        }
        return current;
    }

    /**
     * 
     * @return a map representation of the same JSON data
     * @throws JSONException in case anything goes wrong
     */
    public Map<String, Object> toMap() throws JSONException {
        Map<String, Object> retMap = new HashMap<String, Object>();        
        toMap(retMap, "", this.json);
        return retMap;
    }

    public Set<String> keySet() throws JSONException {
        return toMap().keySet();
    }

    private void toMap(Map<String, Object> retMap, String prefix, JSONObject obj)
            throws JSONException {
        for (Iterator<String> keyIt = obj.keys(); keyIt.hasNext(); ) { 
            String key = keyIt.next();
            Object keyVal;
            try {
                keyVal = obj.get(key);
            } catch (JSONException e) {
                logger.error (e, e);
                throw new JSONException (e.getMessage(), e);
            }
            String curPrefix = !StringUtils.isEmpty(prefix) ? prefix + "." + key : key;

            handleValue(retMap, keyVal, curPrefix);
        }
    }

    private void handleValue(Map<String, Object> retMap, Object keyVal, String curPrefix) throws JSONException {
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
            throws JSONException {
        for (int i = 0; i < arr.length(); i++) {
            Object keyVal;
            try {
                keyVal = arr.get(i);
            } catch (JSONException e) {
                logger.error (e, e);
                throw new JSONException (e.getMessage(), e);
            }
            String curPrefix = !StringUtils.isEmpty(prefix) ? prefix + "." + i : "" + i;

            handleValue(retMap, keyVal, curPrefix);
        }
    }


}
