/*
 * ------------------------------------------------------------------------
 *      PACKAGE:  [incr Tcl]
 *  DESCRIPTION:  Object-Oriented Extensions to Tcl
 *
 *  [incr Tcl] provides object-oriented extensions to Tcl, much as
 *  C++ provides object-oriented extensions to C.  It provides a means
 *  of encapsulating related procedures together with their shared data
 *  in a local namespace that is hidden from the outside world.  It
 *  promotes code re-use through inheritance.  More than anything else,
 *  it encourages better organization of Tcl applications through the
 *  object-oriented paradigm, leading to code that is easier to
 *  understand and maintain.
 *
 *  This part adds a mechanism for integrating C procedures into
 *  [incr Tcl] classes as methods and procs.  Each C procedure must
 *  either be declared via Itcl_RegisterC() or dynamically loaded.
 *
 * ========================================================================
 *  AUTHOR:  Michael J. McLennan
 *           Bell Labs Innovations for Lucent Technologies
 *           mmclennan@lucent.com
 *           http://www.tcltk.com/itcl
 *
 *     RCS:  $Id: Linkage.java,v 1.1 2005/09/11 20:56:57 mdejong Exp $
 * ========================================================================
 *           Copyright (c) 1993-1998  Lucent Technologies, Inc.
 * ------------------------------------------------------------------------
 * See the file "license.itcl" for information on usage and redistribution
 * of this file, and for a DISCLAIMER OF ALL WARRANTIES.
 */

package tcl.pkg.itcl;

import java.util.Enumeration;
import java.util.Hashtable;

import tcl.lang.AssocData;
import tcl.lang.Command;
import tcl.lang.CommandWithDispose;
import tcl.lang.Interp;
import tcl.lang.TclException;

//  These records store the refs for the RegisterObjC function.

class ItclJavafunc {
	Command objCmdProc; // Java objv command handler
}

// This record is stored in the interp assoc data

class AssocHashtable extends Hashtable implements AssocData {

	public void disposeAssocData(Interp interp) {
		Linkage.FreeC(this, interp);
	}
}

class Linkage {

	// Note: Itcl_RegisterC not supported

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_RegisterObjC -> Linkage.RegisterObjC
	 * 
	 * Used to associate a symbolic name with a Java procedure that handles a
	 * Tcl command. Procedures that are registered in this manner can be
	 * referenced in the body of an [incr Tcl] class definition to specify Java
	 * procedures to acting as methods/procs. Usually invoked in an
	 * initialization routine for an extension, called out in Tcl_AppInit() at
	 * the start of an application.
	 * 
	 * Each symbolic procedure can have an arbitrary client data value
	 * associated with it. This value is passed into the command handler
	 * whenever it is invoked.
	 * 
	 * Will raise a TclException to indicate failure.
	 * ------------------------------------------------------------------------
	 */

	static void RegisterObjC(Interp interp, // interpreter handling this
			// registration
			String name, // symbolic name for procedure
			Command proc) // procedure handling Tcl command
			throws TclException {
		// Maps String to ItclJavafunc
		Hashtable procTable;
		ItclJavafunc jfunc;

		// Make sure that a proc was specified.

		if (proc == null) {
			throw new TclException(interp,
					"initialization error: null pointer for "
							+ "Java procedure \"" + name + "\"");
		}

		// Add a new entry for the given procedure. If an entry with
		// this name already exists, then make sure that it was defined
		// with the same proc.

		procTable = Linkage.GetRegisteredProcs(interp);

		jfunc = (ItclJavafunc) procTable.get(name);

		if (jfunc != null) {
			if (jfunc.objCmdProc != null && jfunc.objCmdProc != proc) {
				throw new TclException(interp,
						"initialization error: Java procedure "
								+ "with name \"" + name + "\" already defined");
			}
			if (jfunc.objCmdProc instanceof CommandWithDispose) {
				((CommandWithDispose) jfunc.objCmdProc).disposeCmd();
			}
		} else {
			jfunc = new ItclJavafunc();
		}

		jfunc.objCmdProc = proc;
		procTable.put(name, jfunc);
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_FindC -> Linkage.FindC
	 * 
	 * Used to query a Java procedure via its symbolic name. Looks at the list
	 * of procedures registered previously by RegisterObjC and returns the
	 * ItclJavafunc record if found; returns null otherwise.
	 * ------------------------------------------------------------------------
	 */

	static ItclJavafunc FindC(Interp interp, // interpreter handling this
			// registration
			String name) // symbolic name for procedure
	{
		boolean found = false;
		Hashtable procTable;
		ItclJavafunc jfunc = null;

		if (interp != null) {
			procTable = (Hashtable) interp.getAssocData("itcl_RegC");

			if (procTable != null) {
				jfunc = (ItclJavafunc) procTable.get(name);
				if (jfunc != null) {
					found = true;
				}
			}
		}

		if (!found) {
			return null;
		} else {
			return jfunc;
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * ItclGetRegisteredProcs -> Linkage.GetRegisteredProcs
	 * 
	 * Returns a pointer to a hash table containing the list of registered procs
	 * in the specified interpreter. If the hash table does not already exist,
	 * it is created.
	 * ------------------------------------------------------------------------
	 */

	static Hashtable GetRegisteredProcs(Interp interp) // interpreter handling
	// this registration
	{
		AssocHashtable procTable;

		// If the registration table does not yet exist, then create it.
		procTable = (AssocHashtable) interp.getAssocData("itcl_RegC");

		if (procTable == null) {
			procTable = new AssocHashtable();
			interp.setAssocData("itcl_RegC", procTable);
		}

		return procTable;
	}

	/*
	 * ------------------------------------------------------------------------
	 * ItclFreeC -> FreeC
	 * 
	 * When an interpreter is deleted, this procedure is called to free up the
	 * associated data created by RegisterObjC.
	 * ------------------------------------------------------------------------
	 */

	static void FreeC(Hashtable table, // associated data
			Interp interp) // intepreter being deleted
	{
		for (Enumeration e = table.keys(); e.hasMoreElements();) {
			String key = (String) e.nextElement();
			ItclJavafunc jfunc = (ItclJavafunc) table.get(key);

			if (jfunc.objCmdProc instanceof CommandWithDispose) {
				((CommandWithDispose) jfunc.objCmdProc).disposeCmd();
			}
		}
		table.clear();
	}

} // end class Linkage

