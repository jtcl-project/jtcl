/*
 * WrappedCommand.java
 *
 *	Wrapper for commands located inside a Jacl interp.
 *
 * Copyright (c) 1999 Mo DeJong.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: WrappedCommand.java,v 1.6 2006/01/26 19:49:18 mdejong Exp $
 */

package tcl.lang;

import java.util.HashMap;

/**
 * A Wrapped Command is like the Command struct defined in the C version in the
 * file generic/tclInt.h. It is "wrapped" around a TclJava Command interface
 * reference. We need to wrap Command references so that we can keep track of
 * sticky issues like what namespace the command is defined in without requiring
 * that every implementation of a Command interface provide method to do this.
 * This class is only used in the internal implementation of Jacl.
 */

public class WrappedCommand {
	/**
	 *  Reference to the table that this command is defined inside. The
	 * hashKey member can be // used to lookup this WrappedCommand instance //
	 * in the table of WrappedCommands. The table  member combined with the
	 * hashKey member are  are equivilent to the C version's Command->hPtr.
	 */
	public HashMap table; 
	/**
	 * A string that stores the name of the command.  This name is NOT fully
	 * qualified.
	 */
	public String hashKey; 

	/**
	 *  The namespace where the command is located
	 */
	public Namespace ns;
	/**
	 * The actual command interface that is being wrapped
	 */
	public Command cmd;
	/**
	 * Means that the command is in the process  of being deleted. Other
	 * attempts to  delete the command should be ignored.
	 */
	public boolean deleted; // 

	/**
	 * List of each imported Command created in another namespace when this
	 * command is  imported. These imported commands  redirect invocations
	 * back to this  command. The list is used to remove all  those imported
	 * commands when deleting  this "real" command.
	 */
	ImportRef importRef;

	/**
	 * incremented to invalidate any references
	 */
	public int cmdEpoch; 

	// that point to this command when it is
	// renamed, deleted, hidden, or exposed.
	// This field always have a value in the
	// range 1 to Integer.MAX_VALUE (inclusive).
	// User code should NEVER modify this value.

	// Increment the cmdProch field. This method is used by the interpreter
	// to indicate that a command was hidden, renamed, or deleted.

	void incrEpoch() {
		cmdEpoch++;
		if (cmdEpoch == Integer.MIN_VALUE) {
			// Integer overflow, really unlikely but possible.
			cmdEpoch = 1;
		}
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();

		sb.append("Wrapper for ");
		if (ns != null) {
			sb.append(ns.fullName);
			if (!ns.fullName.equals("::")) {
				sb.append("::");
			}
		}
		if (table != null) {
			sb.append(hashKey);
		}

		sb.append(" -> ");
		sb.append(cmd.getClass().getName());

		sb.append(" cmdEpoch is ");
		sb.append(cmdEpoch);

		return sb.toString();
	}
}
