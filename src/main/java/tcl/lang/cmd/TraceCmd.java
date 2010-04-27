/*
 * TraceCmd.java --
 *
 *	This file implements the Tcl "trace" command.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: TraceCmd.java,v 1.8 2006/01/26 19:49:18 mdejong Exp $
 *
 */

package tcl.lang.cmd;

import java.util.ArrayList;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.TCL;
import tcl.lang.TclException;
import tcl.lang.TclIndex;
import tcl.lang.TclList;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;
import tcl.lang.TclRuntimeError;
import tcl.lang.TclString;
import tcl.lang.TraceRecord;
import tcl.lang.Util;
import tcl.lang.Var;
import tcl.lang.VarTrace;

/**
 * The TraceCmd class implements the Command interface for specifying a new Tcl
 * command. The method cmdProc implements the built-in Tcl command "trace" which
 * is used to manupilate variable traces. See user documentation for more
 * details.
 */

public class TraceCmd implements Command {

	// Valid sub-commands for the trace command.

	static final private String[] validCmds = { "variable", "vdelete", "vinfo", };

	static final private int OPT_VARIABLE = 0;
	static final private int OPT_VDELETE = 1;
	static final private int OPT_VINFO = 2;

	// An array for quickly generating the Tcl strings corresponding to
	// the TCL.TRACE_READS, TCL.TRACE_WRITES and TCL.TRACE_UNSETS flags.

	private static TclObject[] opStr = initOptStr();

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * initOptStr --
	 * 
	 * This static method is called when the TraceCmd class is loaded into the
	 * VM. It initializes the opStr array.
	 * 
	 * Results: Initial value for opStr.
	 * 
	 * Side effects: The TclObjects stored in opStr are preserve()'ed.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private static TclObject[] initOptStr() {
		TclObject[] strings = new TclObject[8];
		strings[0] = TclString.newInstance("error");
		strings[1] = TclString.newInstance("r");
		strings[2] = TclString.newInstance("w");
		strings[3] = TclString.newInstance("rw");
		strings[4] = TclString.newInstance("u");
		strings[5] = TclString.newInstance("ru");
		strings[6] = TclString.newInstance("wu");
		strings[7] = TclString.newInstance("rwu");

		for (int i = 0; i < 8; i++) {
			strings[i].preserve();
		}

		return strings;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * Tcl_TraceObjCmd -> TraceCmd.cmdProc
	 * 
	 * This procedure is invoked as part of the Command interface to process the
	 * "trace" Tcl command. See the user documentation for details on what it
	 * does.
	 * 
	 * Results: None.
	 * 
	 * Side effects: See the user documentation.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public void cmdProc(Interp interp, // Current interpreter.
			TclObject[] objv) // Argument list.
			throws TclException // A standard Tcl exception.
	{
		int len;

		if (objv.length < 2) {
			throw new TclNumArgsException(interp, 1, objv,
					"option [arg arg ...]");
		}
		int opt = TclIndex.get(interp, objv[1], validCmds, "option", 0);

		switch (opt) {
		case OPT_VARIABLE:
		case OPT_VDELETE:
			if (objv.length != 5) {
				if (opt == OPT_VARIABLE) {
					throw new TclNumArgsException(interp, 1, objv,
							"variable name ops command");
				} else {
					throw new TclNumArgsException(interp, 1, objv,
							"vdelete name ops command");
				}
			}

			int flags = 0;
			String ops = objv[3].toString();
			len = ops.length();
			check_ops: {
				for (int i = 0; i < len; i++) {
					switch (ops.charAt(i)) {
					case 'r':
						flags |= TCL.TRACE_READS;
						break;
					case 'w':
						flags |= TCL.TRACE_WRITES;
						break;
					case 'u':
						flags |= TCL.TRACE_UNSETS;
						break;
					default:
						flags = 0;
						break check_ops;
					}
				}
			}

			if (flags == 0) {
				throw new TclException(interp, "bad operations \"" + objv[3]
						+ "\": should be one or more of rwu");
			}

			if (opt == OPT_VARIABLE) {
				CmdTraceProc trace = new CmdTraceProc(objv[4].toString(), flags);
				Var.traceVar(interp, objv[2].toString(), null, flags, trace);
			} else {
				// Search through all of our traces on this variable to
				// see if there's one with the given command. If so, then
				// delete the first one that matches.

				ArrayList traces = Var.getTraces(interp, objv[2].toString(),
						null, 0);
				if (traces != null) {
					len = traces.size();
					for (int i = 0; i < len; i++) {
						TraceRecord rec = (TraceRecord) traces.get(i);

						if (rec.trace instanceof CmdTraceProc) {
							CmdTraceProc proc = (CmdTraceProc) rec.trace;
							if (proc.flags == flags
									&& proc.command.toString().equals(
											objv[4].toString())) {
								Var.untraceVar(interp, objv[2].toString(),
										null, flags, proc);
								break;
							}
						}
					}
				}
			}
			break;

		case OPT_VINFO:
			if (objv.length != 3) {
				throw new TclNumArgsException(interp, 2, objv, "name");
			}
			ArrayList traces = Var.getTraces(interp, objv[2].toString(), null,
					0);
			if (traces != null) {
				len = traces.size();
				TclObject list = TclList.newInstance();
				TclObject cmd = null;
				list.preserve();

				try {
					for (int i = 0; i < len; i++) {
						TraceRecord rec = (TraceRecord) traces.get(i);

						if (rec.trace instanceof CmdTraceProc) {
							CmdTraceProc proc = (CmdTraceProc) rec.trace;
							int mode = proc.flags;
							mode &= (TCL.TRACE_READS | TCL.TRACE_WRITES | TCL.TRACE_UNSETS);
							mode /= TCL.TRACE_READS;

							cmd = TclList.newInstance();
							TclList.append(interp, cmd, opStr[mode]);
							TclList.append(interp, cmd, TclString
									.newInstance(proc.command));
							TclList.append(interp, list, cmd);
						}
					}
					interp.setResult(list);
				} finally {
					list.release();
				}
			}
			break;
		}
	}

} // TraceCmd

// The CmdTraceProc object holds the information for a specific
// trace.
class CmdTraceProc implements VarTrace {

	// The command holds the Tcl script that will execute. The flags
	// hold the mode flags that define what conditions to fire under.

	String command;
	int flags;

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * CmdTraceProc --
	 * 
	 * This function is a constructor for a CmdTraceProc. It simply stores the
	 * flags and command used for this trace proc. details on what it does.
	 * 
	 * Results: None.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	CmdTraceProc(String cmd, int newFlags) {
		flags = newFlags;
		command = cmd;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * traceProc --
	 * 
	 * This function gets called when a variable is used in a way that would
	 * cause this particular trace to fire. It will evaluate the script
	 * associated with this trace.
	 * 
	 * Results: None.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public void traceProc(Interp interp, // The current interpreter.
			String part1, // A Tcl variable or array name.
			String part2, // Array element name or NULL.
			int flags) // Mode flags: TCL.TRACE_READS, TCL.TRACE_WRITES or
			// TCL.TRACE_UNSETS.
			throws TclException // A standard Tcl exception.
	{
		if (((this.flags & flags) != 0)
				&& ((flags & TCL.INTERP_DESTROYED) == 0)) {
			StringBuffer sbuf = new StringBuffer(command);

			try {
				Util.appendElement(interp, sbuf, part1);
				if (part2 != null) {
					Util.appendElement(interp, sbuf, part2);
				} else {
					Util.appendElement(interp, sbuf, "");
				}

				if ((flags & TCL.TRACE_READS) != 0) {
					Util.appendElement(interp, sbuf, "r");
				} else if ((flags & TCL.TRACE_WRITES) != 0) {
					Util.appendElement(interp, sbuf, "w");
				} else if ((flags & TCL.TRACE_UNSETS) != 0) {
					Util.appendElement(interp, sbuf, "u");
				}
			} catch (TclException e) {
				throw new TclRuntimeError("unexpected TclException: " + e);
			}

			// Execute the command.

			interp.eval(sbuf.toString(), 0);
		}
	}

} // CmdTraceProc
