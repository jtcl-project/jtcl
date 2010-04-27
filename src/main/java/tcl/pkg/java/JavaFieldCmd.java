/*
 * JavaFieldCmd.java --
 *
 *	Implements the built-in "java::field" command.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 * RCS: @(#) $Id: JavaFieldCmd.java,v 1.2 1999/05/09 21:53:38 dejong Exp $
 *
 */

package tcl.pkg.java;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclObject;

/*
 * This class implements the built-in "java::field" command.
 */

public class JavaFieldCmd implements Command {

	/*----------------------------------------------------------------------
	 *
	 * cmdProc --
	 *
	 * 	This procedure is invoked to process the "java::field" Tcl
	 * 	command.  See the user documentation for details on what they
	 * 	do.
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
		boolean query;
		int objIndex;
		boolean convert;

		if (argv.length < 3) {
			throw new TclException(interp, usage(argv[0]));
		}

		// Check the validity of the arguments. N.B., the -noconvert flag
		// is allowed only in value query.

		String arg1 = argv[1].toString();
		if ((arg1.length() >= 2) && ("-noconvert".startsWith(arg1))) {
			convert = false;
			objIndex = 2;

			if (argv.length == 4) {
				query = true;
			} else {
				throw new TclException(interp, usage(argv[0]));
			}
		} else {
			convert = true;
			objIndex = 1;

			if (argv.length == 3) {
				query = true;
			} else if ((argv.length % 2) == 0) {
				query = false;
			} else {
				throw new TclException(interp, usage(argv[0]));
			}
		}

		if (query) {
			// Query one field.

			interp.setResult(JavaInvoke.getField(interp, argv[objIndex],
					argv[objIndex + 1], convert));
		} else {
			// Set one or more fields.

			for (int i = objIndex + 1; i < argv.length; i += 2) {
				JavaInvoke.setField(interp, argv[objIndex], argv[i],
						argv[i + 1]);
			}

			interp.resetResult();
		}
	}

	/*----------------------------------------------------------------------
	 *
	 * usage --
	 *
	 *	Returns the usage string for the java::field command.
	 *
	 * Results:
	 *	Returns the usage string for the java::field command.
	 *
	 * Side effects:
	 *	None.
	 *
	 *----------------------------------------------------------------------
	 */

	private static final String usage(TclObject cmd) // The command name.
	{
		// FIXME : unneeded extra code

		// This does not conform to docs
		/*
		 * return "wrong # args: should be \"" + cmd +
		 * " ?-noconvert? object field\" or \"" + cmd +
		 * " object field value ?field value ...?\"";
		 */

		/*
		 * return "wrong # args: should be \"" + cmd +
		 * " ?-noconvert? objOrClass fieldSignature" +
		 * " ?value fieldSignature value ...?";
		 */

		return "wrong # args: should be \"" + cmd
				+ " ?-noconvert? objOrClass field" + " ?value field value ...?";

	}

} // end JavaFieldCmd

