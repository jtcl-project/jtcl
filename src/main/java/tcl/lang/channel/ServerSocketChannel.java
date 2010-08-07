/*
 * ServerSocketChannel.java
 *
 * Implements a server side socket channel for the Jacl
 * interpreter.
 */
package tcl.lang.channel;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import tcl.lang.Interp;
import tcl.lang.TCL;
import tcl.lang.TclEvent;
import tcl.lang.TclException;
import tcl.lang.TclIO;
import tcl.lang.TclObject;
import tcl.lang.TclPosixException;

/**
 * The ServerSocketChannel class implements a channel object for ServerSocket
 * connections, created using the socket command.
 **/

public class ServerSocketChannel extends Channel {

	/**
	 * The java ServerSocket object associated with this Channel.
	 **/

	private ServerSocket sock;

	/**
	 * The interpreter to evaluate the callback in, when a connection is made.
	 **/

	private Interp cbInterp;

	/**
	 * The script to evaluate in the interpreter.
	 **/

	private TclObject callback;

	/**
	 * The thread which listens for new connections.
	 **/

	private AcceptThread acceptThread;

	/**
	 * Creates a new ServerSocketChannel object with the given options. Creates
	 * an underlying ServerSocket object, and a thread to handle connections to
	 * the socket.
	 **/

	public ServerSocketChannel(Interp interp, String localAddr, int port,
			TclObject callback) throws TclException {
		InetAddress localAddress = null;

		// Resolve address (if given)
		if (!localAddr.equals("")) {
			try {
				localAddress = InetAddress.getByName(localAddr);
			} catch (UnknownHostException e) {
				throw new TclException(interp, "host unkown: " + localAddr);
			}
		}
		this.mode = TclIO.CREAT; // Allow no reading or writing on channel
		this.callback = callback;
		this.cbInterp = interp;

		// Create the server socket.
		try {
			if (localAddress == null)
				sock = new ServerSocket(port);
			else
				sock = new ServerSocket(port, 0, localAddress);
		} catch (IOException ex) {
			throw new TclException(interp, ex.getMessage());
		}

		acceptThread = new AcceptThread(sock, this);

		setChanName(TclIO.getNextDescriptor(interp, "sock"));
		acceptThread.start();
	}

	synchronized void addConnection(Socket s) {
		// Create an event which executes the callback TclString with
		// the arguments sock, addr, port added.
		// First, create a SocketChannel for this Socket object.
		SocketChannel sChan = null;
		try {
			sChan = new SocketChannel(cbInterp, s);
			// Register this channel in the channel tables.
			TclIO.registerChannel(cbInterp, sChan);
		} catch (Exception e) {
			e.printStackTrace();
		}
		SocketConnectionEvent evt = new SocketConnectionEvent(cbInterp,
				callback, sChan.getChanName(), s.getInetAddress()
						.getHostAddress(), s.getPort());
		cbInterp.getNotifier().queueEvent((TclEvent) evt, TCL.QUEUE_TAIL);
	}

	// FIXME: Since this does not actually close the socket
	// right away, we run into errors in the test suite
	// saying the socket is already in use after the close
	// command has been issued. Need to figure out how to
	// deal with this issue.

	public void close() throws IOException {
		// Stop the event handler thread.
		// this might not happen for up to a minute!
		acceptThread.pleaseStop();

		super.close();
	}

	/* (non-Javadoc)
	 * @see tcl.lang.channel.Channel#implClose()
	 */
	@Override
	void implClose() throws IOException {
		sock.close();		
	}

	/**
	 * Override to provide specific errors for server socket.
	 **/

	public void seek(Interp interp, long offset, int mode) throws IOException,
			TclException {
		throw new TclPosixException(interp, TclPosixException.EACCES, true,
				"error during seek on \"" + getChanName() + "\"");
	}

	String getChanType() {
		return "tcp";
	}

	protected InputStream getInputStream() throws IOException {
		throw new RuntimeException("should never be called");
	}

	protected OutputStream getOutputStream() throws IOException {
		throw new RuntimeException("should never be called");
	}
}

class AcceptThread extends Thread {

	private ServerSocket sock;
	private ServerSocketChannel sschan;
	boolean keepRunning;

	public AcceptThread(ServerSocket s1, ServerSocketChannel s2) {
		sock = s1;

		// Every 10 seconds, we check to see if this socket has been closed:
		try {
			sock.setSoTimeout(10000);
		} catch (SocketException e) {
		}

		sschan = s2;
		keepRunning = true;
	}

	public void run() {
		try {
			while (keepRunning) {
				Socket s = null;
				try {
					s = sock.accept();
				} catch (InterruptedIOException ex) {
					// Timeout
					continue;
				} catch (IOException ex) {
					// Socket closed
					break;
				}
				// Get a connection
				sschan.addConnection(s);
			}
		} catch (Exception e) {
			// Something went wrong.
			e.printStackTrace();
		}
	}

	public void pleaseStop() {
		keepRunning = false;
	}
}
