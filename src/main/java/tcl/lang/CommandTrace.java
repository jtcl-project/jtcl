package tcl.lang;

/**
 * This class is used to trace command rename and deletion
 * 
 */
public class CommandTrace {
	/**
	 * Indicates a CommandTrace that will execute on a command deletion
	 */
	public static final int DELETE = 0;
	/**
	 * Indicates a CommandTrace that will execute on a command rename
	 */
	public static final int RENAME = 1;
	/**
	 * Command to call when trace is fired
	 */
	protected String callbackCmd;
	/**
	 * The type of trace, either DELETE or RENAME
	 */
	protected int type;
	/**
	 * Interpreter in which to eval the callback
	 */
	Interp interp;

	/**
	 * Create a new CommandTrace
	 * 
	 * @param type
	 *            either DELETE or RENAME
	 * @param callbackCmd
	 *            command to execute when fired
	 */
	public CommandTrace(Interp interp, int type, TclObject callbackCmd) {
		this.type = type;
		this.callbackCmd = callbackCmd.toString();
		this.interp = interp;
	}

	/**
	 * @return the type of this trace, either RENAME or DELETE
	 */
	public int getType() {
		return type;
	}

	/**
	 * @return the callback command for this trace
	 */
	public String getCallbackCmd() {
		return callbackCmd;
	}

	/**
	 * Call the callback function for this CommandTrace if it matches type. Any
	 * errors in the callback function are ignored.
	 * 
	 * @param type
	 *            type of trace being executed (DELETE or RENAME)
	 * @param oldname
	 *            old name of command being renamed or deleted
	 * @param newname
	 *            new name of command being renamed; ignored for DELETE type
	 */
	public void trace(int type, String oldname, String newname) {
		try {
			if (type == this.type) {
				String callback = callbackCmd + " " + oldname + " "
						+ ((type == DELETE) ? "{} delete" : (newname + " rename"));
				interp.eval(callback, 0);
			}
		} catch (TclException e) {
			// ignore any script errors or Tcl exceptions
		}
	}
}
