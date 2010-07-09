// Port of the ChannelBuffer struct from tclIO.h/tclIO.c
// and associated functionality

package tcl.lang.channel;

class ChannelBuffer {

	/** The next position into which a character
	 * will be put in the buffer.
	 */
	int nextAdded;

	/** Position of next byte to be removed
	 * from the buffer.
	 */
	int nextRemoved;

	/**
	 * How big is the buffer?
	 */

	int bufLength;

	/**
	 * Next buffer in chain.
	 */

	ChannelBuffer next;

	/**
	 * The actual bytes stored in the buffer
	 */

	byte[] buf;

	/** A channel buffer has BUFFER_PADDING bytes extra at beginning to
	 * hold any bytes of a native-encoding character that got split by
	 * the end of the previous buffer and need to be moved to the
	 * beginning of the next buffer to make a contiguous string so it
	 * can be converted to UTF-8.
	 *
	 * A channel buffer has BUFFER_PADDING bytes extra at the end to
	 * hold any bytes of a native-encoding character (generated from a
	 * UTF-8 character) that overflow past the end of the buffer and
	 * need to be moved to the next buffer.
	 */
	final static int BUFFER_PADDING = 16;

	/**
	 * AllocChannelBuffer -> ChannelBuffer
	 * 
	 * Create a new ChannelBuffer object
	 */

	ChannelBuffer(int length) {
		int n;

		n = length + BUFFER_PADDING + BUFFER_PADDING;
		buf = new byte[n];
		nextAdded = BUFFER_PADDING;
		nextRemoved = BUFFER_PADDING;
		bufLength = length + BUFFER_PADDING;
		next = null;
	}

	// Generate debug output that describes the contents
	// of this ChannelBuffer object.

	public String toString() {
		int numBytes = nextAdded - nextRemoved;

		StringBuffer sb = new StringBuffer(256);
		sb.append("ChannelBuffer contains " + numBytes + " bytes" + "\n");

		for (int i = 0; i < numBytes; i++) {
			int ival = buf[nextRemoved + i];
			String srep;
			if (((char) ival) == '\r') {
				srep = "\\r";
			} else if (((char) ival) == '\n') {
				srep = "\\n";
			} else {
				srep = "" + ((char) ival);
			}

			sb.append("bytes[" + i + "] = '" + srep + "'" + ", (int) " + ival
					+ " , " + "0x" + Integer.toHexString(ival) + "\n");
		}
		return sb.toString();
	}
}
