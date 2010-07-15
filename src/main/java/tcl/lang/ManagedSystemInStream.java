package tcl.lang;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A class for managing the System.in. Avoids blocking the current thread on the
 * non-interruptible System.in.read(), by isolating all System.in.read()'s in a
 * separate thread. Allows other classes to use System.in in a coordinated way,
 * so that one class doesn't block access to System.in.
 * 
 * After the initial instance is created, Java objects can read from System.in
 * directly to get the benefits of this class, or can create another instance of
 * this class that can be close()'d to interrupt a blocked read.
 * 
 * This class forces standard input to remain unbuffered, so the JVM doesn't steal
 * bytes from stdin that might be useful to subsequent processes that run after this
 * JVM exits.
 */
public class ManagedSystemInStream extends InputStream implements Runnable {
	/**
	 * Set to true with this ManagedSystemInStream is closed.
	 */
	private volatile boolean streamClosed = false;

	/**
	 * The thread that directly reads standard input
	 */
	private static Thread readThread = null;

	/**
	 * Unbuffered FileInputStream that is attached to real stdin
	 */
	private static FileInputStream stdin = null;

	/**
	 * Set to true when an end-of-file is seen on System.in in this instance
	 */
	private boolean eofSeen = false;

	/**
	 * This object is synchronized on to allow communication between the current
	 * thread and the readThread
	 */
	private static Object mutex = new Object();

	/**
	 * Byte returned from the readThread
	 */
	private static int stdinByte;

	/**
	 * Set to true to request a byte from the readThread
	 */
	private static boolean requestStdinByte = false;

	/**
	 * Set to true when stdinByte contains a valid byte from readThread
	 */
	private static boolean stdinByteIsValid = false;

	/**
	 * Any exception caught in readThread; synchronized on mutex
	 */
	private static IOException ioException = null;

	/**
	 * number of instances of ManagedSystemInStream that have not yet been
	 * closed
	 */
	private static int nOpenInstances = 0;

	/**
	 * Is true for the instance installed on System.in
	 */
	private boolean isSystemInInstance = false;

	/**
	 * Create a new ManagedSystemInStream. If this is the first
	 * ManagedSystemInStream instance, System.in is set to this instance, so all
	 * direct access to System.in goes through this instance. Other objects can
	 * either read System.in directly, or create an instance of this class to
	 * read from.
	 */
	public ManagedSystemInStream() {
		super();
		synchronized (mutex) {
			// install this stream as System.in if a ManagedSystemInStream has
			// not yet
			// been installed on System.in
			if (stdin == null) {
				stdin = new FileInputStream(FileDescriptor.in);
				isSystemInInstance = true;
				System.setIn(this);
			}
			if (readThread == null) {
				ioException = null;
				readThread = new Thread(null, this, "ManagedSystemInStream reader thread");
				readThread.start();
			}

			++nOpenInstances;
		}
	}

	/**
	 * @return the true if this instance is installed on System.in
	 */
	public boolean isSystemInInstance() {
		return isSystemInInstance;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.InputStream#available()
	 */
	@Override
	public int available() throws IOException {
		return 0;
	}

	/**
	 * Closes this ManagedSystemInStream instance. Stops any blocked read() on
	 * this instance.
	 */
	@Override
	public void close() throws IOException {
		if (streamClosed)
			return;
		synchronized (mutex) {
			--nOpenInstances;
			streamClosed = true;
			mutex.notifyAll(); // interrupt any pending
								// ManagedSystemInStream.read()
		}
	}

	/**
	 * Reads the next byte of data from System.in. The value byte is returned as
	 * an int in the range 0 to 255. If no byte is available because the end of
	 * the stream has been reached, the value -1 is returned. This method blocks
	 * until input data is available, the end of the stream is detected, an
	 * exception is thrown or the stream is closed.
	 * 
	 * @return Next byte read from System.in, or -1 on end of file or -1 if this
	 *         stream is closed
	 */
	@Override
	public int read() throws IOException {
		while (true) {
			if (eofSeen || streamClosed)
				return -1;
			synchronized (mutex) {
				if (ioException != null) {
					IOException e = ioException;
					ioException = null;
					throw e;
				}

				if (stdinByteIsValid) {
					stdinByteIsValid = false;
					if (stdinByte == -1)
						eofSeen = true;
					return stdinByte;
				} else {
					// if no pending request exists, make one
					if (!requestStdinByte) {
						requestStdinByte = true;
						mutex.notifyAll();
					}
					try {
						mutex.wait(100); // poll for stream close
					} catch (InterruptedException e) {
						// do nothing
					}
				}
			}
		}
	}

	/**
	 * Reads from System.in directly in a separate thread.
	 */
	public void run() {
		boolean doRead;
		IOException savedException;
		int valueRead = -1;

		while (true) {
			if (Thread.interrupted())
				break;
			synchronized (mutex) {
				doRead = requestStdinByte & !stdinByteIsValid;
				if (!doRead)
					try {
						mutex.wait();
					} catch (InterruptedException e) {
						break;
					}
			}
			if (doRead) {
				savedException = null;
				try {
					valueRead = stdin.read();
				} catch (IOException e1) {
					savedException = e1;
				}
				synchronized (mutex) {
					if (savedException == null) {
						stdinByte = valueRead;
						stdinByteIsValid = true;
						requestStdinByte = false;
					} else {
						ioException = savedException;
						requestStdinByte = false;
					}
					mutex.notifyAll();
				}
			}
		}
	}
}
