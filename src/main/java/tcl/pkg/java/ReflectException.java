/*
 * ReflectException.java --
 *
 *	The file implements the handling of the Exception's caught
 *	while invoking the Reflection API.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: ReflectException.java,v 1.4 2002/12/30 22:49:24 mdejong Exp $
 *
 */
package tcl.pkg.java;

import java.lang.reflect.InvocationTargetException;

import tcl.lang.Interp;
import tcl.lang.TCL;
import tcl.lang.TclException;
import tcl.lang.TclList;
import tcl.lang.TclObject;
import tcl.lang.TclRuntimeError;
import tcl.lang.TclString;

/**
 * This class handles Exception's caught while invoking the Reflection API. It
 * records the string form of the Exception into the result of the interpreter
 * and stores the actual Exception object in the errorCode of the interpreter.
 */

class ReflectException extends TclException {

	// The throwable object passed to the constructor
	Throwable throwable;

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * ReflectException --
	 * 
	 * Records the string form of the Exception into the result of the
	 * interpreter and stores the actual Exception object in the errorCode of
	 * the interpreter.
	 * 
	 * Results: None.
	 * 
	 * Side effects: If interp is non-null, the interpreter result and errorCode
	 * are modified
	 * 
	 * ----------------------------------------------------------------------
	 */

	ReflectException(Interp interp, // Current interpreter. May be null.
			// If non-null, its result object and
			// errorCode variable will be changed.
			Throwable e) // The exception to record in the interp.
	{
		super(TCL.ERROR);

		if (throwable instanceof TclException)
			throw new TclRuntimeError(
					"don't wrap TclException in ReflectException");

		if (e instanceof InvocationTargetException) {
			// The original exception is wrapped in InvocationTargetException
			// for us by the Java Reflection API. This fact doesn't provide
			// any interesting information to script writers, so we'll
			// unwrap it so that is more convenient for scripts to
			// figure out the exception.

			throwable = ((InvocationTargetException) e).getTargetException();
		} else {
			throwable = e;
		}

		if (interp != null) {
			TclObject errCode = TclList.newInstance();
			errCode.preserve();

			try {
				TclList.append(interp, errCode, TclString.newInstance("JAVA"));
				TclList.append(interp, errCode, ReflectObject.newInstance(
						interp, Throwable.class, throwable));
			} catch (TclException tclex) {
				throw new TclRuntimeError("unexpected TclException: " + tclex);
			}

			// interp.setErrorCode() may fail silently if there is an bad
			// trace on the "errorCode" variable. If that happens, the
			// errCode list we created above may hang around
			// forever. Hence, we added the pair of preserve() + release()
			// calls to ensure that errCode will get cleaned up if
			// interp.setErrorCode() fails.

			interp.setErrorCode(errCode);
			errCode.release();

			interp.setResult(throwable.toString());
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getThrowable --
	 * 
	 * Return the Throwable object that was recorded into the errorCode global
	 * variable. Invoking this method should be significantly faster than
	 * getting the value of the errorCode variable.
	 * 
	 * Results: Return a Throwable object.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	Throwable getThrowable() {
		return throwable;
	}

} // end ReflectException

