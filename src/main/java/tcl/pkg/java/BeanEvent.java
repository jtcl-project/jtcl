/*
 * BeanEvent.java --
 *
 *	Handles JavaBean events in Tcl.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: BeanEvent.java,v 1.4 2005/11/16 21:08:11 mdejong Exp $
 *
 */

package tcl.pkg.java;

import tcl.lang.Interp;
import tcl.lang.TCL;
import tcl.lang.TclEvent;
import tcl.lang.TclException;
import tcl.lang.TclList;
import tcl.lang.TclObject;
import tcl.lang.TclRuntimeError;

/**
 * This class handles JavaBean events in Tcl.
 */

class BeanEvent extends TclEvent {

	// The interpreter to execute the callback command.

	Interp interp;

	// The callback command to execute when the event is fired.

	String command;

	// Types of the event parameters.

	Class paramTypes[];

	// The parameters for the event.

	Object params[];

	// If an Exception is throws during the execution of the callback
	// script, it is stored in this member variable.

	Throwable exception;

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * BeanEvent --
	 * 
	 * Creates a new BeanEvent instance.
	 * 
	 * side effects: Member fields are initialized.
	 * 
	 * ----------------------------------------------------------------------
	 */

	BeanEvent(Interp i, // Interpreter to execute the callback command.
			Class t[], // Types of the event parameters.
			Object p[], // Parameters for this event.
			TclObject cmd) // The callback command.
	{
		interp = i;
		command = cmd.toString();
		paramTypes = t;
		params = p;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * processEvent --
	 * 
	 * Process the bean event.
	 * 
	 * Results: Always returns 1 -- the event has been processed and can be
	 * removed from the event queue.
	 * 
	 * Side effects: A script is eval'ed to handle the event. The script may may
	 * have arbitrary side effects.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public int processEvent(int flags) // Same as flags passed to
	// InterpGroup.doOneEvent.
	{
		BeanEventMgr mgr = BeanEventMgr.getBeanEventMgr(interp);

		try {
			exception = null;
			mgr.pushEventParamSet(new BeanEventParamSet(paramTypes, params));
			interp.eval(command, 0);
		} catch (TclException e) {
			int code = e.getCompletionCode();

			if (code == TCL.ERROR) {
				exception = e;

				// If the exception is a ReflectException, we throw the
				// actual Java exception, which is stored in the
				// errorCode.

				try {
					TclObject errCode = interp.getVar("errorCode", null,
							TCL.GLOBAL_ONLY);

					if (errCode != null) {
						TclObject elm1 = TclList.index(interp, errCode, 0);
						TclObject elm2 = TclList.index(interp, errCode, 1);

						if ((elm1 != null) && (elm1.toString().equals("JAVA"))) {
							if (elm2 != null) {
								Object obj = null;
								TclObject oldResult = interp.getResult();
								oldResult.preserve();

								try {
									obj = ReflectObject.get(interp, elm2);
								} catch (TclException e3) {
									// The second element in errorCode
									// doesn't contain a Java object
									// handle. Let's restore the interp's
									// result to the old result.

									interp.setResult(oldResult);
								} finally {
									oldResult.release();
								}

								if ((obj != null) && (obj instanceof Throwable)) {
									exception = (Throwable) obj;
									exception.fillInStackTrace();
								}
							}
						}
					}
				} catch (TclException e2) {
					throw new TclRuntimeError("unexpected TclException " + e2);
				}
			} else if (code == TCL.RETURN) {
				// The script invoked the "return" command. We treat this
				// as a normal completion -- even if the command
				// was "return -code error".

			} else if (code == TCL.BREAK) {
				exception = new TclException(interp,
						"invoked \"break\" outside of a loop");
			} else if (code == TCL.CONTINUE) {
				exception = new TclException(interp,
						"invoked \"continue\" outside of a loop");
			} else {
				exception = e;
			}
		} finally {
			mgr.popEventParamSet();
		}

		return 1;
	}

} // end BeanEvent

