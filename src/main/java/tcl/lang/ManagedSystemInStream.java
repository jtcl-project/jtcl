package tcl.lang;

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
 */
public class ManagedSystemInStream extends InputStream implements Runnable {
	/**
	 * Set to true with this ManagedSystemInStream is closed.
	 */
	private volatile boolean streamClosed = false;

	/**
	 * The thread that directly reads System.in
	 */
	private static Thread readThread = null;

	/**
	 * Size of the buffer that passed data from System.in between the current
	 * thread and readThread.
	 */
	final static int bufferSize = 256;

	/**
	 * Set to true when an end-of-file is seen on System.in in this instance
	 */
	private  boolean eofSeen = false;

	/**
	 * This object is synchronized on to allow communication between the current
	 * thread and the readThread
	 */
	private static Object mutex = new Object();

	/**
	 * Any exception caught in readThread; synchronized on mutex
	 */
	private static IOException ioException = null;

	/**
	 * buffer passed from readThread to this thread containing data read from
	 * System.in
	 */
	private static int[] data = new int[bufferSize];

	/**
	 * index in data of next byte to return from read()
	 */
	private static int nextRead = 0;

	/**
	 * index in data of next byte to write when the readThread does
	 * System.in.read()
	 */
	private static int nextWrite = 0;

	/**
	 * number of instances of ManagedSystemInStream that have not yet been
	 * closed
	 */
	private static int nOpenInstances = 0;


	/**
	 * Original stream that System.in was set to prior to the first instance of
	 * this class
	 */
	private static InputStream originalSystemIn = null;

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
			if (originalSystemIn == null) {
				originalSystemIn = System.in;
				isSystemInInstance = true;
				System.setIn(this);
			}
			if (readThread == null) {
				nextRead = 0;
				nextWrite = 0;
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
		synchronized (mutex) {
			// always return at least 1, to allow for reading EOF
			return (nextWrite - nextRead) + originalSystemIn.available() + 1;
		}
	}

	/**
	 * Closes this ManagedSystemInStream instance. Stops any blocked read() on
	 * this instance. If this is the last non-closed() instance, restores
	 * System.in to its original value.
	 */
	@Override
	public void close() throws IOException {
		if (streamClosed)
			return;
		streamClosed = true;
		synchronized (mutex) {
			--nOpenInstances;
			if (nOpenInstances == 0 && readThread != null) {
				// kill the readThread. If it's blocked on a read, it will not
				// die until it gets a byte
				readThread.interrupt();
				readThread = null;
				// reset System.in
				System.setIn(originalSystemIn);
				originalSystemIn = null;
			}
			mutex.notify(); // interrupt any pending ManaagedSystemInStream.read()
		}
		// don't close System.in
		super.close();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.InputStream#mark(int)
	 */
	@Override
	public synchronized void mark(int readlimit) {
		originalSystemIn.mark(readlimit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.InputStream#markSupported()
	 */
	@Override
	public boolean markSupported() {
		return originalSystemIn.markSupported();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.InputStream#reset()
	 */
	@Override
	public synchronized void reset() throws IOException {
		originalSystemIn.reset();
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
	public synchronized int read() throws IOException {
		while (true) {
			if (streamClosed)
				return -1;
			synchronized (mutex) {
				if (ioException != null) {
					IOException e = ioException;
					ioException = null;
					throw e;
				}
				if (eofSeen)
					return -1;
				if (nextRead == nextWrite)
					try {
						mutex.wait(100); // poll for closed channel
					} catch (InterruptedException e) {
						// do nothing
					}
				else {
					int c = data[nextRead++];
					if (nextRead == bufferSize)
						nextRead = 0;
					if (c == -1)
						eofSeen = true;
					return c;
				}
			}
		}
	}

	/**
	 * Reads from System.in directly in a separate thread.
	 */
	public void run() {
		int nAvailable = 0;

		while (true) {
			if (Thread.interrupted())
				break;
			nAvailable = 0;
			try {
				nAvailable = originalSystemIn.available();
			} catch (IOException e) {
				synchronized (mutex) {
					ioException = e;
					mutex.notifyAll();
				}
			}
			/*
			 * Grab whatever is immediately available inside of the synchronized
			 * section
			 */
			if (nAvailable > 0) {
				synchronized (mutex) {
					for (int i = 0; i < nAvailable; i++) {
						if (nextRead == nextWrite + 1 || (nextWrite == bufferSize - 1 && nextRead == 0))
							break; // don't overwrite buffer
						int c;
						try {
							c = originalSystemIn.read();
						} catch (IOException e) {
							ioException = e;
							break;
						}
						data[nextWrite++] = c;
						if (nextWrite == bufferSize)
							nextWrite = 0;
					}
					mutex.notifyAll();
				}
			} else {
				/*
				 * Possibly block. Because System.in.available() returns 0 even
				 * if EOF has been reached, we actually have to test for EOF
				 * with a blocking call to read()
				 */
				int c = 0;
				IOException exception = null;
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {
					return;
				}
				try {
					c = originalSystemIn.read();
				} catch (IOException e) {
					exception = e;
					synchronized (mutex) {
						ioException = e;
						mutex.notifyAll();
					}
				}

				/*
				 * If we didn't get an exception, stuff the read character into
				 * the circular buffer
				 */
				while (exception == null) {
					synchronized (mutex) {
						if (nextRead == nextWrite + 1 || (nextWrite == bufferSize - 1 && nextRead == 0)) {
							// buffer is full, let's wait to be notified when
							// it's been emptied some
							try {
								mutex.wait();
							} catch (InterruptedException e) {
								return; // exit thread if interrupted
							}
						} else {
							data[nextWrite++] = c;
							if (nextWrite == bufferSize)
								nextWrite = 0;
							mutex.notifyAll();
							break;
						}
					}
				}
			}
		}
	}

}
