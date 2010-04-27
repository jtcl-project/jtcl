/*
 * JavaCastCmd.java
 *
 *	Implements the built-in "java::cast" command.
 *
 * Copyright (c) 1998 Mo DeJong.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 * RCS: @(#) $Id: JavaCastCmd.java,v 1.4 2006/04/13 07:36:50 mdejong Exp $
 *
 */

package tcl.pkg.java;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;
import tcl.pkg.java.reflect.PkgInvoker;

/**
 * Implements the built-in "java::cast" command.
 */

public class JavaCastCmd implements Command {

	/*----------------------------------------------------------------------
	 *
	 * cmdProc --
	 *
	 * 	This procedure is invoked to process the "java::cast" Tcl
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

		if (argv.length != 3) {
			throw new TclNumArgsException(interp, 1, argv, "class javaObj");
		}

		Class cast_to = ClassRep.get(interp, argv[1]);

		// A cast to an inaccessible type is not legal in Java.
		if (!PkgInvoker.isAccessible(cast_to)) {
			JavaInvoke.notAccessibleError(interp, cast_to);
		}

		Object obj = ReflectObject.get(interp, argv[2]);

		// The null object can be cast to any type
		if (obj == null) {
			interp.setResult(ReflectObject.newInstance(interp, cast_to, obj));
			return;
		}

		Class cast_from = obj.getClass();

		if (cast_to.isAssignableFrom(cast_from)) {
			interp.setResult(ReflectObject.newInstance(interp, cast_to, obj));
			return;
		}

		// The JavaInfoCmd.getNameFromClass() method will return the name
		// of an Array class in a human readable form.

		throw new TclException(interp, "could not cast from "
				+ JavaInfoCmd.getNameFromClass(cast_from) + " to "
				+ JavaInfoCmd.getNameFromClass(cast_to));
	}

} // end JavaCastCmd

