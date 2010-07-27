/*
 * TclOutputStream.java
 *
 * Copyright (c) 2003 Mo DeJong
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: TclOutputStream.java,v 1.4 2006/07/07 23:36:00 mdejong Exp $
 */

// A TclOutputStream is a cross between a Java OutputStream and
// a Writer. The class supports writing raw bytes as well as
// encoded characters.

package tcl.lang.channel;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

import tcl.lang.Interp;
import tcl.lang.TclByteArray;
import tcl.lang.TclIO;
import tcl.lang.TclObject;
import tcl.lang.TclPosixException;
import tcl.lang.TclRuntimeError;

class TclOutputStream {

	/**
	 * The Java byte stream object data will be written to.
	 */

	private OutputStream output;

	/**
	 * If nonzero, use this character as EOF marker.
	 */

	private char eofChar;

	/**
	 * Translation mode for end-of-line character
	 */

	protected int translation;

	/**
	 * Name of Java encoding for this Channel. A null value means use no
	 * encoding (binary).
	 */

	protected String encoding;

	/**
	 * Charset encoder object. A null value means that no conversions have been
	 * done yet.
	 */

	protected CharsetEncoder cse = null;

	/**
	 * Buffering
	 */

	protected int buffering;

	/**
	 * Blocking
	 */

	protected boolean blocking;

	/**
	 * Blocked
	 */

	protected boolean blocked = false;

	/**
	 * Buffer size in bytes
	 */

	protected int bufSize;

	/**
	 * Staging area used to store chars before conversion into buffered bytes.
	 */

	protected char[] outputStage = null;

	/**
	 * Flags used to track encoding states. The encodingState member of called
	 * outputEncodingState in the C ChannelState type. The encodingStart and
	 * encodingEnd members combined are called outputEncodingFlags and have the
	 * bit values TCL_ENCODING_END and TCL_ENCODING_START.
	 */

	Object encodingState = null;
	boolean encodingStart = true;
	boolean encodingEnd = false;

	/**
	 * First and last buffers in the output queue and the current buffer being
	 * filled.
	 */

	ChannelBuffer outQueueHead = null;
	ChannelBuffer outQueueTail = null;
	ChannelBuffer curOut = null;

	/**
	 * Used to track buffer state, these are bit flags stored in the flags filed
	 * in the C impl.
	 */

	protected boolean bufferReady = false;
	protected boolean bgFlushScheduled = false;
	protected boolean closed = false;

	/**
	 * Posix error code of deferred error.
	 */
	protected int unreportedError = 0;

	/**
	 * FIXME: add desc
	 */

	protected int refCount = 0;

	/**
	 * This flag is true when the OutputStream is from a file on disk that
	 * should be sync()'ed after a flush. A sync should not be used for non-file
	 * streams.
	 */

	protected boolean canSync = false;

	/**
	 * Constructor for Tcl input stream class. We require a byte stream source
	 * at init time, the stram can't be changed after the TclInputStream is
	 * created.
	 */

	TclOutputStream(OutputStream inOutput) {
		output = inOutput;
	}

	/**
	 * Set the sync flag for a channel so that a sync will be invoked in
	 * addition to a flush.
	 */

	void setSync(boolean canSync) {
		this.canSync = canSync;
	}

	/**
	 * Tcl_Close -> close
	 * 
	 * Closes a channel.
	 * 
	 * Closes the channel if this is the last reference.
	 * 
	 * close removes the channel as far as the user is concerned. However, it
	 * may continue to exist for a while longer if it has a background flush
	 * scheduled. The device itself is eventually closed and the channel record
	 * removed, in closeChannel.
	 */

	void close() throws IOException {
		// CloseCallback *cbPtr;
		// Channel *chanPtr;
		// ChannelState *statePtr;
		int result;

		// Perform special handling for standard channels being closed. If the
		// refCount is now 1 it means that the last reference to the standard
		// channel is being explicitly closed, so bump the refCount down
		// artificially to 0. This will ensure that the channel is actually
		// closed, below. Also set the static pointer to NULL for the channel.

		// CheckForStdChannelsBeingClosed();

		// This operation should occur at the top of a channel stack.

		// chanPtr = (Channel *) chan;
		// statePtr = chanPtr->state;
		// chanPtr = statePtr->topChanPtr;

		if (refCount > 0) {
			throw new TclRuntimeError(
					"called Tcl_Close on channel with refCount > 0");
		}

		// When the channel has an escape sequence driven encoding such as
		// iso2022, the terminated escape sequence must write to the buffer.

		if ((encoding != null) && (curOut != null)
		/* && (CheckChannelErrors(statePtr, TCL_WRITABLE) == 0) */) {
			encodingEnd = true;
			char[] empty = new char[0];
			writeChars(empty, 0, 0);
		}

		// FIXME: Impl channel close callbacks ???
		// Tcl_ClearChannelHandlers(chan);

		// Invoke the registered close callbacks and delete their records.

		// while (statePtr->closeCbPtr != (CloseCallback *) NULL) {
		// cbPtr = statePtr->closeCbPtr;
		// statePtr->closeCbPtr = cbPtr->nextPtr;
		// (cbPtr->proc) (cbPtr->clientData);
		// ckfree((char *) cbPtr);
		// }

		// Ensure that the last output buffer will be flushed.

		if ((curOut != null) && (curOut.nextAdded > curOut.nextRemoved)) {
			bufferReady = true;
		}

		// If this channel supports it, close the read side, since we don't need
		// it
		// anymore and this will help avoid deadlocks on some channel types.

		// if (chanPtr->typePtr->closeProc == TCL_CLOSE2PROC) {
		// result = (chanPtr->typePtr->close2Proc)(chanPtr->instanceData,
		// interp,
		// TCL_CLOSE_READ);
		// } else {
		// result = 0;
		// }
		result = 0;

		// The call to flushChannel will flush any queued output and invoke
		// the close function of the channel driver, or it will set up the
		// channel to be flushed and closed asynchronously.

		closed = true;
		if ((flushChannel(/* interp */null, false) != 0) || (result != 0)) {
			// FIXME: We should raise a TclPosixException here instead
			// return TCL_ERROR;
			throw new IOException("Exception in flushChannel");
		}
	}

	/**
	 * CloseChannel -> closeChannel
	 * 
	 * Utility procedure to close a channel and free associated resources.
	 * 
	 * If the channel was stacked, then the it will copy the necessary elements
	 * of the NEXT channel into the TOP channel, in essence unstacking the
	 * channel. The NEXT channel will then be freed.
	 * 
	 * If the channel was not stacked, then we will free all the bits for the
	 * TOP channel, including the data structure itself.
	 * 
	 * Returns 1 if the channel was stacked, 0 otherwise.
	 */

	protected int closeChannel(Interp interp, int errorCode) throws IOException {
		int result = 0;
		// ChannelState *statePtr; // state of the channel stack.
		// ThreadSpecificData *tsdPtr = TCL_TSD_INIT(&dataKey);

		// if (chanPtr == NULL) {
		// return result;
		// }
		// statePtr = chanPtr->state;

		// Discard a leftover buffer in the current output buffer field.

		if (curOut != null) {
			// ckfree((char *) statePtr->curOutPtr);
			curOut = null;
		}

		// The caller guarantees that there are no more buffers
		// queued for output.

		if (outQueueHead != null) {
			throw new TclRuntimeError(
					"TclFlush, closed channel: queued output left");
		}

		// If the EOF character is set in the channel, append that to the
		// output device.

		if (eofChar != 0) {
			try {
				output.write((byte) eofChar);
			} catch (IOException ex) {
				// FIXME: How can we recover here??
				ex.printStackTrace(System.err);
			}
		}

		// Remove this channel from of the list of all channels.

		// Tcl_CutChannel((Tcl_Channel) chanPtr);

		// Close and free the channel driver state.

		// if (chanPtr->typePtr->closeProc != TCL_CLOSE2PROC) {
		// result = (chanPtr->typePtr->closeProc)(chanPtr->instanceData,
		// interp);
		// } else {
		// result = (chanPtr->typePtr->close2Proc)(chanPtr->instanceData,
		// interp,
		// 0);
		// }

		// Some resources can be cleared only if the bottom channel
		// in a stack is closed. All the other channels in the stack
		// are not allowed to remove.

		// if (chanPtr == statePtr->bottomChanPtr) {
		// if (statePtr->channelName != (char *) NULL) {
		// ckfree((char *) statePtr->channelName);
		// statePtr->channelName = NULL;
		// }

		// Tcl_FreeEncoding(statePtr->encoding);
		// if (statePtr->outputStage != NULL) {
		// ckfree((char *) statePtr->outputStage);
		// statePtr->outputStage = (char *) NULL;
		// }
		// }

		// If we are being called synchronously, report either
		// any latent error on the channel or the current error.

		if (unreportedError != 0) {
			errorCode = unreportedError;
		}
		if (errorCode == 0) {
			errorCode = result;
			if (errorCode != 0) {
				// FIXME: How can we deal with this errno issue?
				// Tcl_SetErrno(errorCode);
			}
		}

		// Cancel any outstanding timer.

		// Tcl_DeleteTimerHandler(statePtr->timer);

		// Mark the channel as deleted by clearing the type structure.

		// if (chanPtr->downChanPtr != (Channel *) NULL) {
		// Channel *downChanPtr = chanPtr->downChanPtr;

		// statePtr->nextCSPtr = tsdPtr->firstCSPtr;
		// tsdPtr->firstCSPtr = statePtr;

		// statePtr->topChanPtr = downChanPtr;
		// downChanPtr->upChanPtr = (Channel *) NULL;
		// chanPtr->typePtr = NULL;

		// Tcl_EventuallyFree((ClientData) chanPtr, TCL_DYNAMIC);
		// return Tcl_Close(interp, (Tcl_Channel) downChanPtr);
		// }

		// There is only the TOP Channel, so we free the remaining
		// pointers we have and then ourselves. Since this is the
		// last of the channels in the stack, make sure to free the
		// ChannelState structure associated with it. We use
		// Tcl_EventuallyFree to allow for any last

		// chanPtr->typePtr = NULL;

		// Tcl_EventuallyFree((ClientData) statePtr, TCL_DYNAMIC);
		// Tcl_EventuallyFree((ClientData) chanPtr, TCL_DYNAMIC);

		return errorCode;
	}

	/**
	 * Tcl_Flush -> flush
	 * 
	 * Flushes output data on a channel.
	 */

	void flush() throws IOException {
		// Force current output buffer to be output also.

		if ((curOut != null) && (curOut.nextAdded > curOut.nextRemoved)) {
			bufferReady = true;
		}

		int result = flushChannel(null, false);
		if (result != 0) {
			// FIXME: Should we throw an exception here?
			throw new IOException("Exception during flushChannel");
		}
	}

	/**
	 * FlushChannel -> flushChannel
	 * 
	 * This function flushes as much of the queued output as is possible now. If
	 * calledFromAsyncFlush is true, it is being called in an event handler to
	 * flush channel output asynchronously.
	 * 
	 * Return 0 if successful, else the error code that was returned by the
	 * channel type operation.
	 * 
	 * May produce output on a channel. May block indefinitely if the channel is
	 * synchronous. May schedule an async flush on the channel. May recycle
	 * memory for buffers in the output queue.
	 * 
	 * @param interp
	 *            Interp object.
	 * @param calledFromAsyncFlush
	 *            True if called from an asynchronous flush callback.
	 */

	int flushChannel(Interp interp, boolean calledFromAsyncFlush)
			throws IOException {
		// ChannelState *statePtr = chanPtr->state;
		ChannelBuffer buf;
		int toWrite; // Amount of output data in current
		// buffer available to be written.
		int written; // Amount of output data actually
		// written in current round.
		int errorCode = 0; // Stores POSIX error codes from
		// channel driver operations.
		boolean wroteSome = false; // Set to true if any data was
		// written to the driver.

		try {

			// Prevent writing on a dead channel -- a channel that has been
			// closed
			// but not yet deallocated. This can occur if the exit handler for
			// the
			// channel deallocation runs before all channels are deregistered in
			// all interpreters.

			// if (CheckForDeadChannel(interp, statePtr)) return -1;

			// Loop over the queued buffers and attempt to flush as
			// much as possible of the queued output to the channel.

			while (true) {
				// If the queue is empty and there is a ready current buffer, OR
				// if
				// the current buffer is full, then move the current buffer to
				// the
				// queue.

				if (((curOut != null) && (curOut.nextAdded == curOut.bufLength))
						|| (bufferReady && (outQueueHead == null))) {
					bufferReady = false;
					curOut.next = null;
					if (outQueueHead == null) {
						outQueueHead = curOut;
					} else {
						outQueueTail.next = curOut;
					}
					outQueueTail = curOut;
					curOut = null;
				}
				buf = outQueueHead;

				// If we are not being called from an async flush and an async
				// flush is active, we just return without producing any output.

				if ((!calledFromAsyncFlush) && bgFlushScheduled) {
					return 0;
				}

				// If the output queue is still empty, break out of the while
				// loop.

				if (buf == null) {
					break; // Out of the "while (true)".
				}

				// Produce the output on the channel.

				toWrite = buf.nextAdded - buf.nextRemoved;
				// written = (chanPtr->typePtr->outputProc)
				// (chanPtr->instanceData,
				// bufPtr->buf + bufPtr->nextRemoved, toWrite,
				// &errorCode);
				try {
					output.write(buf.buf, buf.nextRemoved, toWrite);
					written = toWrite;
				} catch (IOException ex) {
					// FIXME: How can we recover and get posix errors?
					ex.printStackTrace(System.err);
					errorCode = TclPosixException.EIO; // Generic I/O error ???
					written = -1;
				}

				// If the write failed completely attempt to start the
				// asynchronous
				// flush mechanism and break out of this loop - do not attempt
				// to
				// write any more output at this time.

				if (written < 0) {
					// If the last attempt to write was interrupted, simply
					// retry.

					if (errorCode == TclPosixException.EINTR) {
						errorCode = 0;
						continue;
					}

					// If the channel is non-blocking and we would have blocked,
					// start a background flushing handler and break out of the
					// loop.

					if ((errorCode == TclPosixException.EWOULDBLOCK)
							|| (errorCode == TclPosixException.EAGAIN)) {
						// This used to check for CHANNEL_NONBLOCKING, and panic
						// if the channel was blocking. However, it appears
						// that setting stdin to -blocking 0 has some effect on
						// the stdout when it's a tty channel (dup'ed
						// underneath)

						if (!bgFlushScheduled) {
							bgFlushScheduled = true;
							updateInterest();
						}
						errorCode = 0;
						break;
					}

					// Decide whether to report the error upwards or defer it.

					if (calledFromAsyncFlush) {
						if (unreportedError == 0) {
							unreportedError = errorCode;
						}
					} else {
						// FIXME: Need to figure out what to do here!
						// Tcl_SetErrno(errorCode);
						// if (interp != NULL) {
						// // Casting away CONST here is safe because the
						// // TCL_VOLATILE flag guarantees CONST treatment
						// // of the Posix error string.
						// Tcl_SetResult(interp,
						// (char *) Tcl_PosixError(interp), TCL_VOLATILE);
					}

					// When we get an error we throw away all the output
					// currently queued.

					discardQueued();
					continue;
				} else {
					wroteSome = true;
				}

				buf.nextRemoved += written;

				// If this buffer is now empty, recycle it.

				if (buf.nextRemoved == buf.nextAdded) {
					outQueueHead = buf.next;
					if (outQueueHead == null) {
						outQueueTail = null;
					}
					recycleBuffer(buf, false);
				}
			} // Closes "while (1)".

			// If we wrote some data while flushing in the background, we are
			// done.
			// We can't finish the background flush until we run out of data and
			// the channel becomes writable again. This ensures that all of the
			// pending data has been flushed at the system level.

			if (bgFlushScheduled) {
				if (wroteSome) {
					return errorCode;
				} else if (outQueueHead == null) {
					bgFlushScheduled = false;
					// FIXME: What is this watchProc?
					// (chanPtr->typePtr->watchProc)(chanPtr->instanceData,
					// statePtr->interestMask);
				}
			}

			// If the channel is flagged as closed, delete it when the refCount
			// drops to zero, the output queue is empty and there is no output
			// in the current output buffer.

			if (closed
					&& (refCount <= 0)
					&& (outQueueHead == null)
					&& ((curOut == null) || (curOut.nextAdded == curOut.nextRemoved))) {
				return closeChannel(interp, errorCode);
			}
			return errorCode;

		} finally {
			output.flush();

			// In some implementations (Sun JDK 1.4 on Win32)
			// the flush method above does not actually sync
			// output data or files. Call sync when we know
			// the object is a file and not something else
			// like a socket stream.

			if (canSync) {
				FileOutputStream fos = (FileOutputStream) output;
				fos.getFD().sync();
			}
		}
	}

	void setEncoding(String inEncoding) {
		encoding = inEncoding;
	}

	void setEofChar(char inEofChar) {
		eofChar = inEofChar;
	}

	void setTranslation(int inTranslation) {
		translation = inTranslation;
	}

	void setBuffering(int inBuffering) {
		buffering = inBuffering;
	}

	void setBufferSize(int inBufSize) {
		bufSize = inBufSize;
		outputStage = null;
	}

	void setBlocking(boolean inBlocking) {
		blocking = inBlocking;
	}

	boolean isBlocked() {
		return blocked;
	}

	// Helper class to implement integer pass by reference.

	private class IntPtr {
		int i;

		IntPtr() {
		}

		IntPtr(int value) {
			i = value;
		}
	}

	/**
	 * RecycleBuffer -> recycleBuffer
	 * 
	 * Helper function to recycle output buffers. Ensures that that curOut is
	 * set to a buffer. Only if these conditions are met is the buffer released
	 * so that it can be garbage collected.
	 */

	private void recycleBuffer(ChannelBuffer buf, boolean mustDiscard) {

		if (mustDiscard)
			return;

		// Only save buffers which are at least as big as the requested
		// buffersize for the channel. This is to honor dynamic changes
		// of the buffersize made by the user.

		if ((buf.bufLength - ChannelBuffer.BUFFER_PADDING) < bufSize) {
			return;
		}

		if (curOut == null) {
			curOut = buf;
			buf.nextRemoved = ChannelBuffer.BUFFER_PADDING;
			buf.nextAdded = ChannelBuffer.BUFFER_PADDING;
			buf.next = null;
		}
	}

	/**
	 * DiscardOutputQueued -> discardQueued
	 * 
	 * Discards all output queued in the output queue of a channel.
	 */

	private void discardQueued() {
		ChannelBuffer buf;

		while (outQueueHead != null) {
			buf = outQueueHead;
			outQueueHead = buf.next;
			recycleBuffer(buf, false);
		}
		outQueueHead = null;
		outQueueTail = null;
	}

	/**
	 * UpdateInterest -> updateInterest
	 * 
	 * Arrange for the notifier to call us back at appropriate times based on
	 * the current state of the channel.
	 */

	void updateInterest() {
		// FIXME: Currently unimplemented
	}

	/**
	 * Tcl_OutputBuffered -> getNumBufferedBytes
	 * 
	 * Return the number of bytes that are current buffered.
	 */

	int getNumBufferedBytes() {
		ChannelBuffer buf;
		int IOQueued = 0;
		for (buf = outQueueHead; buf != null; buf = buf.next) {
			IOQueued += buf.nextAdded - buf.nextRemoved;
		}
		if ((curOut != null) && (curOut.nextAdded > curOut.nextRemoved)) {
			// bufferReady = true;
			IOQueued += curOut.nextAdded - curOut.nextRemoved;
		}
		return IOQueued;
	}

	/**
	 * seekCheckBuferReady
	 * 
	 * This method is used by the seek command to check the channel for buffered
	 * output and mark the buffer as ready to flush if found.
	 */

	void seekCheckBuferReady() {
		if ((curOut != null) && (curOut.nextAdded > curOut.nextRemoved)) {
			bufferReady = true;
		}
	}

	/**
	 * TranslateOutputEOL -> translateEOL
	 * 
	 * Helper function for writeBytes() and writeChars(). Converts the '\n'
	 * characters in the source buffer into the appropriate EOL form specified
	 * by the output translation mode.
	 * 
	 * EOL translation stops either when the source buffer is empty or the
	 * output buffer is full.
	 * 
	 * When converting to CRLF mode and there is only 1 byte left in the output
	 * buffer, this routine stores the '\r' in the last byte and then stores the
	 * '\n' in the byte just past the end of the buffer. The caller is
	 * responsible for passing in a buffer that is large enough to hold the
	 * extra byte.
	 * 
	 * Results:
	 * 
	 * The return value is 1 if a '\n' was translated from the source buffer, or
	 * 0 otherwise -- this can be used by the caller to decide to flush a
	 * line-based channel even though the channel buffer is not full.
	 * 
	 * dstLenPtr.i is filled with how many bytes of the output buffer were used.
	 * As mentioned above, this can be one more that the output buffer's
	 * specified length if a CRLF was stored.
	 * 
	 * srcLenPtr.i is filled with how many bytes of the source buffer were
	 * consumed.
	 * 
	 * It may be obvious, but bears mentioning that when converting in CRLF mode
	 * (which requires two bytes of storage in the output buffer), the number of
	 * bytes consumed from the source buffer will be less than the number of
	 * bytes stored in the output buffer.
	 * 
	 * @param dstArray
	 *            , Output buffer to fill with translated bytes or chars.
	 * @param dstStart
	 *            , First unused index in the dst output array.
	 * @param srcArray
	 *            , Input buffer that holds the bytes or chars to translate
	 * @param srcStart
	 *            , Index of first available byte in src array.
	 * 
	 * @param dstLenPtr
	 *            , On entry, the maximum length of output buffer in bytes or
	 *            chars. On exit, the number of bytes or chars actually used in
	 *            output buffer.
	 * @param srcLenPtr
	 *            , On entry, the length of source buffer. On exit, the number
	 *            of bytes or chars read from the source buffer.
	 */

	boolean translateEOL(Object dstArray, int dstStart, Object srcArray,
			int srcStart, IntPtr dstLenPtr, IntPtr srcLenPtr) {
		final boolean debug = false;

		// Figure out if the srcArray and dstArray buffers
		// are byte or char arrays.
		boolean isCharType;
		char[] srcArrayChar, dstArrayChar;
		byte[] srcArrayByte, dstArrayByte;

		if ((srcArray instanceof char[]) && (dstArray instanceof char[])) {
			isCharType = true;
			srcArrayChar = (char[]) srcArray;
			dstArrayChar = (char[]) dstArray;
			srcArrayByte = null;
			dstArrayByte = null;
		} else if ((srcArray instanceof byte[]) && (dstArray instanceof byte[])) {
			isCharType = false;
			srcArrayChar = null;
			dstArrayChar = null;
			srcArrayByte = (byte[]) srcArray;
			dstArrayByte = (byte[]) dstArray;
		} else {
			throw new TclRuntimeError("unknown array argument types");
		}

		int src, dst, dstEnd, srcLen;
		boolean newlineFound;

		src = srcStart;
		dst = dstStart;
		newlineFound = false;
		srcLen = srcLenPtr.i;

		switch (translation) {
		case TclIO.TRANS_LF: {
			if (isCharType) {
				for (dstEnd = dst + srcLen; dst < dstEnd;) {
					if (srcArrayChar[src] == '\n') {
						newlineFound = true;
					}
					dstArrayChar[dst++] = srcArrayChar[src++];
				}
			} else {
				for (dstEnd = dst + srcLen; dst < dstEnd;) {
					if (srcArrayByte[src] == '\n') {
						newlineFound = true;
					}
					dstArrayByte[dst++] = srcArrayByte[src++];
				}
			}
			dstLenPtr.i = srcLen;
			break;
		}
		case TclIO.TRANS_CR: {
			if (isCharType) {
				for (dstEnd = dst + srcLen; dst < dstEnd;) {
					if (srcArrayChar[src] == '\n') {
						dstArrayChar[dst++] = '\r';
						newlineFound = true;
						src++;
					} else {
						dstArrayChar[dst++] = srcArrayChar[src++];
					}
				}
			} else {
				for (dstEnd = dst + srcLen; dst < dstEnd;) {
					if (srcArrayByte[src] == '\n') {
						dstArrayByte[dst++] = (byte) '\r';
						newlineFound = true;
						src++;
					} else {
						dstArrayByte[dst++] = srcArrayByte[src++];
					}
				}
			}
			dstLenPtr.i = srcLen;
			break;
		}
		case TclIO.TRANS_CRLF: {
			// Since this causes the number of bytes to grow, we
			// start off trying to put 'srcLen' bytes into the
			// output buffer, but allow it to store more bytes, as
			// long as there's still source bytes and room in the
			// output buffer.

			int dstMax;
			// int dstStart, srcStart;

			// dstStart = dst;
			dstMax = dst + dstLenPtr.i;

			// srcStart = src;

			if (srcLen < dstLenPtr.i) {
				dstEnd = dst + srcLen;
			} else {
				dstEnd = dst + dstLenPtr.i;
			}

			if (isCharType) {
				while (dst < dstEnd) {
					if (srcArrayChar[src] == '\n') {
						if (dstEnd < dstMax) {
							dstEnd++;
						}
						dstArrayChar[dst++] = '\r';
						newlineFound = true;
					}
					dstArrayChar[dst++] = srcArrayChar[src++];
				}
			} else {
				while (dst < dstEnd) {
					if (srcArrayByte[src] == '\n') {
						if (dstEnd < dstMax) {
							dstEnd++;
						}
						dstArrayByte[dst++] = (byte) '\r';
						newlineFound = true;
					}
					dstArrayByte[dst++] = srcArrayByte[src++];
				}
			}

			srcLenPtr.i = src - srcStart;
			dstLenPtr.i = dst - dstStart;
			break;
		}
		default: {
			break;
		}
		}
		return newlineFound;
	}

	/**
	 * Tcl_UtfToExternal -> unicodeToExternal
	 * 
	 * Convert a source buffer from unicode characters to a specified encoding.
	 * 
	 * FIXME: Add doc for return values
	 * 
	 * @param src
	 *            , Source characters.
	 * @param srcOff
	 *            , First index in src input array.
	 * @param srcLen
	 *            , Number of characters in src buffer.
	 * @param dst
	 *            , Array to store encoded bytes in.
	 * @param dstOff
	 *            , First available index in dst array.
	 * @param dstLen
	 *            , Length of dst array.
	 * @param srcReadPtr
	 *            , Filled with the number of characters from the source string
	 *            that were converted. This may be less than the original source
	 *            length if there was a problem converting some source
	 *            characters.
	 * @param dstWrotePtr
	 *            , Filled with the number of bytes that were stored in the
	 *            output buffer as a result of the conversion
	 * @param dstCharsPtr
	 *            , Filled with the number of characters that correspond to the
	 *            bytes stored in the output buffer.
	 */

	int unicodeToExternal(char[] src, int srcOff, int srcLen, byte[] dst,
			int dstOff, int dstLen, IntPtr srcReadPtr, IntPtr dstWrotePtr,
			IntPtr dstCharsPtr) {
		final boolean debug = false;
		int result;

		if (encoding == null) {
			throw new TclRuntimeError(
					"unicodeToExternal called with null encoding");
		}

		if (debug) {
			System.out.println("unicodeToExternal(" + srcLen + " " + dstLen
					+ ")");
		}

		// If encoder was flushed already then return 0.

		if ((srcLen == 0) && !encodingEnd) {
			srcReadPtr.i = 0;
			if (dstWrotePtr != null)
				dstWrotePtr.i = 0;
			if (dstCharsPtr != null)
				dstCharsPtr.i = 0;
			return 0;
		}

		if (debug) {
			System.out.println("now to encode char array of length " + srcLen);
			System.out.println("srcOff is " + srcOff);
			for (int i = srcOff; i < (srcOff + srcLen); i++) {
				System.out.println("(char) '" + src[i] + "'");
			}
			System.out.println("encoded as " + encoding);
		}

		if (cse == null) {
			// Note that UnsupportedCharsetException should never be raised
			// here since EncodingCmd.isSupported() should have already
			// returned true for this encoding.

			Charset chrset = Charset.forName(encoding);
			cse = chrset.newEncoder();
			cse.onMalformedInput(CodingErrorAction.REPLACE);
			cse.onUnmappableCharacter(CodingErrorAction.REPLACE);
		}

		int chars_read, bytes_written;
		int bytes_flushed = 0;

		// A CharBuffer wraps the src char[] and
		// handles buffer size issues. A ByteBuffer
		// wraps the dst char[] and handles buffer
		// size issues.

		CharBuffer srcb = CharBuffer.wrap(src, srcOff, srcLen);
		ByteBuffer dstb = ByteBuffer.wrap(dst, dstOff, dstLen);

		int srcbStartPos = srcb.position();
		int dstbStartPos = dstb.position();

		// Pass atEOF flag as true when encodingEnd flag
		// was set to true in close().

		boolean atEOF = encodingEnd;

		CoderResult cresult = cse.encode(srcb, dstb, atEOF);

		chars_read = srcb.position() - srcbStartPos;
		bytes_written = dstb.position() - dstbStartPos;

		if (debug) {
			System.out.println("encoded " + bytes_written + " bytes from "
					+ chars_read + " chars (EOF flag was " + atEOF + ")");
			System.out.println(cse);
			
		}

		// For the case where an encoder needs to write bytes
		// at the end of the data, the close() method passes
		// an empty char[] and sets encodingEnd.

		if (atEOF) {
			if (chars_read != 0 && bytes_written != 0) {
				throw new TclRuntimeError(
						"Should have encoded no chars at EOF, " + chars_read
								+ " " + bytes_written);
			}

			cresult = cse.flush(dstb);

			bytes_flushed = dstb.position() - dstbStartPos;
			bytes_written += bytes_flushed;

			if (debug) {
				System.out
						.println("flushed " + bytes_flushed + " bytes at EOF");
			}

			encodingEnd = false;
		}

		if (!atEOF && (chars_read == 0) && (bytes_written == 0)) {
			throw new TclRuntimeError("No characters converted");
		}

		srcReadPtr.i = chars_read;
		if (dstWrotePtr != null)
			dstWrotePtr.i = bytes_written;
		if (dstCharsPtr != null)
			dstCharsPtr.i = chars_read;

		// FIXME: When do we return error codes?
		result = 0;

		return result;
	}

	/**
	 * WriteBytes -> writeBytes
	 * 
	 * Write a sequence of bytes into an output buffer, may queue the buffer for
	 * output if it gets full, and also remembers whether the current buffer is
	 * ready e.g. if it contains a newline and we are in line buffering mode.
	 * 
	 * The number of bytes written or -1 in case of error. If -1, Tcl_GetErrno
	 * will return the error code.
	 * 
	 * May buffer up output and may cause output to be produced on the channel.
	 * 
	 * @param src
	 *            Bytes to write.
	 * @param srfOff
	 *            First index in src array.
	 * @param srfLen
	 *            Number of bytes to write.
	 */

	int writeBytes(byte[] srcArray, int srcOff, int srcLen) throws IOException {
		ChannelBuffer buf;
		byte[] dstArray;
		int dst, src, dstMax, sawLF, total, savedLF;
		IntPtr dstLen = new IntPtr(), toWrite = new IntPtr();

		total = 0;
		sawLF = 0;
		savedLF = 0;
		src = srcOff;

		// Loop over all bytes in src, storing them in output buffer with
		// proper EOL translation.

		while (srcLen + savedLF > 0) {
			buf = curOut;
			if (buf == null) {
				buf = new ChannelBuffer(bufSize);
				curOut = buf;
			}
			// dst = bufPtr->buf + bufPtr->nextAdded;
			dstArray = buf.buf;
			dst = buf.nextAdded;
			dstMax = buf.bufLength - buf.nextAdded;
			dstLen.i = dstMax;

			toWrite.i = dstLen.i;
			if (toWrite.i > srcLen) {
				toWrite.i = srcLen;
			}

			if (savedLF != 0) {
				// A '\n' was left over from last call to translateEOL()
				// and we need to store it in this buffer. If the channel is
				// line-based, we will need to flush it.

				dstArray[dst++] = (byte) '\n';
				dstLen.i--;
				sawLF++;
			}
			if (translateEOL(dstArray, dst, srcArray, src, dstLen, toWrite)) {
				sawLF++;
			}
			dstLen.i += savedLF;
			savedLF = 0;

			if (dstLen.i > dstMax) {
				savedLF = 1;
				dstLen.i = dstMax;
			}
			buf.nextAdded += dstLen.i;
			if (checkFlush(buf, (sawLF != 0)) != 0) {
				return -1;
			}
			total += dstLen.i;
			src += toWrite.i;
			srcLen -= toWrite.i;
			sawLF = 0;
		}
		return total;
	}

	/**
	 * CheckFlush -> checkFlush
	 * 
	 * Helper function for writeBytes() and writeChars(). If the channel buffer
	 * is ready to be flushed, flush it.
	 * 
	 * The return value is -1 if there was a problem flushing the channel
	 * buffer, or 0 otherwise.
	 * 
	 * The buffer will be recycled if it is flushed.
	 * 
	 * @param buf
	 *            Channel buffer to possibly flush.
	 * @param newlineFlag
	 *            True if a the channel buffer contains a newline.
	 */

	int checkFlush(ChannelBuffer buf, boolean newlineFlag) throws IOException {
		// The current buffer is ready for output:
		// 1. if it is full.
		// 2. if it contains a newline and this channel is line-buffered.
		// 3. if it contains any output and this channel is unbuffered.

		if (!bufferReady) {
			if (buf.nextAdded == buf.bufLength) {
				bufferReady = true;
			} else if (buffering == TclIO.BUFF_LINE) {
				if (newlineFlag) {
					bufferReady = true;
				}
			} else if (buffering == TclIO.BUFF_NONE) {
				bufferReady = true;
			}
		}
		if (bufferReady) {
			if (flushChannel(null, false) != 0) {
				return -1;
			}
		}
		return 0;
	}

	/**
	 * WriteChars -> writeChars
	 * 
	 * Convert chars to the channel's external encoding and write the produced
	 * bytes into an output buffer, may queue the buffer for output if it gets
	 * full, and also remembers whether the current buffer is ready e.g. if it
	 * contains a newline and we are in line buffering mode.
	 * 
	 * The number of bytes written or -1 in case of error. If -1, Tcl_GetErrno
	 * will return the error code.
	 * 
	 * May buffer up output and may cause output to be produced on the channel.
	 * 
	 * @param src
	 *            Chars to write.
	 * @param srfOff
	 *            First index in src array.
	 * @param srfLen
	 *            Number of chars to write.
	 */

	int writeChars(char[] srcArray, int srcOff, int srcLen) throws IOException {
		final boolean debug = false;
		if (debug) {
			System.out.println("writeChars(" + srcLen + ")");
		}

		// ChannelState *statePtr = chanPtr->state; // state info for channel
		ChannelBuffer buf;
		char[] stageArray;
		byte[] dstArray;
		int stage, src, dst;
		int saved, savedLF, sawLF, total, dstLen, stageMax;
		int endEncoding, result;
		boolean consumedSomething;
		// Tcl_Encoding encoding;
		byte[] safe = new byte[ChannelBuffer.BUFFER_PADDING];
		IntPtr stageLen = new IntPtr(), toWrite = new IntPtr();
		IntPtr stageRead = new IntPtr(), dstWrote = new IntPtr();

		total = 0;
		sawLF = 0;
		savedLF = 0;
		saved = 0;
		// encoding = statePtr->encoding;
		src = 0;

		// Write the terminated escape sequence even if srcLen is 0.

		endEncoding = (encodingEnd ? 1 : 0);

		// Loop over all characters in src, storing them in staging buffer
		// with proper EOL translation.

		consumedSomething = true;
		while (consumedSomething && (srcLen + savedLF + endEncoding > 0)) {
			consumedSomething = false;
			if (outputStage == null) {
				outputStage = new char[bufSize + 2];
			}
			stageArray = outputStage;
			stage = 0;
			stageMax = bufSize;
			stageLen.i = stageMax;

			toWrite.i = stageLen.i;
			if (toWrite.i > srcLen) {
				toWrite.i = srcLen;
			}

			if (savedLF != 0) {
				// A '\n' was left over from last call to TranslateOutputEOL()
				// and we need to store it in the staging buffer. If the
				// channel is line-based, we will need to flush the output
				// buffer (after translating the staging buffer).

				stageArray[stage++] = '\n';
				stageLen.i--;
				sawLF++;
			}
			if (translateEOL(stageArray, stage, srcArray, src, stageLen,
					toWrite)) {
				sawLF++;
			}

			stage -= savedLF;
			stageLen.i += savedLF;
			savedLF = 0;

			if (stageLen.i > stageMax) {
				savedLF = 1;
				stageLen.i = stageMax;
			}
			src += toWrite.i;
			srcLen -= toWrite.i;

			if (debug) {
				System.out.println("moved " + stageLen.i
						+ " chars to stageArray");
			}

			// Loop over all characters in staging buffer, converting them
			// to external encoding, storing them in output buffer.

			while (stageLen.i + saved + endEncoding > 0) {
				buf = curOut;
				if (buf == null) {
					buf = new ChannelBuffer(bufSize);
					curOut = buf;
				}
				// dst = buf.buf + buf.nextAdded;
				dstArray = buf.buf;
				dst = buf.nextAdded;
				dstLen = buf.bufLength - buf.nextAdded;

				if (saved != 0) {
					// Here's some translated bytes left over from the last
					// buffer that we need to stick at the beginning of this
					// buffer.

					System.arraycopy(safe, 0, dstArray, dst, saved);
					buf.nextAdded += saved;
					dst += saved;
					dstLen -= saved;
					saved = 0;
				}

				if (debug) {
					System.out.println("invoking unicodeToExternal("
							+ stageLen.i + ")");
				}

				result = unicodeToExternal(stageArray, stage, stageLen.i,
						dstArray, dst, dstLen + ChannelBuffer.BUFFER_PADDING,
						stageRead, dstWrote, null);

				// Fix for SF #506297, reported by Martin Forssen
				// <ruric@users.sourceforge.net>.
				//
				// The encoding chosen in the script exposing the bug writes out
				// three intro characters when TCL_ENCODING_START is set, but
				// does
				// not consume any input as TCL_ENCODING_END is cleared. As some
				// output was generated the enclosing loop calls UtfToExternal
				// again, again with START set. Three more characters in the out
				// and still no use of input ... To break this infinite loop we
				// remove TCL_ENCODING_START from the set of flags after the
				// first
				// call (no condition is required, the later calls remove an
				// unset
				// flag, which is a no-op). This causes the subsequent calls to
				// UtfToExternal to consume and convert the actual input.

				encodingStart = false;

				// The following can never happen since we use unicode
				// characters.
				//
				// if ((result != 0) && ((stageRead.i + dstWrote.i) == 0)) {
				// // We have an incomplete UTF-8 character at the end of the
				// // staging buffer. It will get moved to the beginning of the
				// // staging buffer followed by more bytes from src.
				//
				// src -= stageLen.i;
				// srcLen += stageLen.i;
				// stageLen.i = 0;
				// savedLF = 0;
				// break;
				// }
				buf.nextAdded += dstWrote.i;
				if (buf.nextAdded > buf.bufLength) {
					// When translating from unicode to external encoding, we
					// allowed the translation to produce a character that
					// crossed the end of the output buffer, so that we would
					// get a completely full buffer before flushing it. The
					// extra bytes will be moved to the beginning of the next
					// buffer.

					saved = buf.nextAdded - buf.bufLength;
					System.arraycopy(dstArray, dst + dstLen, safe, 0, saved);
					buf.nextAdded = buf.bufLength;
				}
				if (checkFlush(buf, (sawLF != 0)) != 0) {
					return -1;
				}

				total += dstWrote.i;
				stage += stageRead.i;
				stageLen.i -= stageRead.i;
				sawLF = 0;

				consumedSomething = true;

				// If all translated characters are written to the buffer,
				// endEncoding is set to 0 because the escape sequence may be
				// output.

				if ((stageLen.i + saved == 0) && (result == 0)) {
					endEncoding = 0;
				}
			}
		}

		// If nothing was written and it happened because there was no progress
		// in the UTF conversion, we throw an error.

		if (!consumedSomething && (total == 0)) {
			// Tcl_SetErrno (EINVAL);
			return -1;
		}
		return total;
	}

	/**
	 * DoWriteChars -> doWriteChars
	 * 
	 * Takes a sequence of characters and converts them for output using the
	 * channel's current encoding, may queue the buffer for output if it gets
	 * full, and also remembers whether the current buffer is ready e.g. if it
	 * contains a newline and we are in line buffering mode. Compensates
	 * stacking, i.e. will redirect the data from the specified channel to the
	 * topmost channel in a stack.
	 * 
	 * The number of bytes written or -1 in case of error. If -1, Tcl_GetErrno
	 * will return the error code.
	 * 
	 * May buffer up output and may cause output to be produced on the channel.
	 * 
	 * @param src
	 *            Chars to write.
	 * @param srfOff
	 *            First index in src array.
	 * @param srfLen
	 *            Number of chars to write.
	 */

	int doWriteChars(char[] src, int srcOff, int srcLen) {
		return -1;
	}

	/**
	 * Tcl_WriteObj -> writeObj
	 * 
	 * Takes the Tcl object and queues its contents for output. If the encoding
	 * of the channel is NULL, takes the byte-array representation of the object
	 * and queues those bytes for output. Otherwise, takes the characters in the
	 * UTF-8 (string) representation of the object and converts them for output
	 * using the channel's current encoding. May flush internal buffers to
	 * output if one becomes full or is ready for some other reason, e.g. if it
	 * contains a newline and the channel is in line buffering mode.
	 * 
	 * The number of bytes written or -1 in case of error. If -1, Tcl_GetErrno
	 * will return the error code.
	 * 
	 * May buffer up output and may cause output to be produced on the channel.
	 * 
	 * @param obj
	 *            The object to write.
	 */

	int writeObj(TclObject obj) throws IOException {
		// Always use the topmost channel of the stack

		// char *src;
		int srcLen;

		// statePtr = ((Channel *) chan)->state;
		// chanPtr = statePtr->topChanPtr;

		// if (CheckChannelErrors(statePtr, TCL_WRITABLE) != 0) {
		// return -1;
		// }

		if (encoding == null) {
			srcLen = TclByteArray.getLength(null, obj);
			byte[] bytes = TclByteArray.getBytes(null, obj);
			return writeBytes(bytes, 0, srcLen);
		} else {
			String data = obj.toString();
			final int num_chars = data.length();
			// FIXME: Use outputStage here?
			char[] chars = new char[num_chars];
			data.getChars(0, num_chars, chars, 0);
			return writeChars(chars, 0, num_chars);
		}
	}

}
