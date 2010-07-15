/*
 * Channel.java
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: Channel.java,v 1.27 2006/07/07 23:36:00 mdejong Exp $
 */

package tcl.lang.channel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclIO;
import tcl.lang.TclObject;
import tcl.lang.TclPosixException;
import tcl.lang.TclRuntimeError;
import tcl.lang.TclString;
import tcl.lang.cmd.EncodingCmd;

/**
 * The Channel class provides functionality that will be needed for any type of
 * Tcl channel. It performs generic reads, writes, without specifying how a
 * given channel is actually created. Each new channel type will need to extend
 * the abstract Channel class and override any methods it needs to provide a
 * specific implementation for.
 */

public abstract class Channel {

	/**
	 * The read, write, append and create flags are set here. The variables used
	 * to set the flags are found in the class TclIO.
	 */

	protected int mode;

	/**
	 * This is a unique name that sub-classes need to set. It is used as the key
	 * in the hashtable of registered channels (in interp).
	 */

	private String chanName;

	/**
	 * How many interpreters hold references to this IO channel?
	 */

	public int refCount = 0;

	/**
	 * Tcl input and output objecs. These are like a mix between a Java Stream
	 * and a Reader.
	 */

	protected TclInputStream input = null;
	protected TclOutputStream output = null;

	/**
	 * Set to false when channel is in non-blocking mode.
	 */

	protected boolean blocking = true;

	/**
	 * Buffering (full,line, or none)
	 */

	protected int buffering = TclIO.BUFF_FULL;

	/**
	 * Buffer size, in bytes, allocated for channel to store input or output
	 */

	protected int bufferSize = 4096;

	/**
	 * Name of Java encoding for this Channel. A null value means use no
	 * encoding (binary).
	 */

	// FIXME: Check to see if this field is updated after a call
	// to "encoding system $enc" for new Channel objects!

	protected String encoding;
	protected int bytesPerChar;

	/**
	 * Translation mode for end-of-line character
	 */

	protected int inputTranslation = TclIO.TRANS_AUTO;
	protected int outputTranslation = TclIO.TRANS_PLATFORM;

	/**
	 * If nonzero, use this as a signal of EOF on input.
	 */

	protected char inputEofChar = 0;

	/**
	 * If nonzero, append this to a writeable channel on close.
	 */

	protected char outputEofChar = 0;

	Channel() {
		setEncoding(EncodingCmd.systemJavaEncoding);
	}

	/**
	 * Tcl_ReadChars -> read
	 * 
	 * Read data from the Channel into the given TclObject.
	 * 
	 * @param interp
	 *            is used for TclExceptions.
	 * @param tobj
	 *            the object data will be added to.
	 * @param readType
	 *            specifies if the read should read the entire buffer
	 *            (TclIO.READ_ALL), the next line (TclIO.READ_LINE), of a
	 *            specified number of bytes (TclIO.READ_N_BYTES).
	 * @param numBytes
	 *            the number of bytes/chars to read. Used only when the readType
	 *            is TclIO.READ_N_BYTES.
	 * @return the number of bytes read. Returns -1 on EOF or on error.
	 * @exception TclException
	 *                is thrown if read occurs on WRONLY channel.
	 * @exception IOException
	 *                is thrown when an IO error occurs that was not correctly
	 *                tested for. Most cases should be caught.
	 */

	public int read(Interp interp, TclObject tobj, int readType, int numBytes)
			throws IOException, TclException {
		TclObject dataObj;

		checkRead(interp);
		initInput();

		switch (readType) {
		case TclIO.READ_ALL: {
			return input.doReadChars(tobj, -1);
		}
		case TclIO.READ_LINE: {
			return input.getsObj(tobj);
		}
		case TclIO.READ_N_BYTES: {
			return input.doReadChars(tobj, numBytes);
		}
		default: {
			throw new TclRuntimeError("Channel.read: Invalid read mode.");
		}
		}
	}

	/**
	 * Tcl_WriteObj -> write
	 * 
	 * Write data to the Channel
	 * 
	 * @param interp
	 *            is used for TclExceptions.
	 * @param outData
	 *            the TclObject that holds the data to write.
	 */

	public void write(Interp interp, TclObject outData) throws IOException,
			TclException {

		checkWrite(interp);
		initOutput();

		// FIXME: Is it possible for a write to happen with a null output?
		if (output != null) {
			output.writeObj(outData);
		}
	}

	/**
	 * Tcl_WriteChars -> write
	 * 
	 * Write string data to the Channel.
	 * 
	 * @param interp
	 *            is used for TclExceptions.
	 * @param outStr
	 *            the String object to write.
	 */

	public void write(Interp interp, String outStr) throws IOException,
			TclException {
		write(interp, TclString.newInstance(outStr));
	}

	/**
	 * Close the Channel. The channel is only closed, it is the responsibility
	 * of the "closer" to remove the channel from the channel table.
	 */

	public void close() throws IOException {

		IOException ex = null;

		if (input != null) {
			try {
				input.close();
			} catch (IOException e) {
				ex = e;
			}
			input = null;
		}

		if (output != null) {
			try {
				output.close();
			} catch (IOException e) {
				ex = e;
			}
			output = null;
		}

		if (ex != null)
			throw ex;
	}

	/**
	 * Flush the Channel.
	 * 
	 * @exception TclException
	 *                is thrown when attempting to flush a read only channel.
	 * @exception IOEcception
	 *                is thrown for all other flush errors.
	 */

	public void flush(Interp interp) throws IOException, TclException {

		checkWrite(interp);

		if (output != null) {
			output.flush();
		}
	}

	/**
	 * Move the current file pointer. If seek is not supported on the given
	 * channel then -1 will be returned. A subclass should override this method
	 * if it supports the seek operation.
	 * 
	 * @param interp
	 *            currrent interpreter.
	 * @param offset
	 *            The number of bytes to move the file pointer.
	 * @param mode
	 *            where to begin incrementing the file pointer; beginning,
	 *            current, end.
	 */

	public void seek(Interp interp, long offset, int mode) throws IOException,
			TclException {
		throw new TclPosixException(interp, TclPosixException.EINVAL, true,
				"error during seek on \"" + getChanName() + "\"");
	}

	/**
	 * Return the current file pointer. If tell is not supported on the given
	 * channel then -1 will be returned. A subclass should override this method
	 * if it supports the tell operation.
	 */

	public long tell() throws IOException {
		return (long) -1;
	}

	/**
	 * Setup the TclInputStream on the first call to read
	 */

	protected void initInput() throws IOException {
		if (input != null)
			return;

		input = new TclInputStream(getInputStream());
		input.setEncoding(encoding);
		input.setTranslation(inputTranslation);
		input.setEofChar(inputEofChar);
		input.setBuffering(buffering);
		input.setBufferSize(bufferSize);
		input.setBlocking(blocking);
	}

	/**
	 * Setup the TclOutputStream on the first call to write
	 */

	protected void initOutput() throws IOException {
		if (output != null)
			return;

		output = new TclOutputStream(getOutputStream());
		output.setEncoding(encoding);
		output.setTranslation(outputTranslation);
		output.setEofChar(outputEofChar);
		output.setBuffering(buffering);
		output.setBufferSize(bufferSize);
		output.setBlocking(blocking);
		if (getChanType().equals("file")) {
			output.setSync(true);
		}
	}

	/**
	 * Returns true if the last read reached the EOF.
	 */

	public final boolean eof() {
		if (input != null)
			return input.eof();
		else
			return false;
	}

	/**
	 * This method should be overridden in the subclass to provide a channel
	 * specific InputStream object.
	 */

	protected abstract InputStream getInputStream() throws IOException;

	/**
	 * This method should be overridden in the subclass to provide a channel
	 * specific OutputStream object.
	 */

	protected abstract OutputStream getOutputStream() throws IOException;

	/**
	 * Gets the chanName that is the key for the chanTable hashtable.
	 * 
	 * @return channelId
	 */

	public String getChanName() {
		return chanName;
	}

	/**
	 * Return a string that describes the channel type.
	 * 
	 * This is the equivilent of the Tcl_ChannelType->typeName field.
	 */

	abstract String getChanType();

	/**
	 * Return number of references to this Channel.
	 */

	int getRefCount() {
		return refCount;
	}

	/**
	 * Sets the chanName that is the key for the chanTable hashtable.
	 * 
	 * @param chan
	 *            the unique channelId
	 */

	void setChanName(String chan) {
		chanName = chan;
	}

	public boolean isReadOnly() {
		return ((mode & TclIO.RDONLY) != 0);
	}

	public boolean isWriteOnly() {
		return ((mode & TclIO.WRONLY) != 0);
	}

	public boolean isReadWrite() {
		return ((mode & TclIO.RDWR) != 0);
	}

	// Helper methods to check read/write permission and raise a
	// TclException if reading is not allowed.

	protected void checkRead(Interp interp) throws TclException {
		if (!isReadOnly() && !isReadWrite()) {
			throw new TclException(interp, "channel \"" + getChanName()
					+ "\" wasn't opened for reading");
		}
	}

	protected void checkWrite(Interp interp) throws TclException {
		if (!isWriteOnly() && !isReadWrite()) {
			throw new TclException(interp, "channel \"" + getChanName()
					+ "\" wasn't opened for writing");
		}
	}

	/**
	 * Query blocking mode.
	 */

	public boolean getBlocking() {
		return blocking;
	}

	/**
	 * Set blocking mode.
	 * 
	 * @param blocking
	 *            new blocking mode
	 */

	public void setBlocking(boolean inBlocking) {
		blocking = inBlocking;

		if (input != null)
			input.setBlocking(blocking);
		if (output != null)
			output.setBlocking(blocking);
	}

	/**
	 * Query buffering mode.
	 */

	public int getBuffering() {
		return buffering;
	}

	/**
	 * Set buffering mode
	 * 
	 * @param buffering
	 *            One of TclIO.BUFF_FULL, TclIO.BUFF_LINE, or TclIO.BUFF_NONE
	 */

	public void setBuffering(int inBuffering) {
		if (inBuffering < TclIO.BUFF_FULL || inBuffering > TclIO.BUFF_NONE)
			throw new TclRuntimeError(
					"invalid buffering mode in Channel.setBuffering()");

		buffering = inBuffering;
		if (input != null)
			input.setBuffering(buffering);
		if (output != null)
			output.setBuffering(buffering);
	}

	/**
	 * Query buffer size
	 */

	public int getBufferSize() {
		return bufferSize;
	}

	/**
	 * Tcl_SetChannelBufferSize -> setBufferSize
	 * 
	 * @param size
	 *            new buffer size
	 */

	public void setBufferSize(int size) {

		// If the buffer size is smaller than 1 byte or larger than 1 Meg
		// do not accept the requested size and leave the current buffer size.

		if ((size < 1) || (size > (1024 * 1024))) {
			return;
		}

		bufferSize = size;
		if (input != null)
			input.setBufferSize(bufferSize);
		if (output != null)
			output.setBufferSize(bufferSize);
	}

	int getNumBufferedInputBytes() {
		if (input != null)
			return input.getNumBufferedBytes();
		else
			return 0;
	}

	int getNumBufferedOutputBytes() {
		if (output != null)
			return output.getNumBufferedBytes();
		else
			return 0;
	}

	/**
	 * Tcl_InputBlocked -> isBlocked
	 * 
	 * Returns true if input is blocked on this channel, false otherwise.
	 * 
	 */

	public boolean isBlocked(Interp interp) throws TclException {
		checkRead(interp);

		if (input != null)
			return input.isBlocked();
		else
			return false;
	}

	/**
	 * Returns true if a background flush is waiting to happen.
	 */

	boolean isBgFlushScheduled() {
		// FIXME: Need to query output here
		return false;
	}

	/**
	 * Channel is in CRLF eol input translation mode and the last byte seen was
	 * a CR.
	 */

	boolean inputSawCR() {
		if (input != null)
			return input.sawCR();
		return false;
	}

	/**
	 * Query encoding
	 * 
	 * @return Name of Channel's Java encoding (null if no encoding)
	 */

	public String getEncoding() {
		return encoding;
	}

	/**
	 * Set new Java encoding
	 */

	public void setEncoding(String inEncoding) {
		encoding = inEncoding;
		if (encoding == null) {
			bytesPerChar = 1;
		} else {
			bytesPerChar = EncodingCmd.getBytesPerChar(encoding);
		}

		if (input != null)
			input.setEncoding(encoding);
		if (output != null)
			output.setEncoding(encoding);

		// FIXME: Pass bytesPerChar to input and output
	}

	/**
	 * Query input translation
	 */

	public int getInputTranslation() {
		return inputTranslation;
	}

	/**
	 * Set new input translation
	 */

	public void setInputTranslation(int translation) {
		inputTranslation = translation;
		if (input != null)
			input.setTranslation(inputTranslation);
	}

	/**
	 * Query output translation
	 */

	public int getOutputTranslation() {
		return outputTranslation;
	}

	/**
	 * Set new output translation
	 */

	public void setOutputTranslation(int translation) {
		outputTranslation = translation;
		if (output != null)
			output.setTranslation(outputTranslation);
	}

	/**
	 * Query input eof character
	 */

	public char getInputEofChar() {
		return inputEofChar;
	}

	/**
	 * Set new input eof character
	 */

	public void setInputEofChar(char inEof) {
		// Store as a byte, not a unicode character
		inputEofChar = (char) (inEof & 0xFF);
		if (input != null)
			input.setEofChar(inputEofChar);
	}

	/**
	 * Query output eof character
	 */

	public char getOutputEofChar() {
		return outputEofChar;
	}

	/**
	 * Set new output eof character
	 */

	public void setOutputEofChar(char outEof) {
		// Store as a byte, not a unicode character
		outputEofChar = (char) (outEof & 0xFF);
		if (output != null)
			output.setEofChar(outputEofChar);
	}

}
