/*
 * SocketConnectionEvent
 *
 * A subclass of TclEvent used to indicate that a connection
 * has been made to a server socket.
 */
package tcl.lang.channel;

import tcl.lang.Interp;
import tcl.lang.TCL;
import tcl.lang.TclEvent;
import tcl.lang.TclObject;
import tcl.lang.TclString;

public class SocketConnectionEvent extends TclEvent {

	Interp cbInterp;
	TclObject callbackCmd;

	public SocketConnectionEvent(Interp i, TclObject cb, String chan,
			String ip, int port) {
		cbInterp = i;
		callbackCmd = TclString.newInstance(cb.toString());
		TclString.append(callbackCmd, " " + chan + " " + ip + " " + port);
	}

	public int processEvent(int flags) {
		// Check this event is for us.
		if ((flags == 0) || ((flags & TCL.FILE_EVENTS) == TCL.FILE_EVENTS)
				|| ((flags & TCL.ALL_EVENTS) == TCL.ALL_EVENTS)) {
			// Process the event
			try {
				cbInterp.eval(callbackCmd, TCL.EVAL_GLOBAL);
			} catch (Exception e) {
				// What do I do with this??
				e.printStackTrace();
				// Possibly the interpreter doesn't exist anymore??
			}
			return 1;
		} else {
			System.out.println("Event type: " + flags);
			// Event not for us
			return 0;
		}
	}

}
