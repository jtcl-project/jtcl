/*
 * JavaNullCmd.java --
 *
 *	Implements the built-in "java::null" command.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 * RCS: @(#) $Id: JavaNullCmd.java,v 1.2 1999/05/09 22:19:45 dejong Exp $
 *
 */

package tcl.pkg.java;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclObject;

/**
 * Implements the built-in "java::null" command.
 */

public class JavaNullCmd implements Command {

	/*----------------------------------------------------------------------
	 *
	 * cmdProc --
	 *
	 * 	This procedure is invoked to process the "java::null" Tcl
	 * 	command. See the user documentation for details on what it
	 * 	does.
	 *
	 * Results:
	 *	None.
	 *
	 * Side effects:
	 *	A standard Tcl result is stored in the interpreter.
	 *
	 *----------------------------------------------------------------------
	 */

	public void cmdProc(Interp interp, // Current interpreter.
			TclObject argv[]) // Argument list.
			throws TclException {
		interp.setResult(ReflectObject.newInstance(interp, null, null));
	}

} // end JavaNullCmd

