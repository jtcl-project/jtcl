/*
 * ------------------------------------------------------------------------
 *      PACKAGE:  [incr Tcl]
 *  DESCRIPTION:  Object-Oriented Extensions to Tcl
 *
 *  This file contains procedures that belong in the Tcl/Tk core.
 *  Hopefully, they'll migrate there soon.
 *
 * ========================================================================
 *  AUTHOR:  Michael J. McLennan
 *           Bell Labs Innovations for Lucent Technologies
 *           mmclennan@lucent.com
 *           http://www.tcltk.com/itcl
 *
 *     RCS:  $Id: Migrate.java,v 1.1 2005/09/11 20:56:57 mdejong Exp $
 * ========================================================================
 *           Copyright (c) 1993-1998  Lucent Technologies, Inc.
 * ------------------------------------------------------------------------
 * See the file "license.itcl" for information on usage and redistribution
 * of this file, and for a DISCLAIMER OF ALL WARRANTIES.
 */

package tcl.pkg.itcl;

import tcl.lang.CallFrame;
import tcl.lang.Interp;
import tcl.lang.Var;

class Migrate {

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * _Tcl_GetCallFrame -> Migrate.GetCallFrame
	 * 
	 * Checks the call stack and returns the call frame some number of levels
	 * up. It is often useful to know the invocation context for a command.
	 * 
	 * Results: Returns a token for the call frame 0 or more levels up in the
	 * call stack.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static CallFrame GetCallFrame(Interp interp, // interpreter being queried
			int level) // number of levels up in the call stack (>= 0)
	{
		if (level < 0) {
			Util.Assert(false,
					"Migrate.GetCallFrame called with bad number of levels");
		}

		return ItclAccess.getCallFrame(interp, level);
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * _Tcl_ActivateCallFrame -> Migrate.ActivateCallFrame
	 * 
	 * Makes an existing call frame the current frame on the call stack. Usually
	 * called in conjunction with GetCallFrame to simulate the effect of an
	 * "uplevel" command.
	 * 
	 * Note that this procedure is different from Tcl_PushCallFrame, which adds
	 * a new call frame to the call stack. This procedure assumes that the call
	 * frame is already initialized, and it merely activates it on the call
	 * stack.
	 * 
	 * Results: Returns a token for the call frame that was in effect before
	 * activating the new context. That call frame can be restored by calling
	 * _Tcl_ActivateCallFrame again.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static CallFrame ActivateCallFrame(Interp interp, // interpreter being
			// queried
			CallFrame frame) // call frame to be activated
	{
		return ItclAccess.activateCallFrame(interp, frame);
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * _TclNewVar -> Migrate.NewVar
	 * 
	 * Create a new variable that will eventually be entered into a hashtable.
	 * 
	 * Results: The return value is a reference to the new variable structure.
	 * It is marked as a scalar variable (and not a link or array variable). Its
	 * value initially is null. The variable is not part of any hash table yet.
	 * Since it will be in a hashtable and not in a call frame, its name field
	 * is set null. It is initially marked as undefined.
	 * 
	 * Side effects: Storage gets allocated.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static Var NewVar() {
		return ItclAccess.newVar();
	}

} // end class Migrate

