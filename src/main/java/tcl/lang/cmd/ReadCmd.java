/*
 * ReadCmd.java --
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: ReadCmd.java,v 1.8 2003/03/08 03:42:44 mdejong Exp $
 *
 */

package tcl.lang.cmd;

import java.io.IOException;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.TclByteArray;
import tcl.lang.TclException;
import tcl.lang.TclIO;
import tcl.lang.TclInteger;
import tcl.lang.TclObject;
import tcl.lang.TclRuntimeError;
import tcl.lang.TclString;
import tcl.lang.channel.Channel;

/**
 * This class implements the built-in "read" command in Tcl.
 */

public class ReadCmd implements Command {

	/**
	 * This procedure is invoked to process the "read" Tcl command. See the user
	 * documentation for details on what it does.
	 * 
	 * @param interp
	 *            the current interpreter.
	 * @param argv
	 *            command arguments.
	 */

	public void cmdProc(Interp interp, TclObject argv[]) throws TclException {

		Channel chan; // The channel being operated on this
		// method
		int i = 1; // Index to the next arg in argv
		int toRead = 0; // Number of bytes or chars to read from channel
		int charactersRead; // Number of bytes or chars read from channel
		boolean readAll = true; // If true read-all else toRead
		boolean noNewline = false; // If true, strip the newline if there
		TclObject result;

		if ((argv.length != 2) && (argv.length != 3)) {
			errorWrongNumArgs(interp, argv[0].toString());
		}

		if (argv[i].toString().equals("-nonewline")) {
			noNewline = true;
			i++;
		}

		if (i == argv.length) {
			errorWrongNumArgs(interp, argv[0].toString());
		}

		chan = TclIO.getChannel(interp, argv[i].toString());
		if (chan == null) {
			throw new TclException(interp, "can not find channel named \""
					+ argv[i].toString() + "\"");
		}

		// Consumed channel name.

		i++;

		// Compute how many bytes or chars to read, and see whether the final
		// noNewline should be dropped.

		if (i < argv.length) {
			String arg = argv[i].toString();

			if (Character.isDigit(arg.charAt(0))) {
				toRead = TclInteger.getInt(interp, argv[i]);
				readAll = false;
			} else if (arg.equals("nonewline")) {
				noNewline = true;
			} else {
				throw new TclException(interp, "bad argument \"" + arg
						+ "\": should be \"nonewline\"");
			}
		}

		try {
			if (chan.getEncoding() == null) {
				result = TclByteArray.newInstance();
			} else {
				result = TclString.newInstance(new StringBuffer(64));
			}
			if (readAll) {
				charactersRead = chan.read(interp, result, TclIO.READ_ALL, 0);

				// If -nonewline was specified, and we have not hit EOF
				// and the last char is a "\n", then remove it and return.

				if (noNewline) {
					String inStr = result.toString();
					if ((charactersRead > 0)
							&& (inStr.charAt(charactersRead - 1) == '\n')) {
						interp.setResult(inStr.substring(0,
								(charactersRead - 1)));
						return;
					}
				}
			} else {
				// FIXME: Bug here, the -nonewline flag must be respected
				// when reading a set number of bytes
				charactersRead = chan.read(interp, result, TclIO.READ_N_BYTES,
						toRead);
			}

			/*
			 * // FIXME: Port this -nonewline logic from the C code. if
			 * (charactersRead < 0) { Tcl_ResetResult(interp);
			 * Tcl_AppendResult(interp, "error reading \"", name, "\": ",
			 * Tcl_PosixError(interp), (char *) NULL);
			 * Tcl_DecrRefCount(resultPtr); return TCL_ERROR; }
			 * 
			 * // If requested, remove the last newline in the channel if at
			 * EOF.
			 * 
			 * if ((charactersRead > 0) && (newline != 0)) { char *result; int
			 * length;
			 * 
			 * result = Tcl_GetStringFromObj(resultPtr, &length); if
			 * (result[length - 1] == '\n') { Tcl_SetObjLength(resultPtr, length
			 * - 1); } }
			 */

			interp.setResult(result);

		} catch (IOException e) {
			throw new TclRuntimeError(
					"ReadCmd.cmdProc() Error: IOException when reading "
							+ chan.getChanName());
		}
	}

	/**
	 * A unique error msg is printed for read, therefore dont call this instead
	 * of the standard TclNumArgsException().
	 * 
	 * @param interp
	 *            the current interpreter.
	 * @param cmd
	 *            the name of the command (extracted form argv[0] of cmdProc)
	 */

	private void errorWrongNumArgs(Interp interp, String cmd)
			throws TclException {
		throw new TclException(interp, "wrong # args: should be \""
				+ "read channelId ?numChars?\" "
				+ "or \"read ?-nonewline? channelId\"");
	}

}
