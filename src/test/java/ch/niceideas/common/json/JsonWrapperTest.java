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

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;


public class JsonWrapperTest {

    private static final String jsonYahooExpectedMap = "{submitForm=, ch_niceideas_airXCell_UI_components_workspace_dynforms_DynFormWrapper_2_quandMod_select_assets_resultList_IBM_showData.0=on, assetShowLoaded=, ch_niceideas_airXCell_UI_components_workspace_dynforms_DynFormWrapper_2_quandMod_select_assets_resultList_^IBMSY_plotData.0=on, ch_niceideas_airXCell_UI_components_workspace_dynforms_DynFormWrapper_2_quandMod_select_assets_resultList_IBM.HA_showData.0=on, assetSearchValidate=, ch_niceideas_airXCell_UI_components_workspace_dynforms_DynFormWrapper_2_quandMod_select_assets_resultList_IBM.L_infoData.0=on, ch_niceideas_airXCell_UI_components_workspace_dynforms_DynFormWrapper_2_quandMod_select_assets_resultList_IBM.HM_infoData.0=on, ch_niceideas_airXCell_UI_components_workspace_dynforms_DynFormWrapper_2_quandMod_select_assets_resultList_IBM.MU_plotData.0=on, assetSearchValue=IBM}";
    
    private String sourceJSONInput;
    private String sourceJSONOutput;
    private String sourceJSONSearchYahoo;

    @BeforeEach
    public void setUp() throws Exception {
        InputStream sourceJSONStream = ResourceUtils.getResourceAsStream("JsonWrapperTest/in.json");
        assertNotNull(sourceJSONStream);
        sourceJSONInput = StreamUtils.getAsString(sourceJSONStream, StandardCharsets.UTF_8);
        sourceJSONStream.close();

        sourceJSONStream = ResourceUtils.getResourceAsStream("JsonWrapperTest/out.json");
        assertNotNull(sourceJSONStream);
        sourceJSONOutput = StreamUtils.getAsString(sourceJSONStream, StandardCharsets.UTF_8);
        sourceJSONStream.close();

        InputStream sourceJSONSearchYahootream = ResourceUtils.getResourceAsStream("JsonWrapperTest/yahooSearchIBMJson.json");
        assertNotNull(sourceJSONSearchYahootream);
        sourceJSONSearchYahoo = StreamUtils.getAsString(sourceJSONSearchYahootream, StandardCharsets.UTF_8);
        sourceJSONSearchYahootream.close();
    }

    @Test
    public void testArtificialSerializeDeserialize() {

        JsonWrapper inpWrapper = new JsonWrapper(sourceJSONInput);

        String tempJSON = inpWrapper.getFormattedValue();
        JsonWrapper inpWrapper2 = new JsonWrapper(tempJSON);

        assertEquals(tempJSON, inpWrapper2.getFormattedValue());
    }

    @Test
    public void testParserWithInput() {

        JsonWrapper parser = new JsonWrapper(sourceJSONInput);

        assertNull(parser.getValueForPath("a.tuguduChapeauPointu"));
        assertEquals("ABCD", parser.getValueForPath("name"));
        assertEquals("20.08.2010", parser.getValueForPath("date"));
        assertEquals("12346579", parser.getValueForPath("underObject.value"));
    }

    @Test
    public void testParserWithOutput() {

        JsonWrapper parser = new JsonWrapper(sourceJSONOutput);

        assertEquals("ABCD", parser.getValueForPath("context.operation"));
        assertEquals("123456789", parser.getValueForPath("dto.contentObject.identifier.value"));
        assertEquals("2BB", parser.getValueForPath("dto.contentObject.internalObject.value"));

        assertEquals("132'456.789", parser.getValueForPath("dto.contentObject.netAmount.amount.formattedValue"));

        assertNull(parser.getValueForPath("dto.contentObject.netAmount.amount.sarace"));

        assertNull(parser.getValueForPath("dto.identifier.a.b.c.d"));
    }

    @Test
    public void testSetValue() {

        JsonWrapper parser = new JsonWrapper(sourceJSONOutput);

        assertEquals("ABCD", parser.getValueForPath("context.operation"));

        parser.setValueForPath("context.operation", "TUGUDU");

        assertEquals("TUGUDU", parser.getValueForPath("context.operation"));

        parser.setValueForPath("context.operation2.newProp", "tada");

        assertEquals("tada", parser.getValueForPath("context.operation2.newProp"));
    }

    @Test
    public void testSetValueWithArray() {

        JsonWrapper parser = new JsonWrapper(sourceJSONInput);

        assertNull(parser.getValueForPath("a.firstObject.internalArray1.1"));

        parser.setValueForPath("a.firstObject.internalArray1.1", "TUGUDU");

        assertEquals("TUGUDU", parser.getValueForPath("a.firstObject.internalArray1.1"));

        assertNull(parser.getValueForPath("a.firstObject.internalArray1.2.newProp"));

        parser.setValueForPath("a.firstObject.internalArray1.2.newProp", "tada");

        assertEquals("tada", parser.getValueForPath("a.firstObject.internalArray1.2.newProp"));
    }

    @Test
    public void testToMap() {

        JsonWrapper parser = new JsonWrapper(sourceJSONSearchYahoo);

        assertEquals (jsonYahooExpectedMap, parser.toMap().toString());
    }

    @Test
    public void testIsEmpty() {
        assertTrue(new JsonWrapper("{}").isEmpty());
        assertTrue(JsonWrapper.empty().isEmpty());
        assertFalse(new JsonWrapper("{\"abc\" : \"test\"}").isEmpty());
    }

    @Test
    public void testFullSerializeDeserialize() throws Exception {

        JsonWrapper inpWrapper = new JsonWrapper(sourceJSONInput);

        byte[] serial = serialize(inpWrapper);
        JsonWrapper inpWrapper2 = (JsonWrapper) deserialize(serial);

        assertEquals(inpWrapper.getFormattedValue(), inpWrapper2.getFormattedValue());
    }

    private static byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ObjectOutputStream o = new ObjectOutputStream(b);
        o.writeObject(obj);
        return b.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        ByteArrayInputStream b = new ByteArrayInputStream(bytes);
        ObjectInputStream o = new ObjectInputStream(b);
        return o.readObject();
    }

}
