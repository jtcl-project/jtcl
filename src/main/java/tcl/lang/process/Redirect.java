package tcl.lang.process;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import tcl.lang.channel.Channel;

/**
 * Represents a redirection on one of the streams attached to a TclProcess.
 * Inspired by the Java 1.7 API.
 * 
 */
public class Redirect {
	/**
	 * The kind of redirection (see enum Type)
	 */
	protected Type type;
	/**
	 * The Channel object, for TCL_CHANNEL redirects
	 */
	protected Channel channel = null;
	/**
	 * The File object, for FILE redirects
	 */
	protected File file = null;
	/**
	 * The TclProcess on the other side of the pipe, for PIPE redirects
	 */
	protected TclProcess pipePartner = null;
	/**
	 * If true, close channel when complete
	 */
	protected boolean closeChannel = false;
	/**
	 * If true, append to file
	 */
	protected boolean appendToFile = false;

	/**
	 * An InputStream, for STREAM redirects
	 */
	protected InputStream istream = null;
	/**
	 * An OutputStream, for STREAM redirects
	 */
	protected OutputStream ostream = null;

	/**
	 * The types of redirection that can be defined
	 * 
	 */
	public enum Type {
		INHERIT, PIPE, FILE, TCL_CHANNEL, MERGE_ERROR, STREAM
	}

	private Redirect(Type type) {
		this.type = type;
	}

	/**
	 * Create a PIPE redirect
	 * 
	 * @param partner
	 *            TclProcess on the other side of the pipe. This Redirect object
	 *            is attached to the TclProcess on this side of the pipe.
	 */
	public Redirect(TclProcess partner) {
		this.type = Type.PIPE;
		this.pipePartner = partner;
	}

	/**
	 * Create a File redirect
	 * 
	 * @param f
	 *            File to redirect bytes from/to
	 */
	public Redirect(File f, boolean append) {
		this.type = Type.FILE;
		this.file = f;
		this.appendToFile = append;
	}

	/**
	 * Create a Tcl Channel redirect
	 * 
	 * @param channel
	 *            to redirect bytes from/to
	 */
	public Redirect(Channel channel, boolean close) {
		this.type = Type.TCL_CHANNEL;
		this.channel = channel;
		this.closeChannel = close;
	}

	/**
	 * Create an InputStream STREAM redirect
	 * 
	 * @param s
	 *            InputStream to get bytes from
	 */
	public Redirect(InputStream s) {
		this.istream = s;
		this.type = Type.STREAM;
	}

	/**
	 * Create an OutputStream STREAM redirect
	 * 
	 * @param s
	 *            OutputStream to send bytes to
	 */
	public Redirect(OutputStream s) {
		this.ostream = s;
		this.type = Type.STREAM;
	}

	/**
	 * 
	 * @return a new INHERIT redirect
	 */
	public static Redirect inherit() {
		return new Redirect(Type.INHERIT);
	}

	/**
	 * @return a new redirect to merge stderr with stdout
	 */
	public static Redirect stderrToStdout() {
		return new Redirect(Type.MERGE_ERROR);
	}

	/**
	 * @return This Redirect's type
	 */
	public Type getType() {
		return this.type;
	}
}