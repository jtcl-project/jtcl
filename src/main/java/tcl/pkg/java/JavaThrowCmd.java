/*
 * ThrowCmd.java --
 *
 *	Implements the java::throw command.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 * RCS: @(#) $Id: JavaThrowCmd.java,v 1.2 2002/12/30 06:28:06 mdejong Exp $
 */

package tcl.pkg.java;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;

/*
 * This class implements the built-in "java::throw" command in Tcl.
 */

public class JavaThrowCmd implements Command {

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * cmdProc --
	 * 
	 * This procedure is invoked as part of the Command interface to process the
	 * "java::throw" Tcl command. It receives a Throwable object and throws it
	 * by encapsulating the Throwable inside a ReflectException, which inherits
	 * from TclException. If the Throwable is already of type TclException,
	 * throw it after resetting the interp result to the TclException message.
	 * 
	 * Results: None.
	 * 
	 * Side effects: Can change the interp result, errorInfo, and errorCode.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public void cmdProc(Interp interp, // Current interpreter.
			TclObject argv[]) // Argument list.
			throws TclException // Standard Tcl exception.
	{
		if (argv.length != 2) {
			throw new TclNumArgsException(interp, 1, argv, "throwableObj");
		}

		Object javaObj = null;
		javaObj = ReflectObject.get(interp, argv[1]);

		if (!(javaObj instanceof Throwable)) {
			throw new TclException(interp,
					"bad object: must be an instance of Throwable");
		} else if (javaObj instanceof TclException) {
			TclException te = (TclException) javaObj;
			interp.setResult(te.getMessage());
			throw te;
		} else {
			throw new ReflectException(interp, (Throwable) javaObj);
		}
	}

} // end JavaThrowCmd

