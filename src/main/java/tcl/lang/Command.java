/*
 * Command.java
 *
 *	Interface for Commands that can be added to the Tcl Interpreter.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: Command.java,v 1.3 1999/08/05 03:43:27 mo Exp $
 */

package tcl.lang;


/**
 * The Command interface specifies the method that a new Tcl command must
 * implement. See the createCommand method of the Interp class to see how to add
 * a new command to an interperter.
 */

public interface Command {
	abstract public void cmdProc( // The method cmdProc is called by interp.
			Interp interp, // The interpreter for setting result etc.
			TclObject[] objv) // The argument list for the command.
			throws TclException; // Tcl exceptions are thown for Tcl errors.
}
