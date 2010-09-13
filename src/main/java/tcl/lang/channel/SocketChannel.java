/*
 * SocketChannel.java
 *
 * Implements a socket channel.
 */
package tcl.lang.channel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclIO;
import tcl.lang.TclObject;
import tcl.lang.TclString;

/**
 * The SocketChannel class implements a channel object for Socket connections,
 * created using the socket command.
 **/

public class SocketChannel extends AbstractSocketChannel  {

	/**
	 * The java Socket object associated with this Channel
	 **/

	private Socket sock;

	/**
	 * Constructor - creates a new SocketChannel object with the given options.
	 * Also creates an underlying Socket object, and Input and Output Streams.
	 **/

	public SocketChannel(Interp interp, int mode, String localAddr,
			int localPort, boolean async, String address, int port)
			throws IOException, TclException {
		InetAddress localAddress = null;
		InetAddress addr = null;

		if (async)
			/* NOTE: When async sockets are supported, return error
			 * in connection with getError(Interp) below
			 */
			throw new TclException(interp,
					"Asynchronous socket connection not "
							+ "currently implemented");

		// Resolve addresses
		if (!localAddr.equals("")) {
			try {
				localAddress = InetAddress.getByName(localAddr);
			} catch (UnknownHostException e) {
				throw new TclException(interp, "host unknown: " + localAddr);
			}
		}

		try {
			addr = InetAddress.getByName(address);
		} catch (UnknownHostException e) {
			throw new TclException(interp, "host unknown: " + address);
		}

		// Set the mode of this socket.
		this.mode = mode;

		// Create the Socket object

		if ((localAddress != null) && (localPort != 0))
			sock = new Socket(addr, port, localAddress, localPort);
		else
			sock = new Socket(addr, port);

		// If we got this far, then the socket has been created.
		// Create the channel name
		setChanName(TclIO.getNextDescriptor(interp, "sock"));
	}

	/**
	 * Constructor for making SocketChannel objects from connections made to a
	 * ServerSocket.
	 **/

	public SocketChannel(Interp interp, Socket s) throws IOException,
			TclException {
		this.mode = TclIO.RDWR;
		this.sock = s;

		setChanName(TclIO.getNextDescriptor(interp, "sock"));
	}
	/* (non-Javadoc)
	 * @see tcl.lang.channel.Channel#implClose()
	 */
	@Override
	void implClose() throws IOException {
		sock.close();		
	}

	protected InputStream getInputStream() throws IOException {
		return sock.getInputStream();
	}

	protected OutputStream getOutputStream() throws IOException {
		return sock.getOutputStream();
	}

	@Override
	public TclObject getError(Interp interp) throws TclException {
		/* FIXME: return async errors when it is implemented */
		return TclString.newInstance("");
	}

	@Override
	InetAddress getLocalAddress() {
		return sock.getLocalAddress();
	}

	@Override
	int getLocalPort() {
		return sock.getLocalPort();
	}

	@Override
	InetAddress getPeerAddress() {
		return sock.getInetAddress();
	}

	@Override
	int getPeerPort() {
		return sock.getPort();
	}
	
}
