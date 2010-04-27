/* 
 * JavaIsNullCmd.java --
 *
 *	This class implements the built-in "java::isnull" command in Tcl.
 *
 * Copyright (c) 1998 by Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and redistribution
 * of this file, and for a DISCLAIMER OF ALL WARRANTIES.
 *
 * RCS: @(#) $Id: JavaIsNullCmd.java,v 1.1.1.1 1998/10/14 21:09:14 cvsadmin Exp $
 */

package tcl.pkg.java;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;

public class JavaIsNullCmd implements Command {

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * cmdProc --
	 * 
	 * This procedure is invoked to process the "java::isnull" Tcl command. See
	 * the user documentation for details on what it does.
	 * 
	 * Results: None.
	 * 
	 * Side effects: A standard Tcl result is stored in the interpreter.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public void cmdProc(Interp interp, // The current interpreter.
			TclObject argv[]) // The command arguments.
			throws TclException // Standard Tcl Exception.
	{
		if (argv.length != 2) {
			throw new TclNumArgsException(interp, 1, argv, "object");
		}

		Object obj = null;

		obj = ReflectObject.get(interp, argv[1]);

		if (obj == null) {
			interp.setResult(true);
		} else {
			interp.setResult(false);
		}
	}

} // end JavaIsNullCmd

