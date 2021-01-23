// ============================================================================
//
// Copyright (c) 2010 - 2011, niceideas.ch - Jerome Kehrli
//
// You may distribute this code under the terms of the GNU LGPL license
// (http://www.gnu.org/licenses/lgpl.html). [^]
//
// ===================================================================


package ch.niceideas.common.exceptions;



public class WrappedRTException extends CommonRTException {

    private static final long serialVersionUID = -8017722618562184690L;

    /**
     * Create a new instance of CommonRTException
     */
    public WrappedRTException() {}

    /**
     * Create a new instance of CommonRTException
     *
     * @param message the initial error message to set
     */
    public WrappedRTException(String message) {
        super(message);
    }

    /**
     * Create a new instance of CommonRTException
     *
     * @param message the initial error message to set
     * @param cause the underlying cause of the error
     */
    public WrappedRTException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create a new instance of CommonRTException
     *
     * @param cause the underlying cause of the error
     */
    public WrappedRTException(Throwable cause) {
        super(cause);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Throwable initCause(Throwable cause) {
        return super.initCause(cause);
    }


}
