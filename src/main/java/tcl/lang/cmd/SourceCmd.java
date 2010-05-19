/*
 * SourceCmd.java
 *
 *	Implements the "source" command.
 *
 * Copyright (c) 1997 Cornell University.
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: SourceCmd.java,v 1.2 2005/11/07 07:41:51 mdejong Exp $
 *
 */

package tcl.lang.cmd;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.TCL;
import tcl.lang.TclException;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;

/**
 * This class implements the built-in "source" command in Tcl.
 */

public class SourceCmd implements Command {

	/**
	 * cmdProc --
	 * 
	 * This cmdProc is invoked to process the "source" Tcl command. See the user
	 * documentation for details on what it does.
	 * 
	 * Results: None.
	 * 
	 * Side effects: A standard Tcl result is stored in the interpreter. See the
	 * user documentation.
	 * 
	 * @see tcl.lang.Command#cmdProc(tcl.lang.Interp, tcl.lang.TclObject[])
	 */
	public void cmdProc(Interp interp, TclObject argv[]) throws TclException {
		String fileName = null;
		boolean url = false;

		if (argv.length == 2) {
			fileName = argv[1].toString();
		} else if (argv.length == 3) {
			if (argv[1].toString().equals("-url")) {
				url = true;
				fileName = argv[2].toString();
			}
		}

		if (fileName == null) {
			throw new TclNumArgsException(interp, 1, argv, "?-url? fileName");
		}

		try {
			if (fileName.startsWith("resource:/")) {
				interp.evalResource(fileName.substring(9));
			} else if (url) {
				interp.evalURL(null, fileName);
			} else {
				interp.evalFile(fileName);
			}
		} catch (TclException e) {
			int code = e.getCompletionCode();

			if (code == TCL.RETURN) {
				int realCode = interp.updateReturnInfo();
				if (realCode != TCL.OK) {
					e.setCompletionCode(realCode);
					throw e;
				}
			} else if (code == TCL.ERROR) {
				// Record information telling where the error occurred.

				interp.addErrorInfo("\n    (file line " + interp.errorLine + ")");
				throw e;
			} else {
				throw e;
			}
		}
	}

}
