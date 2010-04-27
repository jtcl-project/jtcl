/*
 * ------------------------------------------------------------------------
 *      PACKAGE:  [incr Tcl]
 *  DESCRIPTION:  Object-Oriented Extensions to Tcl
 *
 *  This file is part of Itcl, it provides access to Jacl fields
 *  and methods that would otherwise only be available to classes
 *  in the tcl.lang package. It also provides some utility
 *  methods that needs access to Jacl internals.
 *  
 * ========================================================================
 *  AUTHOR:  Michael J. McLennan
 *           Bell Labs Innovations for Lucent Technologies
 *           mmclennan@lucent.com
 *           http://www.tcltk.com/itcl
 *
 *     RCS:  $Id: ItclAccess.java,v 1.3 2006/01/26 19:49:18 mdejong Exp $
 * ========================================================================
 *           Copyright (c) 1993-1998  Lucent Technologies, Inc.
 * ------------------------------------------------------------------------
 * See the file "license.itcl" for information on usage and redistribution
 * of this file, and for a DISCLAIMER OF ALL WARRANTIES.
 */

package tcl.pkg.itcl;

import java.util.HashMap;

import tcl.lang.CallFrame;
import tcl.lang.Interp;
import tcl.lang.Namespace;
import tcl.lang.Procedure;
import tcl.lang.TclException;
import tcl.lang.TclObject;
import tcl.lang.Var;
import tcl.lang.WrappedCommand;

public class ItclAccess {
	public static boolean isProcCallFrame(CallFrame frame) {
		return frame.isProcCallFrame;
	}

	public static void setProcCallFrameFalse(CallFrame frame) {
		frame.isProcCallFrame = false;
	}

	public static TclObject[] getCallFrameObjv(CallFrame frame) {
		return frame.objv;
	}

	public static Namespace getCallFrameNamespace(CallFrame frame) {
		return frame.ns;
	}

	public static void setCallFrameObjv(CallFrame frame, TclObject[] objv) {
		frame.objv = objv;
	}

	public static CallFrame getCallFrame(Interp interp, int level) {
		CallFrame frame;

		frame = interp.varFrame;
		while (frame != null && level > 0) {
			frame = frame.callerVar;
			level--;
		}
		return frame;
	}

	public static CallFrame activateCallFrame(Interp interp, CallFrame frame) {
		CallFrame oldFrame;

		oldFrame = interp.varFrame;
		interp.varFrame = frame;

		return oldFrame;
	}

	public static CallFrame newCallFrame(Interp i) {
		return new CallFrame(i);
	}

	public static CallFrame getVarFrame(Interp i) {
		return i.varFrame;
	}

	public static HashMap getVarTable(CallFrame frame) {
		return frame.varTable;
	}

	public static void setVarTable(CallFrame frame, HashMap table) {
		frame.varTable = table;
	}

	public static Var newVar() {
		return new Var();
	}

	public static void deleteVars(Interp interp, HashMap varTable) {
		Var.deleteVars(interp, varTable);
	}

	public static int decrVarRefCount(Var var) {
		var.refCount -= 1;
		return var.refCount;
	}

	public static Procedure newProcedure(Interp interp, Namespace ns,
			String name, TclObject args, TclObject b, String sFileName,
			int sLineNumber) throws TclException {
		return new Procedure(interp, ns, name, args, b, sFileName, sLineNumber);
	}

	public static TclObject[][] getArgList(Procedure proc) {
		return proc.argList;
	}

	public static void setWrappedCommand(Procedure proc, WrappedCommand wcmd) {
		proc.wcmd = wcmd;
	}

	public static void assignLocalVar(Interp interp, String name,
			TclObject val, CallFrame frame) throws TclException {
		if (frame.varTable == null) {
			frame.varTable = new HashMap();
		}
		Var var = new Var();
		var.clearVarInHashtable(); // Needed to avoid "dangling namespace var"
		// error
		var.table = frame.varTable;
		frame.varTable.put(name, var);
		interp.setVar(name, null, val, 0);
	}

	public static void createObjVar(Var var, String key, Namespace ns,
			HashMap table) {
		var.hashKey = key;
		var.ns = ns;

		// NOTE: Tcl reports a "dangling upvar" error for variables
		// with a null "hPtr" field. Put something non-zero
		// in here to keep Tcl_SetVar2() happy. The only time
		// this field is really used is it remove a variable
		// from the hash table that contains it in CleanupVar,
		// but since these variables are protected by their
		// higher refCount, they will not be deleted by CleanupVar
		// anyway. These variables are unset and removed in
		// ItclFreeObject().

		var.table = table;
		var.refCount = 1; // protect from being deleted
	}

	public static void createCommonVar(Var var, String key, Namespace ns,
			HashMap table) {
		var.table = table;
		var.hashKey = key;
		var.ns = ns;

		var.setVarNamespace();
		var.refCount++; // one use by namespace
		var.refCount++; // another use by class
	}

	public static Object FirstHashEntry(HashMap table) {
		return Namespace.FirstHashEntry(table);
	}

}
