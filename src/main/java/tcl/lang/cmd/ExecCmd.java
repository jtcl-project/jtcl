/*
 * ExecCmd.java --
 *
 *	This file contains the Jacl implementation of the built-in Tcl "exec"
 *	command. The exec command is not available on the Mac.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: ExecCmd.java,v 1.13 2006/06/30 07:57:18 mdejong Exp $
 */

package tcl.lang.cmd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclInteger;
import tcl.lang.TclList;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;
import tcl.lang.TclString;

/*
 * This class implements the built-in "exec" command in Tcl.
 */

public class ExecCmd implements Command {

	private static boolean debug = false;

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * cmdProc --
	 * 
	 * This procedure is invoked to process the "exec" Tcl command. See the user
	 * documentation for details on what it does.
	 * 
	 * Results: None.
	 * 
	 * Side effects: See the user documentation.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public void cmdProc(Interp interp, // The current interpreter.
			TclObject argv[]) // The arguments to exec.
			throws TclException // A standard Tcl exception.
	{
		int firstWord; // Index to the first non-switch arg
		int argLen = argv.length; // No of args to copy to argStrs
		int exit; // denotes exit status of process
		int errorBytes; // number of bytes of process stderr
		boolean background = false; // Indicates a bg process
		boolean keepNewline = false; // Retains newline in pipline output
		Process p; // The exec-ed process
		String argStr; // Conversion of argv to a string
		StringBuffer sbuf;

		// Check for a leading "-keepnewline" argument.

		for (firstWord = 1; firstWord < argLen; firstWord++) {
			argStr = argv[firstWord].toString();
			if ((argStr.length() > 0) && (argStr.charAt(0) == '-')) {
				if (argStr.equals("-keepnewline")) {
					keepNewline = true;
				} else if (argStr.equals("--")) {
					firstWord++;
					break;
				} else {
					throw new TclException(interp, "bad switch \"" + argStr
							+ "\": must be -keepnewline or --");
				}
			} else {
				break;
			}
		}

		if (argLen <= firstWord) {
			throw new TclNumArgsException(interp, 1, argv,
					"?switches? arg ?arg ...?");
		}

		// See if the command is to be run in background.
		// Currently this does nothing, it is just for compatibility

		if (argv[argLen - 1].toString().equals("&")) {
			argLen--;
			background = true;
		}

		try {
			p = execCmd(interp, argv, firstWord, argLen);

			// If user wanted to run in the background, then
			// don't wait for or attach to the stdout or stderr
			// streams. We don't actually know what the pid is
			// so just return a placeholder.

			if (background) {
				interp.setResult("pid0");
				return;
			}

			sbuf = new StringBuffer();

			// Create a pair of threads to read stdout and stderr
			// of the subprocess. Each stream needs to be handled
			// by a serarate thread.

			ExecInputStreamReader stdout_reader = new ExecInputStreamReader(p
					.getInputStream());

			ExecInputStreamReader stderr_reader = new ExecInputStreamReader(p
					.getErrorStream());

			// Start reading threads, wait for process to terminate in
			// this thread, then make sure other threads have terminated.

			stdout_reader.start();
			stderr_reader.start();

			if (debug) {
				System.out
						.println("started reader threads, invoking waitFor()");
			}

			exit = p.waitFor();

			if (debug) {
				System.out.println("waitFor() returned " + exit);
				System.out.println("joining reader threads");
			}

			stdout_reader.join();
			stderr_reader.join();

			// Get stdout and stderr from other threads.

			int numBytes;
			numBytes = stdout_reader.appendBytes(sbuf);
			if (debug) {
				System.out.println("appended " + numBytes
						+ " bytes from stdout stream");
			}
			numBytes = stderr_reader.appendBytes(sbuf);
			if (debug) {
				System.out.println("appended " + numBytes
						+ " bytes from stderr stream");
			}

			errorBytes = stderr_reader.getInBytes();

			// Check for the special case where there is no error
			// data but the process returns an error result

			if ((errorBytes == 0) && (exit != 0)) {
				sbuf.append("child process exited abnormally");
			}

			// If the last character of the result buffer is a newline, then
			// remove the newline character (the newline would just confuse
			// things). Finally, we set pass the result to the interpreter.

			int length = sbuf.length();
			if (!keepNewline && (length > 0)
					&& (sbuf.charAt(length - 1) == '\n')) {
				sbuf.setLength(length - 1);
			}

			// Tcl supports lots of child status conditions.
			// Unfortunately, we can only find the child's
			// exit status using the Java API

			if (exit != 0) {
				TclObject childstatus = TclList.newInstance();
				TclList.append(interp, childstatus, TclString
						.newInstance("CHILDSTATUS"));

				// We don't know how to find the child's pid
				TclList.append(interp, childstatus, TclString
						.newInstance("?PID?"));

				TclList.append(interp, childstatus, TclInteger
						.newInstance(exit));

				interp.setErrorCode(childstatus);
			}

			// when the subprocess writes to its stderr stream or returns
			// a non zero result we generate an error

			if ((exit != 0) || (errorBytes != 0)) {
				throw new TclException(interp, sbuf.toString());
			}

			// otherwise things went well so set the result

			interp.setResult(sbuf.toString());
		} catch (IOException e) {
			// if exec fails we end up catching the exception here

			throw new TclException(interp, "couldn't execute \""
					+ argv[firstWord].toString()
					+ "\": no such file or directory");

		} catch (InterruptedException e) {
			// Do Nothing...
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * readStreamIntoBuffer --
	 * 
	 * This utility function will read the contents of an InputStream into a
	 * StringBuffer. When done it returns the number of bytes read from the
	 * InputStream. The assumption is an unbuffered stream
	 * 
	 * Results: Returns the number of bytes read from the stream to the buffer
	 * 
	 * Side effects: Data is read from the InputStream.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static int readStreamIntoBuffer(InputStream in, StringBuffer sbuf) {
		int numRead = 0;
		BufferedReader br = new BufferedReader(new InputStreamReader(in));

		try {
			String line = br.readLine();

			while (line != null) {
				sbuf.append(line);
				numRead += line.length();
				sbuf.append('\n');
				numRead++;
				line = br.readLine();
			}
		} catch (IOException e) {
			// do nothing just return numRead
			if (debug) {
				System.out.println("IOException during stream read()");
				e.printStackTrace(System.out);
			}
		} finally {
			try {
				br.close();
			} catch (IOException e) {
				if (debug) {
					System.out.println("IOException during stream close()");
					e.printStackTrace(System.out);
				}
			}
		}

		return numRead;
	}

	/*
	 * public
	 * ----------------------------------------------------------------------
	 * 
	 * execCmd --
	 * 
	 * This procedure is invoked to process the "exec" call assuming the
	 * Runtime.exec( String[] cmdArr, String[] envArr, File currDir ) API exists
	 * (introduced in JDK 1.3).
	 * 
	 * Results: Returns the new process.
	 * 
	 * Side effects: See the user documentation.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private Process execCmd(Interp interp, TclObject argv[], int first, int last)
			throws IOException {
		String[] strv = new String[last - first];

		for (int i = first, j = 0; i < last; j++, i++) {
			strv[j] = argv[i].toString();
		}

		return Runtime.getRuntime().exec(strv, null, interp.getWorkingDir());
	}

} // end ExecCmd

/*
 * ----------------------------------------------------------------------
 * 
 * ExecInputStreamReader --
 * 
 * Read data from an input stream into a StringBuffer. This code is executed in
 * its own thread since some JDK implementation would deadlock when reading from
 * a stream after waitFor is invoked.
 * ----------------------------------------------------------------------
 */

class ExecInputStreamReader extends Thread {
	InputStream in;
	StringBuffer sb;
	int inBytes;

	ExecInputStreamReader(InputStream in) {
		this.in = in;
		this.sb = new StringBuffer();
		inBytes = 0;
	}

	public void run() {
		inBytes = ExecCmd.readStreamIntoBuffer(in, sb);
	}

	int appendBytes(StringBuffer dest) {
		int bytes = sb.length();
		dest.append(sb.toString());
		return bytes;
	}

	int getInBytes() {
		return inBytes;
	}
}
