/*
 * StdChannel.java --
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: StdChannel.java,v 1.20 2007/10/01 21:48:40 mdejong Exp $
 *
 */

package tcl.lang.channel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import tcl.lang.Interp;
import tcl.lang.ManagedSystemInStream;
import tcl.lang.TclException;
import tcl.lang.TclIO;
import tcl.lang.TclObject;
import tcl.lang.TclRuntimeError;

/**
 * Subclass of the abstract class Channel. It implements all of the methods to
 * perform read, write, open, close, etc on system stdio channels.
 */

public class StdChannel extends Channel {

	/**
	 * stdType store which type, of the three below, this StdChannel is.
	 */

	private int stdType = -1;

	/**
	 * Flags indicating the type of this StdChannel.
	 */

	public static final int STDIN = 0;
	public static final int STDOUT = 1;
	public static final int STDERR = 2;

	/**
	 * These static variables contain references to the actual in, out, and err
	 * streams that are read from or written to when the "stdin", "stdout", or
	 * "stderr" streams are read from or written to in Jacl. The user should
	 * invoke the setIn(), setOut(), and setErr() methods in this class to
	 * reassign to a specific Java stream.
	 */

	static InputStream _in = new ManagedSystemInStream();
	static PrintStream _out = System.out;
	static PrintStream _err = System.err;

	/**
	 * Reassign the static variables that reference the in, out, and err streams
	 * used by Jacl. The user should note that these methods will change the
	 * underlying Java stream in use for all Jacl interpreters in the current
	 * process.
	 */

	public static void setIn(InputStream in) {
		_in = in;
	}

	public static void setOut(PrintStream out) {
		_out = out;
	}

	public static void setErr(PrintStream err) {
		_err = err;
	}

	/**
	 * Constructor that does nothing. Open() must be called before any of the
	 * subsequent read, write, etc calls can be made.
	 */

	StdChannel() {
	}

	/**
	 * Constructor that will automatically call open.
	 * 
	 * @param stdName
	 *            name of the stdio channel; stdin, stderr or stdout.
	 */

	StdChannel(String stdName) {
		if (stdName.equals("stdin")) {
			open(STDIN);
		} else if (stdName.equals("stdout")) {
			open(STDOUT);
		} else if (stdName.equals("stderr")) {
			open(STDERR);
		} else {
			throw new TclRuntimeError("Error: unexpected type for StdChannel");
		}
	}

	public StdChannel(int type) {
		open(type);
	}

	/**
	 * Set the channel type to one of the three stdio types. Throw a
	 * tclRuntimeEerror if the stdName is not one of the three types. If it is a
	 * stdin channel, initialize the "in" data member. Since "in" is static it
	 * may have already be initialized, test for this case first. Set the names
	 * to fileX, this will be the key in the chanTable hashtable to access this
	 * object. Note: it is not put into the hash table in this function. The
	 * calling function is responsible for that.
	 * 
	 * @param stdName
	 *            String that equals stdin, stdout, stderr
	 * @return The name of the channelId
	 */

	String open(int type) {

		switch (type) {
		case STDIN:
			mode = TclIO.RDONLY;
			setBuffering(TclIO.BUFF_LINE);
			setChanName("stdin");
			break;
		case STDOUT:
			mode = TclIO.WRONLY;
			setBuffering(TclIO.BUFF_LINE);
			setChanName("stdout");
			break;
		case STDERR:
			mode = TclIO.WRONLY;
			setBuffering(TclIO.BUFF_NONE);
			setChanName("stderr");
			break;
		default:
			throw new RuntimeException(
					"type does not match one of STDIN, STDOUT, or STDERR");
		}

		stdType = type;

		return getChanName();
	}

	/**
	 * Write to stdout or stderr. If the stdType is not set to STDOUT or STDERR
	 * this is an error; either the stdType wasnt correctly initialized, or this
	 * was called on a STDIN channel.
	 * 
	 * @param interp
	 *            the current interpreter.
	 * @param s
	 *            the string to write
	 */

	public void write(Interp interp, TclObject outData) throws IOException,
			TclException {

		checkWrite(interp);

		if (stdType == STDERR) {
			_err.print(outData.toString());
		} else {
			String s = outData.toString();
			_out.print(s);
			if (buffering == TclIO.BUFF_NONE
					|| (buffering == TclIO.BUFF_LINE && s.endsWith("\n"))) {
				_out.flush();
			}
		}
	}


	/* (non-Javadoc)
	 * @see tcl.lang.channel.Channel#implClose()
	 */
	@Override
	void implClose() throws IOException {
		if (stdType == STDOUT)
			_out.flush();
	}

	String getChanType() {
		return "tty";
	}

	protected InputStream getInputStream() throws IOException {
		return _in;
	}

	protected OutputStream getOutputStream() throws IOException {
		throw new RuntimeException("should never be called");
	}
}
