/*
 * RegexpCmd.java --
 *
 * 	This file contains the Jacl implementation of the built-in Tcl
 *	"regexp" command. 
 *
 * Copyright (c) 1997-1999 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: RegexpCmd.java,v 1.15 2010/02/19 06:19:00 mdejong Exp $
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
import tcl.lang.TclList;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;
import tcl.lang.TclString;

/**
 * This class implements the built-in "regexp" command in Tcl.
 */

public class RegexpCmd implements Command {

	// switches for regexp command

	private static final String validOpts[] = { "-all", "-about", "-indices",
			"-inline", "-expanded", "-line", "-linestop", "-lineanchor",
			"-nocase", "-start", "--" };

	private static final int OPT_ALL = 0;
	private static final int OPT_ABOUT = 1;
	private static final int OPT_INDICES = 2;
	private static final int OPT_INLINE = 3;
	private static final int OPT_EXPANDED = 4;
	private static final int OPT_LINE = 5;
	private static final int OPT_LINESTOP = 6;
	private static final int OPT_LINEANCHOR = 7;
	private static final int OPT_NOCASE = 8;
	private static final int OPT_START = 9;
	private static final int OPT_LAST = 10;

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * init --
	 * 
	 * This procedure is invoked to connect the regexp and regsub commands to
	 * the CmdProc method of the RegexpCmd and RegsubCmd classes, respectively.
	 * Avoid the AutoloadStub class because regexp and regsub need a stub with a
	 * switch to check for the existence of the tcl.regexp package.
	 * 
	 * Results: None.
	 * 
	 * Side effects: The regexp and regsub commands are now connected to the
	 * CmdProc method of the RegexpCmd and RegsubCmd classes, respectively.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	public static void init(Interp interp) // Current interpreter.
	{
		interp.createCommand("regexp", new tcl.lang.cmd.RegexpCmd());
		interp.createCommand("regsub", new tcl.lang.cmd.RegsubCmd());
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * cmdProc --
	 * 
	 * This procedure is invoked to process the "regexp" Tcl command. See the
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
			TclObject[] objv) // Arguments to "regexp" command.
			throws TclException {
		boolean indices = false;
		boolean doinline = false;
		boolean about = false;
		boolean last = false;
		int all = 0;
		int flags;
		int offset = 0; // the index offset of the string to start matching the
		int objc = 0;
		// regular expression at
		TclObject result;
		int i;

		// Default regexp behavior is to assume that '.' will match newline
		// characters and that only \n is seen as a newline. Support for
		// newline sensitive matching must be enabled, it is off by default.

		flags = Pattern.DOTALL | Pattern.UNIX_LINES;

		for (i = 1; i < objv.length; i++) {
			if (last) {
				break;
			}

			TclObject obj = objv[i];

			if ((obj.toString().length() == 0)
					|| (obj.toString().charAt(0) != '-')) {
				// Not an option
				break;
			}

			int index = TclIndex.get(interp, obj, validOpts, "switch", 0);

			switch (index) {
			case OPT_ABOUT:
				about = true;
				break;
			case OPT_EXPANDED:
				flags |= Pattern.COMMENTS;
				break;
			case OPT_INDICES:
				indices = true;
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
			case OPT_ALL:
				all = 1;
				break;
			case OPT_INLINE:
				doinline = true;
				break;
			case OPT_START:
				if (++i >= objv.length) {
					// break the switch, the index out of bounds exception
					// will be caught later
					break;
				}

				offset = TclInteger.get(interp, objv[i]);

				if (offset < 0) {
					offset = 0;
				}

				break;
			case OPT_LAST:
				last = true;
				break;
			} // end of switch block
		} // end of for loop

		if ((objv.length - i) < (2 - (about ? 1 : 0))) {
			throw new TclNumArgsException(interp, 1, objv,
					"?switches? exp string ?matchVar?"
							+ " ?subMatchVar subMatchVar ...?");
		}

		if (doinline && ((objv.length - i - 2) != 0)) {
			// User requested -inline, but specified match variables - a
			// no-no.

			throw new TclException(interp,
					"regexp match variables not allowed when using -inline");
		}

		String exp = objv[i++].toString();

		String string;

		if (about) {
			string = "";
		} else {
			string = objv[i++].toString();
		}

		Regex reg;
		result = TclInteger.newInstance(0);

		if ((string.length() == 0) && ((flags & Pattern.MULTILINE) != 0)) {
			// Compile the expression without the Pattern.MULTILINE flag
			// so that matching to the empty string works as expected.

			flags &= ~Pattern.MULTILINE;
		}

		try {
			reg = new Regex(exp, string, offset, flags);
		} catch (PatternSyntaxException ex) {
			throw new TclException(interp, Regex.getPatternSyntaxMessage(ex));
		}

		// If about switch was enabled, return info about regexp

		if (about) {
			TclObject props = TclList.newInstance();
			props = reg.getInfo(interp);
			interp.appendElement(props.toString());
			return;
		}

		boolean matched;

		// The following loop is to handle multiple matches within the
		// same source string; each iteration handles one match. If
		// "-all" hasn't been specified then the loop body only gets
		// executed once. We terminate the loop when the starting offset
		// is past the end of the string.

		while (true) {
			matched = reg.match();

			if (!matched) {
				// We want to set the value of the intepreter result only
				// when this is the first time through the loop.

				if (all <= 1) {
					// If inlining, set the interpreter's object result
					// to an empty list, otherwise set it to an integer
					// object w/ value 0.

					if (doinline) {
						interp.resetResult();
					} else {
						interp.setResult(0);
					}
					return;
				}

				break;
			}

			int groupCount = reg.groupCount();
			int group = 0;

			if (doinline) {
				// It's the number of substitutions, plus one for the
				// matchVar at index 0

				objc = groupCount + 1;
			} else {
				objc = objv.length - i;
			}

			// loop for each variable or list element that stores a result

			for (int j = 0; j < objc; j++) {
				TclObject obj;

				if (indices) {
					int start;
					int end;

					if (group <= groupCount) {
						start = reg.start(group);
						end = reg.end(group);
						group++;

						if (end >= reg.getOffset()) {
							end--;
						}
					} else {
						start = -1;
						end = -1;
					}

					obj = TclList.newInstance();
					TclList.append(interp, obj, TclInteger.newInstance(start));
					TclList.append(interp, obj, TclInteger.newInstance(end));
				} else {
					if (group <= groupCount) {
						// group 0 is the whole match, the groups
						// 1 to groupCount indicate submatches
						// but note that the number of variables
						// could be more than the number of matches.
						// Also, optional matches groups might not
						// match a range in the input string.

						int start = reg.start(group);

						if (start == -1) {
							// Optional group did not match input
							obj = TclList.newInstance();
						} else {
							int end = reg.end(group);
							String substr = string.substring(start, end);
							obj = TclString.newInstance(substr);
						}

						group++;
					} else {
						obj = TclList.newInstance();
					}
				}

				if (doinline) {
					interp.appendElement(obj.toString());
				} else {
					String varName = objv[i + j].toString();
					try {
						interp.setVar(varName, obj, 0);
					} catch (TclException e) {
						throw new TclException(interp,
								"couldn't set variable \"" + varName + "\"");
					}
				}
			} // end of for loop

			if (all == 0) {
				break;
			}

			// Adjust the offset to the character just after the last one
			// in the matchVar and increment all to count how many times
			// we are making a match. We always increment the offset by
			// at least one to prevent endless looping (as in the case:
			// regexp -all {a*} a). Otherwise, when we match the NULL
			// string at the end of the input string, we will loop
			// indefinitely (because the length of the match is 0, so
			// the offset never changes).

			offset = reg.getOffset();

			int matchStart = reg.start();
			int matchEnd = reg.end();
			int matchLength = (matchEnd - matchStart);

			// FIXME: Does not match Tcl impl here, how does Tcl C version work?
			// offset += matchEnd;
			offset += (matchStart - offset) + matchLength;

			// A match of length zero could happen for {^} {$} or {.*} and in
			// these cases we always want to bump the index up one.

			if (matchLength == 0) {
				offset++;
			}
			all++;
			if (offset >= string.length()) {
				break;
			}
			reg.setOffset(offset);
		} // end of while loop

		// Set the interpreter's object result to an integer object with
		// value 1 if -all wasn't specified, otherwise it's all-1 (the
		// number of times through the while - 1). Get the resultPtr again
		// as the Tcl_ObjSetVar2 above may have cause the result to change.
		// [Patch #558324] (watson).

		if (!doinline) {
			interp.setResult((all != 0) ? (all - 1) : 1);
		}
	} // end cmdProc
} // end RegexpCmd

