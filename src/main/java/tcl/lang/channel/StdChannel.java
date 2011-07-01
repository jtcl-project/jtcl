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
import tcl.lang.TclIO;
import tcl.lang.TclRuntimeError;

/**
 * Subclass of the abstract class Channel. It implements all of the methods to
 * perform read, write, open, close, etc on system stdio channels.
 */

public class StdChannel extends Channel {

	/**
	 * Defines the type of Channel: STDIN, STDOUT or STDERR
	 */
	private int stdType = -1;

	/**
	 * Used to indicate that this channel is a standard input channel
	 */
	public static final int STDIN = 0;
	/**
	 * Used to indicate that this channel is a standard output channel
	 */
	public static final int STDOUT = 1;
	/**
	 * Used to indicate that this channel is a standard error channel
	 */
	public static final int STDERR = 2;

	/**
	 * Standard Input Stream to read from
	 */
	private InputStream in;
	/**
	 * Standard output stream to read from
	 */
	private OutputStream out;
	/**
	 * Standard error stream to read from
	 */
	private OutputStream err;
	


	/**
	 * Constructor that will automatically call open.
	 * 
	 * @param stdName
	 *            name of the stdio channel; "stdin", "stderr" or "stdout"
	 */

	StdChannel(Interp interp, String stdName) {
		in = interp.getSystemIn();
		out = interp.getSystemOut();
		err = interp.getSystemErr();
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

	public StdChannel(Interp interp, int type) {
		in = interp.getSystemIn();
		out = interp.getSystemOut();
		err = interp.getSystemErr();
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
			throw new RuntimeException("type does not match one of STDIN, STDOUT, or STDERR");
		}

		stdType = type;

		return getChanName();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tcl.lang.channel.Channel#implClose()
	 */
	@Override
	void implClose() throws IOException {
		/*
		 * don't actually close the various standard streams, because we can't
		 * get them back
		 */
	}

	@Override
	String getChanType() {
		return "tty";
	}

	@Override
	protected InputStream getInputStream() throws IOException {
		if (stdType == STDIN)
			return in;
		else
			throw new RuntimeException("Should never be called");
	}

	@Override
	protected OutputStream getOutputStream() throws IOException {
		switch (stdType) {
		case STDOUT:
			return out;
		case STDERR:
			return err;
		}
		throw new RuntimeException("should never be called");
	}
}
