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


package ch.niceideas.common.utils;

import java.math.BigDecimal;
import java.util.Arrays;

import junit.framework.TestCase;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StringUtilsTest  {

    @Test
    public void testIsIntegerValue() throws Exception {
           
        assertFalse(StringUtils.isNumericValue("=1+2"));        
        assertFalse(StringUtils.isNumericValue("abc"));        
        assertFalse(StringUtils.isNumericValue("a b c"));
        assertFalse(StringUtils.isNumericValue("1 2 3"));
        
        assertTrue(StringUtils.isNumericValue("1"));        
        assertTrue(StringUtils.isNumericValue("123"));
        assertTrue(StringUtils.isNumericValue("+123"));
        assertTrue(StringUtils.isNumericValue("-123"));
        assertTrue(StringUtils.isNumericValue(" - 123"));
    }

    @Test
    public void testIsNumericValue() throws Exception {
           
        assertFalse(StringUtils.isNumericValue("=1+2"));      
        assertFalse(StringUtils.isNumericValue("abc"));
        assertFalse(StringUtils.isNumericValue("a b c"));
        assertFalse(StringUtils.isNumericValue("1 2 3"));
        assertFalse(StringUtils.isNumericValue("1. 1"));
        assertFalse(StringUtils.isNumericValue("e"));
        
        assertFalse(StringUtils.isNumericValue("1.1 e10 e1"));
        
        assertTrue(StringUtils.isNumericValue("1.1"));        
        assertTrue(StringUtils.isNumericValue("-1.1"));
        assertTrue(StringUtils.isNumericValue("+1.1"));
        assertTrue(StringUtils.isNumericValue(" - 1.1"));
        assertTrue(StringUtils.isNumericValue(" - 1.1e10"));
        
        assertTrue(StringUtils.isNumericValue(" - 1.1 e10"));
        
        assertTrue(StringUtils.isNumericValue("1 .1"));
    }

    @Test
    public void testFirstLetterLowerCase() throws Exception {
        assertEquals("abcdf", StringUtils.firstLetterLowerCase("abcdf"));
        assertEquals("abcdf", StringUtils.firstLetterLowerCase("Abcdf"));

        assertEquals("a", StringUtils.firstLetterLowerCase("a"));
        assertEquals("a", StringUtils.firstLetterLowerCase("A"));

        assertEquals("aBCDE", StringUtils.firstLetterLowerCase("aBCDE"));
        assertEquals("aBCDE", StringUtils.firstLetterLowerCase("ABCDE"));
    }

    @Test
    public void testFirstLetterUpperCase() throws Exception {
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
    public void testPad() throws Exception {
        assertEquals("a  ", StringUtils.padRight("a", 3));
        assertEquals("a    ", StringUtils.padRight("a", 5));
        assertEquals("a         ", StringUtils.padRight("a", 10));
    }

    /**
     * Utile dans le cadre des tests
     */
    static class PseudoCGLIBClass extends StringUtilsTest {
    }
    
    @Test 
    public void testGetHexString() throws Exception {
        
        assertEquals ("616161", StringUtils.getHexString("aaa".getBytes("UTF-8")));
        
        assertEquals ("c3a9c3a0c3a8", StringUtils.getHexString("éàè".getBytes("UTF-8")));
        
    }
    
    @Test
    public void testReverseString() throws Exception {
    	
    	assertEquals ("Jerome is name My", StringUtils.reverseString("My name is Jerome"));
    	assertEquals ("string a is this", StringUtils.reverseString("this is a string"));
    	assertEquals ("fox the over jumps dog lazy the", StringUtils.reverseString("the lazy dog jumps over the fox"));
    }

    @Test
    public void testMultipleSearch() throws Exception {
        
        String source = "abc erd ohd the lazy fox jumps over my beautiful self again and is'nt that just sad";
        String[] searches = {"abc", "def", "myself", "fox", "jumps", "jerome", "kehrli"};
        
        System.err.println (Arrays.toString(StringUtils.multipleSearch (source, searches)));        
    }

}
