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
 * RCS: @(#) $Id: TraceCmd.java,v 1.6 1999/08/15 19:38:36 mo Exp $
 * 
 * updated by Bruce Johnson to provide some of the newer trace subcommands (add, remove, info)
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

	static final private String[] varTraceOps = { "read", "write", "unset", };
	static final private String[] validCmds = { "add", "info", "remove", "variable", "vdelete", "vinfo", };

	static final private int OPT_ADD = 0;
	static final private int OPT_INFO = 1;
	static final private int OPT_REMOVE = 2;
	static final private int OPT_VARIABLE = 3;
	static final private int OPT_VDELETE = 4;
	static final private int OPT_VINFO = 5;

	static final private String[] subCmds = { "variable", "command", "execution", };

	static final private int TYPE_VARIABLE = 0;
	static final private int TYPE_COMMAND = 1;
	static final private int TYPE_RENAME = 2;
	static final private int TYPE_DELETE = 3;
	static final private int TYPE_EXECUTION = 4;

	// Arrays for quickly generating the Tcl strings corresponding to
	// the TCL.TRACE_ARRAY, TCL.TRACE_READS, TCL.TRACE_WRITES and
	// TCL.TRACE_UNSETS flags.
	// Strings are in both old (rwua) and new styles (array read write unset)

	private static TclObject[] opStr = initOptStr();
	private static TclObject[] opArrayStr = initOptArrayStr();
	private static TclObject[] opNewStyleStr = initOptNewStyleStr();
	private static TclObject[] opNewStyleArrayStr = initOptNewStyleArrayStr();

	// initialize option strings
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

	private static TclObject[] initOptArrayStr() {
		TclObject[] strings = new TclObject[8];
		strings[0] = TclString.newInstance("a");
		strings[1] = TclString.newInstance("ra");
		strings[2] = TclString.newInstance("wa");
		strings[3] = TclString.newInstance("rwa");
		strings[4] = TclString.newInstance("ua");
		strings[5] = TclString.newInstance("rua");
		strings[6] = TclString.newInstance("wua");
		strings[7] = TclString.newInstance("rwua");

		for (int i = 0; i < 8; i++) {
			strings[i].preserve();
		}

		return strings;
	}

	private static TclObject[] initOptNewStyleStr() {
		TclObject[] strings = new TclObject[8];
		strings[0] = TclString.newInstance("error");
		strings[1] = TclString.newInstance("read");
		strings[2] = TclString.newInstance("write");
		strings[3] = TclString.newInstance("read write");
		strings[4] = TclString.newInstance("unset");
		strings[5] = TclString.newInstance("read unset");
		strings[6] = TclString.newInstance("write unset");
		strings[7] = TclString.newInstance("read write unset");

		for (int i = 0; i < 8; i++) {
			strings[i].preserve();
		}

		return strings;
	}

	private static TclObject[] initOptNewStyleArrayStr() {
		TclObject[] strings = new TclObject[8];
		strings[0] = TclString.newInstance("array");
		strings[1] = TclString.newInstance("array read");
		strings[2] = TclString.newInstance("array write");
		strings[3] = TclString.newInstance("array read write");
		strings[4] = TclString.newInstance("array unset");
		strings[5] = TclString.newInstance("array read unset");
		strings[6] = TclString.newInstance("array write unset");
		strings[7] = TclString.newInstance("array read write unset");

		for (int i = 0; i < 8; i++) {
			strings[i].preserve();
		}

		return strings;
	}

	/**
	 * This procedure is invoked as part of the Command interface to process the
	 * "trace" Tcl command. See the user documentation for details on what it
	 * does.
	 * 
	 * @param interp
	 *            current interpreter.
	 * @param objv
	 *            argument list.
	 * 
	 * @throws TclException
	 */
	public void cmdProc(Interp interp, TclObject[] objv) throws TclException {
		int len;

		if (objv.length < 2) {
			throw new TclNumArgsException(interp, 1, objv, "option ?arg arg ...?");
		}
		int opt = TclIndex.get(interp, objv[1], validCmds, "option", 0);

		switch (opt) {
		case OPT_ADD:
		case OPT_REMOVE:
		case OPT_INFO:
			if (objv.length < 3) {
				throw new TclNumArgsException(interp, 2, objv, "type opList");
			}
			int subOpt = TclIndex.get(interp, objv[2], subCmds, "option", 0);
			if (subOpt == TYPE_VARIABLE) {
				traceVariable(interp, opt, objv, 3);
			} else if (subOpt == TYPE_COMMAND) {
				throw new TclException(interp, "trace add/remove/info command not implemented yet");
				// FIXME - implement command traces
				// traceCommand(interp, opt, objv, 3);
			} else if (subOpt == TYPE_EXECUTION) {
				// FIXME - implement executions traces
				throw new TclException(interp, "trace add/remove/info execution not implemented yet");
			} else {
				throw new TclException(interp, "bad option \"" + objv[2]
						+ "\": must be execution, command, or variable");
			}
			break;
		case OPT_VARIABLE:
			if (objv.length != 5) {
				throw new TclNumArgsException(interp, 1, objv, "variable name ops command");
			}
			traceVariable(interp, OPT_ADD, objv, 2);
			break;
		case OPT_VDELETE:
			if (objv.length != 5) {
				throw new TclNumArgsException(interp, 1, objv, "vdelete name ops command");
			}
			traceVariable(interp, OPT_REMOVE, objv, 2);
			break;

		case OPT_VINFO:
			if (objv.length != 3) {
				throw new TclNumArgsException(interp, 2, objv, "name");
			}
			traceVariable(interp, OPT_VINFO, objv, 2);
			break;
		}
	}

	/**
	 * Parse and execute options for command traces.
	 * 
	 * @param interp
	 *            current interpreter
	 * @param opt
	 *            option index
	 * @param objv
	 *            argument list
	 * @param start
	 *            the index of the argument list to start parsing sub-options
	 * @throws TclException
	 */
	// FIXME - need to implement
	private void traceCommand(Interp interp, int opt, TclObject[] objv, int start) throws TclException {
		int len;

		if (objv.length < 3) {
			throw new TclNumArgsException(interp, 2, objv, "option [arg arg ...]");
		}
		switch (opt) {
		case OPT_ADD:
		case OPT_REMOVE:
			if (objv.length != 6) {
				if (opt == OPT_ADD) {
					throw new TclNumArgsException(interp, 3, objv, "name opList command");
				}
			}
			break;
		}
	}

	/**
	 * Parse and execute options for variable traces.
	 * 
	 * @param interp
	 *            current interpreter
	 * @param opt
	 *            option index (OPT_ADD, OPT_REMOVE, etc.)
	 * @param objv
	 *            argument list
	 * @param start
	 *            the index of the argument list to start parsing sub-options
	 * @throws TclException
	 */
	private void traceVariable(Interp interp, int opt, TclObject[] objv, int start) throws TclException {
		int len;

		if (objv.length < (start + 1)) {
			throw new TclNumArgsException(interp, start, objv, "name ?arg arg...?");
		}

		switch (opt) {
		case OPT_ADD:
		case OPT_REMOVE:
			if (objv.length != (start + 3)) {
				throw new TclNumArgsException(interp, start, objv, "name opList command");
			}

			int flags = 0;
			boolean isNewStyle = false;
			if (start == 3) {
				// new style trace add ...
				isNewStyle = true;
				TclObject opsArray[] = TclList.getElements(interp, objv[start + 1]);
				for (int i = 0; i < opsArray.length; i++) {
					String opsString = opsArray[i].toString();
					if ("read".equals(opsArray[i].toString())) {
						flags |= TCL.TRACE_READS;
					} else if ("write".equals(opsArray[i].toString())) {
						flags |= TCL.TRACE_WRITES;
					} else if ("unset".equals(opsArray[i].toString())) {
						flags |= TCL.TRACE_UNSETS;
					} else if ("array".equals(opsArray[i].toString())) {
						flags |= TCL.TRACE_ARRAY;
					} else {
						throw new TclException(interp, "bad operation \"" + objv[4]
								+ "\": must be array, read, unset, or write");
					}
					if (flags == 0) {
						throw new TclException(interp, "bad operations list\"" + objv[3]
								+ "\": should be one or more of array, read, unset, or write");
					}
				}
			} else {
				// old style trace variable ...
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
						case 'a':
							flags |= TCL.TRACE_ARRAY;
							break;
						default:
							flags = 0;
							break check_ops;
						}
					}
				}
				if (flags == 0) {
					throw new TclException(interp, "bad operations \"" + objv[3] + "\": should be one or more of rwua");
				}
			}

			if (opt == OPT_ADD) {
				CmdTraceProc trace = new CmdTraceProc(objv[start + 2].toString(), flags, isNewStyle);
				Var.traceVar(interp, objv[start].toString(), null, flags, trace);
			} else {
				// Search through all of our traces on this variable to
				// see if there's one with the given command. If so, then
				// delete the first one that matches.

				ArrayList traces = Var.getTraces(interp, objv[start].toString(), null, 0);
				if (traces != null) {
					len = traces.size();
					for (int i = 0; i < len; i++) {
						TraceRecord rec = (TraceRecord) traces.get(i);

						if (rec.trace instanceof CmdTraceProc) {
							CmdTraceProc proc = (CmdTraceProc) rec.trace;
							if (proc.flags == flags && proc.command.toString().equals(objv[start + 2].toString())) {
								Var.untraceVar(interp, objv[start].toString(), null, flags, proc);
								break;
							}
						}
					}
				}
			}
			break;

		case OPT_VINFO:
		case OPT_INFO:
			if (objv.length != (start + 1)) {
				throw new TclNumArgsException(interp, start, objv, "name");
			}
			ArrayList traces = Var.getTraces(interp, objv[start].toString(), null, 0);
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
							boolean hasArrayTrace = (mode & TCL.TRACE_ARRAY) == TCL.TRACE_ARRAY;
							mode &= (TCL.TRACE_READS | TCL.TRACE_WRITES | TCL.TRACE_UNSETS);
							mode /= TCL.TRACE_READS;

							// get the correct mode strings array, w/ or w/o
							// array trace, and for the command 'info' or 'vinfo'
							TclObject[] modeStrs;
							if (hasArrayTrace) {
								if (opt == OPT_INFO) {
									modeStrs = opNewStyleArrayStr;
								} else {
									modeStrs = opArrayStr;
								}
							} else {
								if (opt == OPT_INFO) {
									modeStrs = opNewStyleStr;
								} else {
									modeStrs = opStr;
								}
							}

							cmd = TclList.newInstance();
							TclList.append(interp, cmd, modeStrs[mode]);
							TclList.append(interp, cmd, TclString.newInstance(proc.command));
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

}

/**
 * The CmdTraceProc object holds the information for a specific trace.
 * 
 */
class CmdTraceProc implements VarTrace {

	// The command holds the Tcl script that will execute. The flags
	// hold the mode flags that define what conditions to fire under.

	String command;
	int flags;
	boolean newStyle;

	/**
	 * Constructor for a CmdTraceProc. It simply stores the flags and command
	 * used for this trace proc. details on what it does.
	 * 
	 * @param cmd
	 *            the command string
	 * @param newFlags
	 *            trace flags
	 * @param isNewStyle
	 *            true if new style options are used when invoking the command
	 *            string
	 */

	CmdTraceProc(String cmd, int newFlags, boolean isNewStyle) {
		command = cmd;
		flags = newFlags;
		newStyle = isNewStyle;
	}

	/**
	 * Evaluate the script associated with this trace.
	 * 
	 * @param interp
	 *            the current interpreter
	 * @param part1
	 *            the name of a scalar variable, or the array name
	 * @param part2
	 *            the index of an array variable, null if the variable is a
	 *            scaler
	 * @param flags
	 *            the trace flags that caused this invocation
	 * @throws TclException
	 */

	public void traceProc(Interp interp, String part1, String part2, int flags) throws TclException {
		if (((this.flags & flags) != 0) && ((flags & TCL.INTERP_DESTROYED) == 0)) {
			StringBuffer sbuf = new StringBuffer(command);

			try {
				Util.appendElement(interp, sbuf, part1);
				if (part2 != null) {
					Util.appendElement(interp, sbuf, part2);
				} else {
					Util.appendElement(interp, sbuf, "");
				}

				if (newStyle) {
					if ((flags & TCL.TRACE_READS) != 0) {
						Util.appendElement(interp, sbuf, "read");
					} else if ((flags & TCL.TRACE_WRITES) != 0) {
						Util.appendElement(interp, sbuf, "write");
					} else if ((flags & TCL.TRACE_UNSETS) != 0) {
						Util.appendElement(interp, sbuf, "unset");
					} else if ((flags & TCL.TRACE_ARRAY) != 0) {
						Util.appendElement(interp, sbuf, "array");
					}
				} else {
					if ((flags & TCL.TRACE_READS) != 0) {
						Util.appendElement(interp, sbuf, "r");
					} else if ((flags & TCL.TRACE_WRITES) != 0) {
						Util.appendElement(interp, sbuf, "w");
					} else if ((flags & TCL.TRACE_UNSETS) != 0) {
						Util.appendElement(interp, sbuf, "u");
					} else if ((flags & TCL.TRACE_ARRAY) != 0) {
						Util.appendElement(interp, sbuf, "a");
					}
				}
			} catch (TclException e) {
				throw new TclRuntimeError("unexpected TclException: " + e);
			}

			// Execute the command.

			interp.eval(sbuf.toString(), 0);
		}
	}

}