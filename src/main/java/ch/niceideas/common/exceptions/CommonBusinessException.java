// ============================================================================
//
// Copyright (c) 2010 - 2011, niceideas.ch - Jerome Kehrli
//
// You may distribute this code under the terms of the GNU LGPL license
// (http://www.gnu.org/licenses/lgpl.html). [^]
//
// ===================================================================


package ch.niceideas.common.exceptions;

public class CommonBusinessException extends Exception {

    static final long serialVersionUID = -3387112211112229248L;

    public CommonBusinessException(String message) {
        super(message);
    }

    public CommonBusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    public CommonBusinessException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
