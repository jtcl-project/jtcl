/*
 * TclIO.java --
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: TclIO.java,v 1.11 2009/07/16 22:12:18 rszulgo Exp $
 *
 */

package tcl.lang;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import tcl.lang.channel.Channel;
import tcl.lang.channel.StdChannel;

public class TclIO {

	public static final int READ_ALL = 1;
	public static final int READ_LINE = 2;
	public static final int READ_N_BYTES = 3;

	public static final int SEEK_SET = 1;
	public static final int SEEK_CUR = 2;
	public static final int SEEK_END = 3;

	public static final int RDONLY = 1;
	public static final int WRONLY = 2;
	public static final int RDWR = 4;
	public static final int APPEND = 8;
	public static final int CREAT = 16;
	public static final int EXCL = 32;
	public static final int TRUNC = 64;

	public static final int BUFF_FULL = 0;
	public static final int BUFF_LINE = 1;
	public static final int BUFF_NONE = 2;

	public static final int TRANS_AUTO = 0;
	public static final int TRANS_BINARY = 1;
	public static final int TRANS_LF = 2;
	public static final int TRANS_CR = 3;
	public static final int TRANS_CRLF = 4;

	public static int TRANS_PLATFORM;

	static {
		if (Util.isWindows())
			TRANS_PLATFORM = TRANS_CRLF;
		else if (Util.isMac())
			TRANS_PLATFORM = TRANS_CR;
		else
			TRANS_PLATFORM = TRANS_LF;
	}

	/**
	 * Table of channels currently registered for all interps. The
	 * interpChanTable has "virtual" references into this table that stores the
	 * registered channels for the individual interp.
	 */

	private static StdChannel stdinChan = null;
	private static StdChannel stdoutChan = null;
	private static StdChannel stderrChan = null;

	public static Channel getChannel(Interp interp, String chanName) {
		return ((Channel) getInterpChanTable(interp).get(chanName));
	}

	public static void getChannelNames(Interp interp, TclObject pattern)
			throws TclException {
		HashMap ht = getInterpChanTable(interp);
		Iterator it = ht.keySet().iterator();

		while (it.hasNext()) {
			String chanName = (String) it.next();
			Channel chan = (Channel) ht.get(chanName);

			if (chan == stdinChan) {
				chanName = "stdin";
			} else if (chan == stdoutChan) {
				chanName = "stdout";
			} else if (chan == stderrChan) {
				chanName = "stderr";
			}

			try {
				if (pattern == null) {
					interp.appendElement(chanName);
				} else if (Util.stringMatch(chanName, pattern.toString())) {
					interp.appendElement(chanName);
				}
			} catch (TclException e) {
				throw e;
			}
		}
	}

	public static void registerChannel(Interp interp, Channel chan) {

		if (interp != null) {
			HashMap chanTable = getInterpChanTable(interp);
			chanTable.put(chan.getChanName(), chan);
			chan.refCount++;
		}
	}

	public static void unregisterChannel(Interp interp, Channel chan) {
		HashMap chanTable = getInterpChanTable(interp);
		chanTable.remove(chan.getChanName());

		if (--chan.refCount <= 0) {
			try {
				chan.close();
			} catch (IOException e) {
				// e.printStackTrace(System.err);
				throw new TclRuntimeError(
						"TclIO.unregisterChannel() Error: IOException when closing "
								+ chan.getChanName() + ": " + e.getMessage());
			}
		}
	}

	static HashMap getInterpChanTable(Interp interp) {
		Channel chan;

		if (interp.interpChanTable == null) {

			interp.interpChanTable = new HashMap();

			chan = getStdChannel(StdChannel.STDIN);
			registerChannel(interp, chan);

			chan = getStdChannel(StdChannel.STDOUT);
			registerChannel(interp, chan);

			chan = getStdChannel(StdChannel.STDERR);
			registerChannel(interp, chan);
		}

		return interp.interpChanTable;
	}

	public static Channel getStdChannel(int type) {
		Channel chan = null;

		switch (type) {
		case StdChannel.STDIN:
			if (stdinChan == null) {
				stdinChan = new StdChannel(StdChannel.STDIN);
			}
			chan = stdinChan;
			break;
		case StdChannel.STDOUT:
			if (stdoutChan == null) {
				stdoutChan = new StdChannel(StdChannel.STDOUT);
			}
			chan = stdoutChan;
			break;
		case StdChannel.STDERR:
			if (stderrChan == null) {
				stderrChan = new StdChannel(StdChannel.STDERR);
			}
			chan = stderrChan;
			break;
		default:
			throw new TclRuntimeError("Invalid type for StdChannel");
		}

		return (chan);
	}

	/**
	 * Really ugly function that attempts to get the next available channelId
	 * name. In C the FD returned in the native open call returns this value,
	 * but we don't have that so we need to do this funky iteration over the
	 * HashMap.
	 * 
	 * @param interp
	 *            currrent interpreter.
	 * @return the next integer to use in the channelId name.
	 */

	public static String getNextDescriptor(Interp interp, String prefix) {
		int i;
		HashMap htbl = getInterpChanTable(interp);

		// The first available file identifier in Tcl is "file3"
		if (prefix.equals("file"))
			i = 3;
		else
			i = 0;

		for (; (htbl.get(prefix + i)) != null; i++) {
			// Do nothing...
		}
		return prefix + i;
	}

	/*
	 * Return a string description for a translation id defined above.
	 */

	public static String getTranslationString(int translation) {
		switch (translation) {
		case TRANS_AUTO:
			return "auto";
		case TRANS_CR:
			return "cr";
		case TRANS_CRLF:
			return "crlf";
		case TRANS_LF:
			return "lf";
		case TRANS_BINARY:
			return "lf";
		default:
			throw new TclRuntimeError("bad translation id");
		}
	}

	/*
	 * Return a numerical identifier for the given -translation string.
	 */

	public static int getTranslationID(String translation) {
		if (translation.equals("auto"))
			return TRANS_AUTO;
		else if (translation.equals("cr"))
			return TRANS_CR;
		else if (translation.equals("crlf"))
			return TRANS_CRLF;
		else if (translation.equals("lf"))
			return TRANS_LF;
		else if (translation.equals("binary"))
			return TRANS_LF;
		else if (translation.equals("platform"))
			return TRANS_PLATFORM;
		else
			return -1;
	}

	/*
	 * Return a string description for a -buffering id defined above.
	 */

	public static String getBufferingString(int buffering) {
		switch (buffering) {
		case BUFF_FULL:
			return "full";
		case BUFF_LINE:
			return "line";
		case BUFF_NONE:
			return "none";
		default:
			throw new TclRuntimeError("bad buffering id");
		}
	}

	/*
	 * Return a numerical identifier for the given -buffering string.
	 */

	public static int getBufferingID(String buffering) {
		if (buffering.equals("full"))
			return BUFF_FULL;
		else if (buffering.equals("line"))
			return BUFF_LINE;
		else if (buffering.equals("none"))
			return BUFF_NONE;
		else
			return -1;
	}

}
