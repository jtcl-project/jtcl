/*
 * RegsubCmd.java
 *
 * 	This contains the Jacl implementation of the built-in Tcl
 *	"regsub" command.
 *
 * Copyright (c) 1997-1999 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: RegsubCmd.java,v 1.12 2009/10/04 20:08:56 mdejong Exp $
 */

package tcl.lang.cmd;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.Regex;
import tcl.lang.TclException;
import tcl.lang.TclIndex;
import tcl.lang.TclInteger;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;

/**
 * This class implements the built-in "regsub" command in Tcl.
 */

public class RegsubCmd implements Command {

	private static final String validOpts[] = { "-all", "-nocase", "-expanded",
			"-line", "-linestop", "-lineanchor", "-start", "--" };

	private static final int OPT_ALL = 0;
	private static final int OPT_NOCASE = 1;
	private static final int OPT_EXPANDED = 2;
	private static final int OPT_LINE = 3;
	private static final int OPT_LINESTOP = 4;
	private static final int OPT_LINEANCHOR = 5;
	private static final int OPT_START = 6;
	private static final int OPT_LAST = 7;

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * cmdProc --
	 * 
	 * This procedure is invoked to process the "regsub" Tcl command. See the
	 * user documentation for details on what it does.
	 * 
	 * Results: A standard Tcl result.
	 * 
	 * Side effects: See the user documentation.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	public void cmdProc(Interp interp, // Current interpreter.
			TclObject[] objv) // Arguments to "regsub" command.
			throws TclException {
		int idx;
		boolean all = false;
		boolean last = false;
		int flags;
		int offset = 0;
		String result;

		// Default regexp behavior is to assume that '.' will match newline
		// characters and that only \n is seen as a newline. Support for
		// newline sensitive matching must be enabled, it is off by default.

		flags = Pattern.DOTALL | Pattern.UNIX_LINES;

		for (idx = 1; idx < objv.length; idx++) {
			if (last) {
				break;
			}

			TclObject obj = objv[idx];

			if ((obj.toString().length() == 0)
					|| (obj.toString().charAt(0) != '-')) {
				// Not an option
				break;
			}

			int index = TclIndex.get(interp, obj, validOpts, "switch", 0);

			switch (index) {
			case OPT_ALL:
				all = true;
				break;
			case OPT_EXPANDED:
				flags |= Pattern.COMMENTS;
				break;
			case OPT_LINESTOP:
				flags &= ~Pattern.DOTALL; // Don't match . to newline character
				break;
			case OPT_LINEANCHOR:
				flags |= Pattern.MULTILINE; // Use line sensitive matching
				break;
			case OPT_LINE:
				flags |= Pattern.MULTILINE; // Use line sensitive matching
				flags &= ~Pattern.DOTALL; // Don't match . to newline character
				break;
			case OPT_NOCASE:
				flags |= Pattern.CASE_INSENSITIVE;
				break;
			case OPT_START:
				if (++idx == objv.length) {
					// break the switch, the index out of bounds exception
					// will be caught later

					break;
				}

				offset = TclInteger.get(interp, objv[idx]);

				if (offset < 0) {
					offset = 0;
				}
				break;
			case OPT_LAST:
				last = true;
				break;
			}
		} // end options for loop

		if (objv.length - idx < 3 || objv.length - idx > 4) {
			throw new TclNumArgsException(interp, 1, objv,
					"?switches? exp string subSpec ?varName?");
		}

		// get cmd's params

		String exp = objv[idx++].toString();
		String string = objv[idx++].toString();
		String subSpec = objv[idx++].toString();
		String varName = null;

		if ((objv.length - idx) > 0) {
			varName = objv[idx++].toString();
		}

		Regex reg;
		try {
			// we use the substring of string at the specified offset
			reg = new Regex(exp, string, offset, flags);
		} catch (PatternSyntaxException ex) {
			throw new TclException(interp, Regex.getPatternSyntaxMessage(ex));
		}

		// Parse a subSpec param from Tcl's to Java's form.

		subSpec = Regex.parseSubSpec(subSpec);

		// do the replacement process

		if (!all) {
			result = reg.replaceFirst(subSpec);
		} else {
			result = reg.replaceAll(subSpec);
		}

		try {
			if (varName != null) {
				interp.setResult(reg.getCount());
				interp.setVar(varName, result, 0);
			} else {
				interp.setResult(result);
			}
		} catch (TclException e) {
			throw new TclException(interp, "couldn't set variable \"" + varName
					+ "\"");
		}
	}

} // end class RegsubCmd

