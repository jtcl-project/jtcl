package tcl.lang.channel;

import java.io.IOException;

import tcl.lang.IdleHandler;
import tcl.lang.Interp;
import tcl.lang.TCL;
import tcl.lang.TclException;

/**
 * This class is the handles the transient event that executes a 'fileevent'
 * script exactly once. It schedules a duplicate of itself for the next
 * fileevent script execution. It is closely tied with FileEventScript.
 * 
 * @author Dan Bodoh
 * 
 */
public class FileEvent extends IdleHandler {
	/**
	 * Indicates a READ file event
	 */
	public final static int READABLE = 0;
	/**
	 * Indicates a WRITE file event
	 */
	public final static int WRITABLE = 1;

	/**
	 * The interpreter in which this FileEvent is registered
	 */
	Interp interp;
	/**
	 * The type of this FileEvent, either READABLE or WRITEABLE
	 */
	int type;

	/**
	 * The Channel associated with this FileEvent
	 */
	Channel channel;

	/**
	 * Create a new FileEvent
	 * 
	 * @param interp
	 *            interpreter in which to create the file event
	 * @param channel
	 *            Channel on which to create the file event
	 * @param type
	 *            either READABLE or WRITEABLE
	 */
	private FileEvent(Interp interp, Channel channel, int type) {
		super();
		this.interp = interp;
		this.channel = channel;
		this.type = type;
		super.register(interp.getNotifier());
	}

	/**
	 * Create a new FileEvent and add it to the TclEvent queue. It is internally
	 * implemented as an IdleHandler, but could also be implemented as a regular
	 * TclEvent
	 * 
	 * @param interp
	 *            interpreter in which to create the file event
	 * @param channel
	 *            Channel on which to create the file event
	 * @param type
	 *            either READABLE or WRITABLE
	 */
	public static void queueFileEvent(Interp interp, Channel channel, int type) {
		new FileEvent(interp, channel, type);
	}

	/**
	 * Put a duplicate FileEvent onto the queue
	 */
	private void requeue() {
		queueFileEvent(this.interp, this.channel, this.type);
	}

	/**
	 * Permanently remove the FileEventScript for this FileEvent from the
	 * interpreter
	 */
	void dispose() {
		FileEventScript.dispose(interp, channel, type);
	}

	@Override
	public void processIdleEvent() {

		FileEventScript script = FileEventScript.find(interp, channel, type);
		if (script == null) {
			return; // event was disposed
		}
		if (type == READABLE && !channel.isReadable()) {
			try {
				channel.fillInputBuffer();
				requeue();
			} catch (IOException e) {
				new TclException(interp, e.getMessage());
				interp.backgroundError();
				dispose();
			}
			return;
		}
		if (type == WRITABLE && !channel.isWritable()) {
			requeue();
			return;
		}

		/*
		 * Run the user's fileevent script
		 */
		try {
			interp.eval(script.getScript(), TCL.GLOBAL_ONLY);
		} catch (TclException e) {
			interp.backgroundError();
			dispose();
			return;
		}

		/*
		 * Put a duplicate FileEvent on the queue
		 */
		requeue();
		return;
	}

}
