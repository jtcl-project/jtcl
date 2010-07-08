package tcl.lang;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import tcl.lang.channel.Channel;

/**
 * This class copies data from an InputStream or Channel to a OutputStream or
 * Channel, inside of a thread. As with any Thread, PipelineCoupler.start()
 * starts execution. Exceptions can be retrieved with
 * PipelineCoupler.ExceptionReceiver
 */
public class PipelineCoupler extends Thread {
	/**
	 * stream to read from, if not null
	 */
	private InputStream istream = null;

	/**
	 * Stream to write to, if not null
	 */
	private OutputStream ostream = null;

	/**
	 * Channel to read from, if not null
	 */
	private Channel ichannel = null;

	/**
	 * Channel to write to, if not null
	 */
	private Channel ochannel = null;

	/**
	 * Buffer size of data for read from channels.
	 */
	private static final int bufSize = 256;

	/**
	 * Any exceptions in the thread are stored in this receiver
	 */
	private ExceptionReceiver exceptionReceiver = null;

	/**
	 * Access to stream and channel writes are synchronized to this Object.
	 */
	private Object writeMutex = new Object();

	/**
	 * The TCL Interp is used to throw TclExceptions
	 */
	private Interp interp;

	/**
	 * This thread should exit when stop is true
	 */
	private volatile boolean stop = false;

	/**
	 * Tracking for thread leaks
	 */
	static volatile int runningCouplerCount = 0;

	/**
	 * @param in
	 *            InputStream to copy data from
	 * @param out
	 *            OutputStream to copy data to
	 */
	public PipelineCoupler(InputStream in, OutputStream out) {
		super();
		this.istream = in;
		this.ostream = out;
	}

	/**
	 * @param interp
	 *            Interp instance that contains the Channel in
	 * @param in
	 *            Channel that this PipelineCoupler will read from
	 * @param out
	 *            OutputStream that this PipelineCoupler will write to
	 */
	public PipelineCoupler(Interp interp, Channel in, OutputStream out) {
		super();
		this.ichannel = in;
		this.ostream = out;
		this.interp = interp;
	}

	/**
	 * @param interp
	 *            Interp instance that contains the Channel out
	 * @param in
	 *            InputStream that this PipelineCoupler will read from
	 * @param out
	 *            Channel that this PipelineCoupler will write to
	 */
	public PipelineCoupler(Interp interp, InputStream in, Channel out) {
		super();
		this.istream = in;
		/* If output is stdout or stderr, just use those streams directly */
		String chname = out.getChanName();
		if (chname != null) {
			if (chname.equals("stdout")) {
				this.ostream = System.out;
			} else if (chname.equals("stderr")) {
				this.ostream = System.err;
			}
		} 
		if (this.ostream==null) this.ochannel = out;
		this.interp = interp;
	}

	/**
	 * @param r
	 *            a receiver for any Exceptions
	 */
	public void setExceptionReceiver(ExceptionReceiver r) {
		exceptionReceiver = r;
	}

	/**
	 * @param o
	 *            Object to synchronize writes on
	 */
	public void setWriteMutex(Object o) {
		this.writeMutex = o;
	}

	/**
	 * Tell this PipelineCoupler that it should exit as soon as possible
	 */
	public void requestStop() {
		stop = true;
		// If istream is the stdin stream, close it to force read() to unblock
		if (istream != null && istream instanceof ManagedSystemInStream && istream != System.in) {
			try {
				istream.close();
			} catch (IOException e) {
				// do nothing
			}
		}
	}

	/**
	 * Called from PipelineCoupler.start()
	 */
	@Override
	public void run() {
		// System.out.println("Coupler count is "+runningCouplerCount);
		++runningCouplerCount;

		/**
		 * Buffer for bytes from/to streams
		 */
		byte[] bbuf = null;

		/**
		 * Buffer for bytes from/to channels
		 */
		TclObject tclbuf = null;

		/**
		 * Number of bytes in buffer
		 */
		int bufByteCount;

		bbuf = new byte[bufSize];
		tclbuf = TclByteArray.newInstance();

		try {
			while (true) {
				bufByteCount = -1;

				/*
				 * Read either from a stream or from a channel, as the
				 * constructor set up
				 */
				if (istream != null) {
					/*
					 * istreams are not actually buffered, for interactivity in
					 * case we're reading from stdin and writing to
					 * stdout/stderr
					 */
					int c = istream.read();
					if (c == -1)
						bufByteCount = -1;
					else {
						bufByteCount = 1;
						bbuf[0] = (byte) (c & 0xFF);
					}
				}

				if (ichannel != null) {
					bufByteCount = ichannel.read(interp, tclbuf, TclIO.READ_N_BYTES, bufSize);
					// End of input file?
					if (bufByteCount == 0 && ichannel.eof())
						bufByteCount = -1;

					/* convert to byte array for ostream */
					if (bufByteCount > 0) {
						bbuf = TclByteArray.getBytes(interp, tclbuf);
					}

				}

				/* Write to outputStream, if that's the destination */
				if (ostream != null) {
					synchronized (this.writeMutex) {
						if (bufByteCount > 0)
							ostream.write(bbuf, 0, bufByteCount);
						// flush for interactivity
						ostream.flush();
					}
				} else {
					/* Otherwise, write to channel */
					synchronized (this.writeMutex) {						
						if (bufByteCount > 0) {
							ochannel.write(interp, TclByteArray.newInstance(bbuf, 0, bufByteCount));
						}
						if (bufByteCount == -1) {
							ochannel.flush(interp);
						}
					}
				}

				/*
				 * Time to exit the coupler if requestStop() has been called, or
				 * end of file has been reached
				 */
				if (stop || bufByteCount == -1)
					break;

				if (bufByteCount == 0) {
					yield();
					continue;
				}
			}
		} catch (Exception e) {
			if (exceptionReceiver != null && exceptionReceiver.getException() == null) {
				exceptionReceiver.setException(e);
			}
		} finally {

			/*
			 * Close output streams, to cause neighbor processes to die Channels
			 * and stdin stream are closed as needed in Pipeline
			 */
			synchronized (writeMutex) {
				if (ostream != null && ostream != System.out && ostream != System.err) {
					try {
						ostream.close();
					} catch (IOException e) {
						// do nothing
					}
				}
			}
		}
		--runningCouplerCount;
	}

	/**
	 * ExceptionReceiver objects are receivers of any Exceptions that occur in
	 * an instance of PipelineCoupler
	 */
	public static class ExceptionReceiver {
		Exception e = null;

		/**
		 * @param e
		 *            The Exception to be received
		 */
		synchronized public void setException(Exception e) {
			this.e = e;
		}

		/**
		 * @return the Exception received, or null if none was received
		 */
		synchronized public Exception getException() {
			return e;
		}

		public TclException getAsTclException(Interp interp) {
			if (e == null)
				return null;
			if (e instanceof TclException)
				return (TclException) e;
			else
				return new TclException(interp, e.getMessage());
		}
	}

}
