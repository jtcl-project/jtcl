/*
 * JavaPropCmd.java --
 *
 *	Implements the built-in "java::prop" command.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 * RCS: @(#) $Id: JavaPropCmd.java,v 1.3 1999/05/09 22:23:46 dejong Exp $
 *
 */

package tcl.pkg.java;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclObject;

/**
 * This class implements the built-in "java::prop" command. The java::prop
 * command is used to manipulate Java Bean properties from Tcl.
 */

public class JavaPropCmd implements Command {

	/*----------------------------------------------------------------------
	 *
	 * cmdProc --
	 *
	 * 	This procedure is invoked to process the "java::prop" Tcl
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
			// Query one property.

			interp.setResult(JavaInvoke.getProperty(interp, argv[objIndex],
					argv[objIndex + 1], convert));
		} else {
			// Set one or more properties.

			for (int i = objIndex + 1; i < argv.length; i += 2) {
				JavaInvoke.setProperty(interp, argv[objIndex], argv[i],
						argv[i + 1]);
			}

			interp.resetResult();
		}
	}

	/*----------------------------------------------------------------------
	 *
	 * usage --
	 *
	 *	Returns the usage string for the java::prop command.
	 *
	 * Results:
	 *	Returns the usage string for the java::prop command.
	 *
	 * Side effects:
	 *	None.
	 *
	 *----------------------------------------------------------------------
	 */

	private static final String usage(TclObject cmd) // The command name.
	{
		return "wrong # args: should be \"" + cmd
				+ " ?-noconvert? javaObj property ?value property value ...?\"";
	}

} // end JavaPropCmd

