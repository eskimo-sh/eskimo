// ============================================================================
//
// Copyright (c) 2010 - 2011, niceideas.ch - Jerome Kehrli
//
// You may distribute this code under the terms of the GNU LGPL license
// (http://www.gnu.org/licenses/lgpl.html). [^]
//
// ===================================================================


package ch.niceideas.common.exceptions;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * FIXME Document me
 * <p />
 * 
 * <b>
 * The design of this class as well as in general the ideas expressed here and the original implementation of these
 * ideas are the work of Mr. Thomas Beck. <br />
 * I would like to take this opportunity to thank him for the privilege and the great pleasure it has been to work
 * during those years with the very best software engineer I have ever met in my career.
 * </b>
 */
public class CommonBusinessException extends Exception {

    static final long serialVersionUID = -3387112211112229248L;

    public CommonBusinessException() {
    }

    public CommonBusinessException(String message) {
        super(message);
    }

    public CommonBusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    public CommonBusinessException(Throwable cause) {
        super(cause);
    }
}
