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


package ch.niceideas.common.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class StringUtilsTest  {

    private static final String SOURCE = "abc erd ohd the ad lazy fox jumps over ad my beautiful self again and is'nt that just sad nope ?";

    @Test
    public void testIsIntegerValue() {
           
        assertFalse(StringUtils.isIntegerValue("=1+2"));
        assertFalse(StringUtils.isIntegerValue("abc"));
        assertFalse(StringUtils.isIntegerValue("a b c"));
        assertFalse(StringUtils.isIntegerValue("1 2 3"));
        assertFalse(StringUtils.isIntegerValue(null));
        
        assertTrue(StringUtils.isIntegerValue("1"));
        assertTrue(StringUtils.isIntegerValue("123"));
        assertTrue(StringUtils.isIntegerValue("+123"));
        assertTrue(StringUtils.isIntegerValue("-123"));
        assertTrue(StringUtils.isIntegerValue(" - 123"));
    }

    @Test
    public void testIsNumericValue() {
           
        assertFalse(StringUtils.isNumericValue("=1+2"));      
        assertFalse(StringUtils.isNumericValue("abc"));
        assertFalse(StringUtils.isNumericValue("a b c"));
        assertFalse(StringUtils.isNumericValue("1 2 3"));
        assertFalse(StringUtils.isNumericValue("1. 1"));
        assertFalse(StringUtils.isNumericValue("e"));
        
        assertFalse(StringUtils.isNumericValue("1.1 e10 e1"));

        assertFalse(StringUtils.isNumericValue(null));
        
        assertTrue(StringUtils.isNumericValue("1.1"));        
        assertTrue(StringUtils.isNumericValue("-1.1"));
        assertTrue(StringUtils.isNumericValue("+1.1"));
        assertTrue(StringUtils.isNumericValue(" - 1.1"));
        assertTrue(StringUtils.isNumericValue(" - 1.1e10"));
        
        assertTrue(StringUtils.isNumericValue(" - 1.1 e10"));
        
        assertTrue(StringUtils.isNumericValue("1 .1"));
    }

    @Test
    public void testFirstLetterLowerCase() {
        assertEquals("abcdf", StringUtils.firstLetterLowerCase("abcdf"));
        assertEquals("abcdf", StringUtils.firstLetterLowerCase("Abcdf"));

        assertEquals("a", StringUtils.firstLetterLowerCase("a"));
        assertEquals("a", StringUtils.firstLetterLowerCase("A"));

        assertEquals("aBCDE", StringUtils.firstLetterLowerCase("aBCDE"));
        assertEquals("aBCDE", StringUtils.firstLetterLowerCase("ABCDE"));
    }

    @Test
    public void testFirstLetterUpperCase() {
        assertEquals("Abcdf", StringUtils.firstLetterUpperCase("abcdf"));
        assertEquals("Abcdf", StringUtils.firstLetterUpperCase("Abcdf"));

        assertEquals("A", StringUtils.firstLetterUpperCase("a"));
        assertEquals("A", StringUtils.firstLetterUpperCase("A"));

        assertEquals("ABCDE", StringUtils.firstLetterUpperCase("aBCDE"));
        assertEquals("ABCDE", StringUtils.firstLetterUpperCase("ABCDE"));
    }

    @Test
    public void testTrimTrailingSpaces() {
        String s1 = "xxxxxxxxxx";
        assertEquals(s1, StringUtils.trimTrailingSpaces(s1));
        s1 = "";
        assertEquals(s1, StringUtils.trimTrailingSpaces(s1));
        s1 = "xx ";
        assertEquals("xx", StringUtils.trimTrailingSpaces(s1));
        s1 = " xx ";
        assertEquals(" xx", StringUtils.trimTrailingSpaces(s1));
        s1 = "  d d d d d d d d d dewd efd ewf ewfe wfew few f                   ";
        assertEquals("  d d d d d d d d d dewd efd ewf ewfe wfew few f", StringUtils.trimTrailingSpaces(s1));

        assertNull(StringUtils.trimTrailingSpaces((String)null));
    }

    @Test
    public void testToBigDecimal() {
        assertEquals(new BigDecimal("0"), StringUtils.toBigDecimal("", new BigDecimal("0")));
        assertEquals(new BigDecimal("1"), StringUtils.toBigDecimal(" ", new BigDecimal("1")));
        assertEquals(new BigDecimal("2"), StringUtils.toBigDecimal(null, new BigDecimal("2")));

        assertEquals(new BigDecimal("100"), StringUtils.toBigDecimal("100", new BigDecimal("0")));
        assertEquals(new BigDecimal("200"), StringUtils.toBigDecimal("  200", new BigDecimal("0")));
        assertEquals(new BigDecimal("300"), StringUtils.toBigDecimal("300  ", new BigDecimal("0")));
    }

    @Test
    public void testPadRight() {
        assertEquals("a  ", StringUtils.padRight("a", 3));
        assertEquals("a    ", StringUtils.padRight("a", 5));
        assertEquals("a         ", StringUtils.padRight("a", 10));
        assertEquals("          ", StringUtils.padRight(null, 10));
    }

    @Test
    public void testPadLeft() {
        assertEquals("  a", StringUtils.padLeft("a", 3));
        assertEquals("    a", StringUtils.padLeft("a", 5));
        assertEquals("         a", StringUtils.padLeft("a", 10));
        assertEquals("          ", StringUtils.padLeft(null, 10));
    }

    @Test 
    public void testGetHexString() {
        
        assertEquals ("616161", StringUtils.getHexString("aaa".getBytes(StandardCharsets.UTF_8)));
        
        assertEquals ("c3a9c3a0c3a8", StringUtils.getHexString("éàè".getBytes(StandardCharsets.UTF_8)));
        
    }
    
    @Test
    public void testReverseString() {
    	
    	assertEquals ("Jerome is name My", StringUtils.reverseString("My name is Jerome"));
    	assertEquals ("string a is this", StringUtils.reverseString("this is a string"));
    	assertEquals ("fox the over jumps dog lazy the", StringUtils.reverseString("the lazy dog jumps over the fox"));
    }

    @Test
    public void testMultipleSearch() {
        
        String source = "abc erd ohd the lazy fox jumps over my beautiful self again and is'nt that just sad";
        String[] searches = {"abc", "def", "myself", "fox", "jumps", "jerome", "kehrli"};
        
        assertEquals("[0, -1, -1, 21, 25, -1, -1]", Arrays.toString(StringUtils.multipleSearch (source, searches)));
    }

    @Test
    public void testLastIndexOf() {


        assertEquals(88, StringUtils.lastIndexOf(SOURCE, "ad".toCharArray()));
    }

    @Test
    public void testUrlEncodeDecode() {
        String sourceString = "test encode\n with & some nutty?characters!";
        String encoded = StringUtils.urlEncode(sourceString);
        assertEquals("test+encode%0A+with+%26+some+nutty%3Fcharacters%21", encoded);
        assertEquals(sourceString, StringUtils.urlDecode(encoded));
    }

    @Test
    public void testToUnicodeLiteral() {
        assertEquals ("\\u0054\\u006F\\u00E9\\u00E0\\u0042\\u0065\\u00E8\\u00FC\\u0049\\u006D\\u0070\\u006C\\u0065\\u006D\\u0065\\u006E\\u0074\\u0065\\u0064", StringUtils.toUnicodeLiteral("ToéàBeèüImplemented"));
    }

    @Test
    public void testTrimToSize() {
        assertEquals("abc erd ...", StringUtils.trimToSize(SOURCE, 15));
        assertEquals("abc erd ...", StringUtils.trimToSize(SOURCE, 14));
        assertEquals("abc erd ...", StringUtils.trimToSize(SOURCE, 12));
    }

    @Test
    public void testMultipleSearches() {
        int[] results = StringUtils.multipleSearch(SOURCE, new String[]{"abc", "ad", "self"});
        StringBuilder sb = new StringBuilder();
        Arrays.stream(results).forEach(intVal -> sb.append(intVal).append("_"));
        assertEquals("0_87_55_", sb.toString());
    }
}
