/*
 * JavaGetInterpCmd.java --
 *
 *	Implements the built-in "java::getinterp" command.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 * RCS: @(#) $Id: JavaGetInterpCmd.java,v 1.1.1.1 1998/10/14 21:09:14 cvsadmin Exp $
 */

package tcl.pkg.java;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclObject;

/*
 * Implements the built-in "java::getinterp" command.
 */

public class JavaGetInterpCmd implements Command {

	/*----------------------------------------------------------------------
	 *
	 * cmdProc --
	 *
	 * 	This procedure is invoked to process the "java::getInterp" Tcl
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
			throws TclException // A standard Tcl exception.
	{
		interp.setResult(ReflectObject
				.newInstance(interp, Interp.class, interp));
	}

} // end JavaGetInterpCmd

