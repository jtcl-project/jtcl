/*
 * Copyright (c) 2005 Advanced Micro Devices, Inc.
 *
 * See the file "license.amd" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 * RCS: @(#) $Id: TJCShell.java,v 1.2 2006/01/24 07:55:45 mdejong Exp $
 *
 */

// This class implements the start up shell for the TJC compiler.

package tcl.pkg.tjc;

import tcl.lang.ConsoleThread;
import tcl.lang.Interp;
import tcl.lang.Notifier;
import tcl.lang.TCL;
import tcl.lang.TclException;
import tcl.lang.TclInteger;
import tcl.lang.TclList;
import tcl.lang.TclObject;
import tcl.lang.TclRuntimeError;
import tcl.lang.TclString;

public class TJCShell {

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * main --
	 * 
	 * Main program for tclsh and most other Tcl-based applications.
	 * 
	 * Results: None.
	 * 
	 * Side effects: This procedure initializes the Tcl world and then starts
	 * interpreting commands; almost anything could happen, depending on the
	 * script being interpreted.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public static void main(String[] args) // Array of command-line argument
	// strings.
	{
		String fileName = "resource:/tcl/pkg/tjc/library/tjc.tcl";
		int startIndex = 0;

		// Create the interpreter. This will also create the built-in
		// Tcl commands.

		Interp interp = new Interp();

		// Check for -shell or -s, this means the user wanted to start
		// a TJC enabled shell instead of running the tjc program.

		if ((args.length > 0)
				&& (args[0].equals("-s") || args[0].equals("-shell"))) {
			startIndex = 1;
		}

		TclObject argv = TclList.newInstance();
		argv.preserve();
		try {
			if (startIndex == 1) {
				// Invoke tjc as Tcl shell with TJC commands.
				interp.setVar("argv0", "tjc", TCL.GLOBAL_ONLY);
				interp.setVar("tcl_interactive", "1", TCL.GLOBAL_ONLY);
			} else {
				// Invoke tjc as standalone executable
				interp.setVar("argv0", "tjc.tcl", TCL.GLOBAL_ONLY);
				interp.setVar("tcl_interactive", "0", TCL.GLOBAL_ONLY);
			}
			for (int i = startIndex; i < args.length; i++) {
				TclList.append(interp, argv, TclString.newInstance(args[i]));
			}
			interp.setVar("argv", argv, TCL.GLOBAL_ONLY);
			interp.setVar("argc", TclInteger.newInstance(TclList.getLength(
					interp, argv)), TCL.GLOBAL_ONLY);
		} catch (TclException e) {
			throw new TclRuntimeError("unexpected TclException: " + e);
		} finally {
			argv.release();
		}

		// If a script file was specified then just source that file
		// and quit. Load the TJC runtime support package in case
		// the tjc.tcl to be sourced was itself compiled with TJC.
		// We could load via TJC::package but this works just fine.

		if (fileName != null) {
			int exitCode = 0;
			try {
				interp.eval("package require TJC");
				interp.eval("source " + fileName);
			} catch (TclException e) {
				int code = e.getCompletionCode();
				if (code == TCL.RETURN) {
					code = interp.updateReturnInfo();
					if (code != TCL.OK) {
						System.err
								.println("command returned bad code: " + code);
						exitCode = 2;
					}
				} else if (code == TCL.ERROR) {
					System.err.println(interp.getResult().toString());
					exitCode = 1;
				} else {
					System.err.println("command returned bad code: " + code);
					exitCode = 2;
				}
			}

			if (startIndex == 0) {
				// Note that if the above interp.evalFile() returns the main
				// thread will exit. This may bring down the VM and stop
				// the execution of Tcl.
				//
				// If the script needs to handle events, it must call
				// vwait or do something similar.
				//
				// Note that the script can create AWT widgets. This will
				// start an AWT event handling thread and keep the VM up.
				// However,
				// the interpreter thread (the same as the main thread) would
				// have exited and no Tcl scripts can be executed.

				interp.dispose();
				System.exit(exitCode);
			}
		}

		if (startIndex == 1) {
			// We are running in interactive mode. Start the ConsoleThread
			// that loops, grabbing stdin and passing it to the interp.

			ConsoleThread consoleThread = new ConsoleThread(interp);
			consoleThread.setDaemon(true);
			consoleThread.start();

			// Loop forever to handle user input events in the command line.

			Notifier notifier = interp.getNotifier();
			while (true) {
				// process events until "exit" is called.

				notifier.doOneEvent(TCL.ALL_EVENTS);
			}
		}
	}
} // end class TJCShell

