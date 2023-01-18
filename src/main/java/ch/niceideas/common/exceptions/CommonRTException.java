// ============================================================================
//
// Copyright (c) 2010 - 2011, niceideas.ch - Jerome Kehrli
//
// You may distribute this code under the terms of the GNU LGPL license
// (http://www.gnu.org/licenses/lgpl.html). [^]
//
// ===================================================================


package ch.niceideas.common.exceptions;



/**
 * A base class for RuntimeException in the niceideas-commons library.
 */
public class CommonRTException extends RuntimeException {

    private static final long serialVersionUID = -8017722618562184690L;

    /**
     * Create a new instance of CommonRTException
     * 
     * @param message the initial error message to set
     */
    public CommonRTException(String message) {
        super(message);
    }

    /**
     * Create a new instance of CommonRTException
     * 
     * @param cause the underlying cause of the error
     */
    public CommonRTException(Throwable cause) {
        super(cause);
    }

}
