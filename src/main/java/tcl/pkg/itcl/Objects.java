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
 *  This segment handles "objects" which are instantiated from class
 *  definitions.  Objects contain public/protected/private data members
 *  from all classes in a derivation hierarchy.
 *
 * ========================================================================
 *  AUTHOR:  Michael J. McLennan
 *           Bell Labs Innovations for Lucent Technologies
 *           mmclennan@lucent.com
 *           http://www.tcltk.com/itcl
 *
 *     RCS:  $Id: Objects.java,v 1.3 2006/01/26 19:49:18 mdejong Exp $
 * ========================================================================
 *           Copyright (c) 1993-1998  Lucent Technologies, Inc.
 * ------------------------------------------------------------------------
 * See the file "license.itcl" for information on usage and redistribution
 * of this file, and for a DISCLAIMER OF ALL WARRANTIES.
 */

package tcl.pkg.itcl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import tcl.lang.CallFrame;
import tcl.lang.CommandWithDispose;
import tcl.lang.Interp;
import tcl.lang.Namespace;
import tcl.lang.Resolver;
import tcl.lang.TCL;
import tcl.lang.TclException;
import tcl.lang.TclList;
import tcl.lang.TclObject;
import tcl.lang.TclRuntimeError;
import tcl.lang.TclString;
import tcl.lang.Var;
import tcl.lang.WrappedCommand;

class Objects {
	static HashMap dangleTable = new HashMap();

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_CreateObject -> Objects.CreateObject
	 * 
	 * Creates a new object instance belonging to the given class. Supports
	 * complex object names like "namesp::namesp::name" by following the
	 * namespace path and creating the object in the desired namespace.
	 * 
	 * Automatically creates and initializes data members, including the
	 * built-in protected "this" variable containing the object name. Installs
	 * an access command in the current namespace, and invokes the constructor
	 * to initialize the object.
	 * 
	 * If any errors are encountered, the object is destroyed and this procedure
	 * raises a TclException. Otherwise a reference to a new object is returned.
	 * ------------------------------------------------------------------------
	 */

	static ItclObject CreateObject(Interp interp, // interpreter mananging new
			// object
			String name, // name of new object
			ItclClass cdefn, // class for new object
			TclObject[] objv) // argument objects
			throws TclException {
		int result;
		boolean ctorErr;
		TclException ctorEx = null;

		String head, tail;
		StringBuffer objName;
		Namespace parentNs;
		ItclContext context;
		ItclObject newObj;
		ItclClass cd;
		ItclVarDefn vdefn;
		ItclHierIter hier;
		Itcl_InterpState istate;

		// If installing an object access command will clobber another
		// command, signal an error. Be careful to look for the object
		// only in the current namespace context. Otherwise, we might
		// find a global command, but that wouldn't be clobbered!

		WrappedCommand wcmd = Namespace.findCommand(interp, name, null,
				TCL.NAMESPACE_ONLY);
		// cmd = wcmd.cmd;

		if (wcmd != null && !Cmds.IsStub(wcmd)) {
			throw new TclException(interp, "command \"" + name
					+ "\" already exists in namespace \""
					+ Namespace.getCurrentNamespace(interp).fullName + "\"");
		}

		// Extract the namespace context and the simple object
		// name for the new object.

		Util.ParseNamespPathResult res = Util.ParseNamespPath(name);
		head = res.head;
		tail = res.tail;

		if (head != null) {
			parentNs = Class.FindClassNamespace(interp, head);

			if (parentNs == null) {
				throw new TclException(interp, "namespace \"" + head
						+ "\" not found in context \""
						+ Namespace.getCurrentNamespace(interp).fullName + "\"");
			}
		} else {
			parentNs = Namespace.getCurrentNamespace(interp);
		}

		objName = new StringBuffer();
		if (parentNs != Namespace.getGlobalNamespace(interp)) {
			objName.append(parentNs.fullName);
		}
		objName.append("::");
		objName.append(tail);

		// Create a new object and initialize it.

		newObj = new ItclObject();
		newObj.classDefn = cdefn;
		Util.PreserveData(cdefn);

		newObj.dataSize = cdefn.numInstanceVars;
		newObj.data = new Var[newObj.dataSize];

		newObj.constructed = new HashMap();
		newObj.destructed = null;

		// Add a command to the current namespace with the object name.
		// This is done before invoking the constructors so that the
		// command can be used during construction to query info.

		Util.PreserveData(newObj);
		interp.createCommand(objName.toString(), new HandleInstanceCmd(newObj));
		wcmd = Namespace.findCommand(interp, name, null, TCL.NAMESPACE_ONLY);
		newObj.w_accessCmd = wcmd;
		newObj.accessCmd = wcmd.cmd;

		Util.PreserveData(newObj); // while cmd exists in the interp
		// Itcl_EventuallyFree((ClientData)newObj, ItclFreeObject);

		// Install the class namespace and object context so that
		// the object's data members can be initialized via simple
		// "set" commands.

		context = new ItclContext(interp);
		Methods.PushContext(interp, null, cdefn, newObj, context);

		hier = new ItclHierIter();
		Class.InitHierIter(hier, cdefn);

		cd = Class.AdvanceHierIter(hier);
		while (cd != null) {
			for (Iterator iter = cd.variables.entrySet().iterator(); iter
					.hasNext();) {
				Map.Entry entry = (Map.Entry) iter.next();
				vdefn = (ItclVarDefn) entry.getValue();

				if ((vdefn.member.flags & ItclInt.THIS_VAR) != 0) {
					if (cd == cdefn) {
						CreateObjVar(interp, vdefn, newObj);
						interp.setVar("this", TclString.newInstance(""), 0);
						interp.traceVar("this", newObj, TCL.TRACE_READS
								| TCL.TRACE_WRITES);
					}
				} else if ((vdefn.member.flags & ItclInt.COMMON) == 0) {
					CreateObjVar(interp, vdefn, newObj);
				}
			}
			cd = Class.AdvanceHierIter(hier);
		}
		Class.DeleteHierIter(hier);

		Methods.PopContext(interp, context); // back to calling context

		// Now construct the object. Look for a constructor in the
		// most-specific class, and if there is one, invoke it.
		// This will cause a chain reaction, making sure that all
		// base classes constructors are invoked as well, in order
		// from least- to most-specific. Any constructors that are
		// not called out explicitly in "initCode" code fragments are
		// invoked implicitly without arguments.

		ctorErr = true;
		try {
			Methods.InvokeMethodIfExists(interp, "constructor", cdefn, newObj,
					objv);
			ctorErr = false;
		} catch (TclException ex) {
			ctorEx = ex;
		}

		// If there is no constructor, construct the base classes
		// in case they have constructors. This will cause the
		// same chain reaction.

		if (cdefn.functions.get("constructor") == null) {
			ctorErr = true;
			try {
				Methods.ConstructBase(interp, newObj, cdefn);
				ctorErr = false;
			} catch (TclException ex) {
				ctorEx = ex;
			}
		}

		// If construction failed, then delete the object access
		// command. This will destruct the object and delete the
		// object data. Be careful to save and restore the interpreter
		// state, since the destructors may generate errors of their own.

		if (ctorErr) {
			istate = Util.SaveInterpState(interp, 0);

			// Bug 227824.
			// The constructor may destroy the object, possibly indirectly
			// through the destruction of the main widget in the iTk
			// megawidget it tried to construct. If this happens we must
			// not try to destroy the access command a second time.

			if (newObj.accessCmd != null) {
				if (interp.deleteCommandFromToken(newObj.w_accessCmd) != 0) {
					throw new TclRuntimeError(
							"could not delete instance command from token");
				}
				newObj.accessCmd = null;
			}
			result = Util.RestoreInterpState(interp, istate);
		}

		// At this point, the object is fully constructed.
		// Destroy the "constructed" table in the object data, since
		// it is no longer needed.

		newObj.constructed.clear();
		newObj.constructed = null;

		// Add it to the list of all known objects. The only
		// tricky thing to watch out for is the case where the
		// object deleted itself inside its own constructor.
		// In that case, we don't want to add the object to
		// the list of valid objects. We can determine that
		// the object deleted itself by checking to see if
		// its accessCmd member is NULL.

		if (!ctorErr && (newObj.accessCmd != null)) {
			cdefn.info.objects.put(newObj.accessCmd, newObj);
		}

		// Release the object. If it was destructed above, it will
		// die at this point.

		Util.ReleaseData(newObj);

		if (ctorErr) {
			throw ctorEx;
		}

		return newObj;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_DeleteObject -> Objects.DeleteObject
	 * 
	 * Attempts to delete an object by invoking its destructor.
	 * 
	 * If the destructor is successful, then the object is deleted by removing
	 * its access command, and this procedure returns normally. Otherwise, the
	 * object will remain alive, and this procedure raises a TclException.
	 * ------------------------------------------------------------------------
	 */

	static void DeleteObject(Interp interp, // interpreter mananging object
			ItclObject contextObj) // object to be deleted
			throws TclException {
		ItclClass cdefn = contextObj.classDefn;

		Util.PreserveData(contextObj);

		// Invoke the object's destructors.

		try {
			Objects.DestructObject(interp, contextObj, 0);
		} catch (TclException ex) {
			Util.ReleaseData(contextObj);
			throw ex;
		}

		// Remove the object from the global list.

		cdefn.info.objects.remove(contextObj.accessCmd);

		// Change the object's access command so that it can be
		// safely deleted without attempting to destruct the object
		// again. Then delete the access command. If this is
		// the last use of the object data, the object will die here.

		((HandleInstanceCmd) contextObj.accessCmd).deleteToken = true;

		if (interp.deleteCommandFromToken(contextObj.w_accessCmd) != 0) {
			throw new TclRuntimeError(
					"could not delete instance command from token");
		}
		contextObj.accessCmd = null;

		Util.ReleaseData(contextObj); // object should die here
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_DestructObject -> Objects.DestructObject
	 * 
	 * Invokes the destructor for a particular object. Usually invoked by
	 * DeleteObject() or DestroyObject() as a part of the object destruction
	 * process. If the ItclInt.IGNORE_ERRS flag is included, all destructors are
	 * invoked even if errors are encountered.
	 * 
	 * Raises a TclException if anything goes wrong.
	 * ------------------------------------------------------------------------
	 */

	static void DestructObject(Interp interp, // interpreter mananging new
			// object
			ItclObject contextObj, // object to be destructed
			int flags) // flags: ItclInt.IGNORE_ERRS
			throws TclException {
		int result;

		// If there is a "destructed" table, then this object is already
		// being destructed. Flag an error, unless errors are being
		// ignored.

		if (contextObj.destructed != null) {
			if ((flags & ItclInt.IGNORE_ERRS) == 0) {
				throw new TclException(interp,
						"can't delete an object while it is being destructed");
			}
			return;
		}

		// Create a "destructed" table to keep track of which destructors
		// have been invoked. This is used in DestructBase to make
		// sure that all base class destructors have been called,
		// explicitly or implicitly.

		contextObj.destructed = new HashMap();

		// Destruct the object starting from the most-specific class.
		// If all goes well, return the null string as the result.

		TclException dtorEx = null;

		try {
			Objects.DestructBase(interp, contextObj, contextObj.classDefn,
					flags);
		} catch (TclException ex) {
			dtorEx = ex;
		}

		if (dtorEx == null) {
			interp.resetResult();
		}

		contextObj.destructed.clear();
		contextObj.destructed = null;

		if (dtorEx != null) {
			throw dtorEx;
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * ItclDestructBase -> Objects.DestructBase
	 * 
	 * Invoked by DestructObject() to recursively destruct an object from the
	 * specified class level. Finds and invokes the destructor for the specified
	 * class, and then recursively destructs all base classes. If the
	 * ItclInt.IGNORE_ERRS flag is included, all destructors are invoked even if
	 * errors are encountered.
	 * 
	 * Raises a TclException if anything goes wrong.
	 * ------------------------------------------------------------------------
	 */

	static void DestructBase(Interp interp, // interpreter
			ItclObject contextObj, // object being destructed
			ItclClass contextClass, // current class being destructed
			int flags) // flags: ItclInt.IGNORE_ERRS
			throws TclException {
		Itcl_ListElem elem;
		ItclClass cdefn;

		// Look for a destructor in this class, and if found,
		// invoke it.

		if (contextObj.destructed.get(contextClass.name) == null) {
			Methods.InvokeMethodIfExists(interp, "destructor", contextClass,
					contextObj, null);
		}

		// Scan through the list of base classes recursively and destruct
		// them. Traverse the list in normal order, so that we destruct
		// from most- to least-specific.

		elem = Util.FirstListElem(contextClass.bases);
		while (elem != null) {
			cdefn = (ItclClass) Util.GetListValue(elem);

			Objects.DestructBase(interp, contextObj, cdefn, flags);
			elem = Util.NextListElem(elem);
		}

		// Throw away any result from the destructors and return.

		interp.resetResult();
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_FindObject -> Objects.FindObject
	 * 
	 * Searches for an object with the specified name, which have namespace
	 * scope qualifiers like "namesp::namesp::name", or may be a scoped value
	 * such as "namespace inscope ::foo obj".
	 * 
	 * Raises a TclException if anything goes wrong. If an object was found, it
	 * is returned. Otherwise, null is returned.
	 * ------------------------------------------------------------------------
	 */

	static ItclObject FindObject(Interp interp, // interpreter containing this
			// object
			String name) // name of the object
			throws TclException {
		Namespace contextNs = null;

		String cmdName;
		WrappedCommand wcmd;
		ItclObject ro;

		// The object name may be a scoped value of the form
		// "namespace inscope <namesp> <command>". If it is,
		// decode it.

		Util.DecodeScopedCommandResult res = Util.DecodeScopedCommand(interp,
				name);
		contextNs = res.rNS;
		cmdName = res.rCmd;

		// Look for the object's access command, and see if it has
		// the appropriate command handler.

		try {
			wcmd = Namespace.findCommand(interp, cmdName, contextNs, 0);
		} catch (TclException ex) {
			wcmd = null;
		}

		if (wcmd != null && Objects.IsObject(wcmd)) {
			return Objects.GetContextFromObject(wcmd);
		} else {
			return null;
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_IsObject -> Objects.IsObject
	 * 
	 * Checks the given Tcl command to see if it represents an itcl object.
	 * Returns true if the command is associated with an object.
	 * ------------------------------------------------------------------------
	 */

	static boolean IsObject(WrappedCommand wcmd) // command being tested
	{
		if (wcmd.cmd instanceof HandleInstanceCmd) {
			return true;
		}

		// This may be an imported command. Try to get the real
		// command and see if it represents an object.

		wcmd = Namespace.getOriginalCommand(wcmd);
		if ((wcmd != null) && (wcmd.cmd instanceof HandleInstanceCmd)) {
			return true;
		}
		return false;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Objects.GetContextFromObject
	 * 
	 * Return the ItclObject context object associated with a given This
	 * function assumes that IsObject() returns true for this command.
	 * ------------------------------------------------------------------------
	 */

	static ItclObject GetContextFromObject(WrappedCommand wcmd) // command that
	// represents
	// the object
	{
		return ((HandleInstanceCmd) wcmd.cmd).contextObj;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ObjectIsa -> Objects.ObjectIsa
	 * 
	 * Checks to see if an object belongs to the given class. An object "is-a"
	 * member of the class if the class appears anywhere in its inheritance
	 * hierarchy. Returns true if the object belongs to the class, and false
	 * otherwise.
	 * ------------------------------------------------------------------------
	 */

	static boolean ObjectIsa(ItclObject contextObj, // object being tested
			ItclClass cdefn) // class to test for "is-a" relationship
	{
		return (contextObj.classDefn.heritage.get(cdefn) != null);
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_HandleInstance -> Object.HandleInstanceCmd.cmdProc
	 * 
	 * Invoked by Tcl whenever the user issues a command associated with an
	 * object instance. Handles the following syntax:
	 * 
	 * <objName> <method> <args>...
	 * 
	 * ------------------------------------------------------------------------
	 */

	static class HandleInstanceCmd implements CommandWithDispose {
		ItclObject contextObj;
		boolean deleteToken;

		HandleInstanceCmd(ItclObject contextObj) {
			this.contextObj = contextObj;
			deleteToken = false;
		}

		// Invoked when the instance command is deleted in the Tcl interp.

		public void disposeCmd() {
			if (deleteToken == false) {
				Objects.DestroyObject(contextObj);
			} else {
				Util.ReleaseData(contextObj);
			}
		}

		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			String token;
			ItclMemberFunc mfunc;
			ItclObjectInfo info;
			ItclContext context;
			CallFrame frame;

			if (objv.length < 2) {
				throw new TclException(interp,
						"wrong # args: should be one of..."
								+ ReportObjectUsage(interp, contextObj));
			}

			// Make sure that the specified operation is really an
			// object method, and it is accessible. If not, return usage
			// information for the object.

			token = objv[1].toString();

			mfunc = (ItclMemberFunc) contextObj.classDefn.resolveCmds
					.get(token);
			if (mfunc != null) {
				if ((mfunc.member.flags & ItclInt.COMMON) != 0) {
					mfunc = null;
				} else if (mfunc.member.protection != Itcl.PUBLIC) {
					Namespace contextNs = Util.GetTrueNamespace(interp,
							mfunc.member.classDefn.info);

					if (!Util.CanAccessFunc(mfunc, contextNs)) {
						mfunc = null;
					}
				}
			}

			if (mfunc == null && !token.equals("info")) {
				throw new TclException(interp, "bad option \"" + token
						+ "\": should be one of..."
						+ ReportObjectUsage(interp, contextObj));
			}

			// Install an object context and invoke the method.
			//
			// TRICKY NOTE: We need to pass the object context into the
			// method, but activating the context here puts us one level
			// down, and when the method is called, it will activate its
			// own context, putting us another level down. If anyone
			// were to execute an "uplevel" command in the method, they
			// would notice the extra call frame. So we mark this frame
			// as "transparent" and Itcl_EvalMemberCode will automatically
			// do an "uplevel" operation to correct the problem.

			info = contextObj.classDefn.info;

			context = new ItclContext(interp);
			Methods.PushContext(interp, null, contextObj.classDefn, contextObj,
					context);

			try { // start context release block

				frame = context.frame;
				Util.PushStack(frame, info.transparentFrames);

				// Bug 227824
				// The tcl core will blow up in 'TclLookupVar' if we don't reset
				// the 'isProcCallFrame'. This happens because without the
				// callframe refered to by 'framePtr' will be inconsistent
				// ('isProcCallFrame' set, but 'procPtr' not set).

				if (token.equals("info")) {
					ItclAccess.setProcCallFrameFalse(frame);
				}

				TclObject cmdline = Util.CreateArgs(interp, null, objv, 1);
				TclObject[] cmdlinev = TclList.getElements(interp, cmdline);
				Util.EvalArgs(interp, cmdlinev);

			} finally { // end context release block
				Util.PopStack(info.transparentFrames);
				Methods.PopContext(interp, context);
			}
		}
	} // end class HandleInstanceCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_GetInstanceVar -> Object.GetInstanceVar
	 * 
	 * Returns the current value for an object data member. The member name is
	 * interpreted with respect to the given class scope, which is usually the
	 * most-specific class for the object.
	 * 
	 * If successful, this procedure returns a pointer to a string value which
	 * remains alive until the variable changes it value. If anything goes
	 * wrong, this returns null.
	 * ------------------------------------------------------------------------
	 */

	static String GetInstanceVar(Interp interp, // current interpreter
			String name, // name of desired instance variable
			ItclObject contextObj, // current object
			ItclClass contextClass) // name is interpreted in this scope
	{
		ItclContext context;
		TclObject val = null;

		// Make sure that the current namespace context includes an
		// object that is being manipulated.

		if (contextObj == null) {
			interp
					.setResult("cannot access object-specific info without an object context");
			return null;
		}

		// Install the object context and access the data member
		// like any other variable.

		context = new ItclContext(interp);
		try {
			Methods
					.PushContext(interp, null, contextClass, contextObj,
							context);
		} catch (TclException ex) {
			return null;
		}

		try {
			val = interp.getVar(name, TCL.LEAVE_ERR_MSG);
		} catch (TclException ex) {
			// No-op
		} finally {
			Methods.PopContext(interp, context);
		}

		if (val != null) {
			return val.toString();
		} else {
			return null;
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * ItclReportObjectUsage -> ReportObjectUsage
	 * 
	 * Returns a String object summarizing the usage for all of the methods
	 * available for this object. Useful when reporting errors in
	 * Itcl_HandleInstance().
	 * ------------------------------------------------------------------------
	 */

	static String ReportObjectUsage(Interp interp, // current interpreter
			ItclObject contextObj) // current object
	{
		ItclClass cdefn = contextObj.classDefn;
		int ignore = ItclInt.CONSTRUCTOR | ItclInt.DESTRUCTOR | ItclInt.COMMON;

		int cmp;
		String name;
		Itcl_List cmdList;
		Itcl_ListElem elem;
		ItclMemberFunc mfunc, cmpDefn;

		// Scan through all methods in the virtual table and sort
		// them in alphabetical order. Report only the methods
		// that have simple names (no ::'s) and are accessible.

		cmdList = new Itcl_List();
		Util.InitList(cmdList);

		for (Iterator iter = cdefn.resolveCmds.entrySet().iterator(); iter
				.hasNext();) {
			Map.Entry entry = (Map.Entry) iter.next();
			name = (String) entry.getKey();
			mfunc = (ItclMemberFunc) entry.getValue();

			if ((name.indexOf("::") != -1)
					|| (mfunc.member.flags & ignore) != 0) {
				mfunc = null;
			} else if (mfunc.member.protection != Itcl.PUBLIC) {
				Namespace contextNs = Util.GetTrueNamespace(interp,
						mfunc.member.classDefn.info);

				if (!Util.CanAccessFunc(mfunc, contextNs)) {
					mfunc = null;
				}
			}

			if (mfunc != null) {
				elem = Util.FirstListElem(cmdList);
				while (elem != null) {
					cmpDefn = (ItclMemberFunc) Util.GetListValue(elem);
					cmp = mfunc.member.name.compareTo(cmpDefn.member.name);
					if (cmp < 0) {
						Util.InsertListElem(elem, mfunc);
						mfunc = null;
						break;
					} else if (cmp == 0) {
						mfunc = null;
						break;
					}
					elem = Util.NextListElem(elem);
				}
				if (mfunc != null) {
					Util.AppendList(cmdList, mfunc);
				}
			}
		}

		// Add a series of statements showing usage info.

		StringBuffer buffer = new StringBuffer(64);

		elem = Util.FirstListElem(cmdList);
		while (elem != null) {
			mfunc = (ItclMemberFunc) Util.GetListValue(elem);
			buffer.append("\n  ");
			Methods.GetMemberFuncUsage(mfunc, contextObj, buffer);

			elem = Util.NextListElem(elem);
		}
		Util.DeleteList(cmdList);

		return buffer.toString();
	}

	/*
	 * ------------------------------------------------------------------------
	 * ItclTraceThisVar -> Objects.TraceThisVar
	 * 
	 * Invoked to handle read/write traces on the "this" variable built into
	 * each object.
	 * 
	 * On read, this procedure updates the "this" variable to contain the
	 * current object name. This is done dynamically, since an object's identity
	 * can change if its access command is renamed.
	 * 
	 * On write, this procedure returns an error string, warning that the "this"
	 * variable cannot be set.
	 * ------------------------------------------------------------------------
	 */

	static void TraceThisVar(ItclObject contextObj, // object instance data
			Interp interp, // interpreter managing this variable
			String name1, // variable name
			String name2, // unused
			int flags) // flags indicating read/write
			throws TclException {
		String objName;

		// Handle read traces on "this"

		if ((flags & TCL.TRACE_READS) != 0) {
			if (contextObj.accessCmd != null) {
				objName = interp.getCommandFullName(contextObj.w_accessCmd);
			} else {
				objName = "";
			}

			interp.setVar(name1, TclString.newInstance(objName), 0);

			return;
		}

		// Handle write traces on "this"

		if ((flags & TCL.TRACE_WRITES) != 0) {
			throw new TclException(interp,
					"variable \"this\" cannot be modified");
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * ItclDestroyObject -> Objects.DestroyObject
	 * 
	 * Invoked when the object access command is deleted to implicitly destroy
	 * the object. Invokes the object's destructors, ignoring any errors
	 * encountered along the way. Removes the object from the list of all known
	 * objects and releases the access command's claim to the object data.
	 * 
	 * Note that the usual way to delete an object is via DeleteObject(). This
	 * procedure is provided as a back-up, to handle the case when an object is
	 * deleted by removing its access command.
	 * ------------------------------------------------------------------------
	 */

	static void DestroyObject(ItclObject contextObj) // object instance data
	{
		ItclClass cdefn = contextObj.classDefn;
		Itcl_InterpState istate;

		// Attempt to destruct the object, but ignore any errors.

		istate = Util.SaveInterpState(cdefn.interp, 0);
		try {
			Objects.DestructObject(cdefn.interp, contextObj,
					ItclInt.IGNORE_ERRS);
		} catch (TclException ex) {
			// Ignore any TclException that comes from DestructObject.
			// The code does not actually check IGNORE_ERRS and
			// avoid throwing an exception, so just ignore it here.
		}
		Util.RestoreInterpState(cdefn.interp, istate);

		// Now, remove the object from the global object list.
		// We're careful to do this here, after calling the destructors.
		// Once the access command is nulled out, the "this" variable
		// won't work properly.

		if (contextObj.accessCmd != null) {
			cdefn.info.objects.remove(contextObj.accessCmd);
			contextObj.accessCmd = null;
		}

		Util.ReleaseData(contextObj);
	}

	/*
	 * ------------------------------------------------------------------------
	 * ItclFreeObject -> Objects.FreeObject
	 * 
	 * Deletes all instance variables and frees all memory associated with the
	 * given object instance. This is usually invoked automatically by
	 * Itcl_ReleaseData(), when an object's data is no longer being used.
	 * ------------------------------------------------------------------------
	 */

	static void FreeObject(ItclObject contextObj) // object instance data
	{
		Interp interp = contextObj.classDefn.interp;

		ItclClass cd;
		ItclHierIter hier;
		ItclVarDefn vdefn;
		ItclContext context;
		Itcl_InterpState istate;

		// Install the class namespace and object context so that
		// the object's data members can be destroyed via simple
		// "unset" commands. This makes sure that traces work properly
		// and all memory gets cleaned up.
		//
		// NOTE: Be careful to save and restore the interpreter state.
		// Data can get freed in the middle of any operation, and
		// we can't affort to clobber the interpreter with any errors
		// from below.

		istate = Util.SaveInterpState(interp, 0);

		// Scan through all object-specific data members and destroy the
		// actual variables that maintain the object state. Do this
		// by unsetting each variable, so that traces are fired off
		// correctly. Make sure that the built-in "this" variable is
		// only destroyed once. Also, be careful to activate the
		// namespace for each class, so that private variables can
		// be accessed.

		hier = new ItclHierIter();
		Class.InitHierIter(hier, contextObj.classDefn);
		cd = Class.AdvanceHierIter(hier);
		while (cd != null) {

			boolean pushErr = false;

			context = new ItclContext(interp);

			try {
				Methods.PushContext(interp, null, cd, contextObj, context);
			} catch (TclException ex) {
				pushErr = true;
			}

			if (!pushErr) {
				for (Iterator iter = cd.variables.entrySet().iterator(); iter
						.hasNext();) {
					Map.Entry entry = (Map.Entry) iter.next();
					String key = (String) entry.getKey();
					vdefn = (ItclVarDefn) entry.getValue();

					if ((vdefn.member.flags & ItclInt.THIS_VAR) != 0) {
						if (cd == contextObj.classDefn) {
							try {
								interp.unsetVar(vdefn.member.fullname, 0);
							} catch (TclException ex) {
							}
						}
					} else if ((vdefn.member.flags & ItclInt.COMMON) == 0) {
						try {
							interp.unsetVar(vdefn.member.fullname, 0);
						} catch (TclException ex) {
						}
					}
				}
				Methods.PopContext(interp, context);
			}

			cd = Class.AdvanceHierIter(hier);
		}
		Class.DeleteHierIter(hier);

		// Free the memory associated with object-specific variables.
		// For normal variables this would be done automatically by
		// CleanupVar() when the variable is unset. But object-specific
		// variables are protected by an extra reference count, and they
		// must be deleted explicitly here.

		for (int i = 0; i < contextObj.dataSize; i++) {
			if (contextObj.data[i] != null) {
				contextObj.data[i] = null;
			}
		}

		Util.RestoreInterpState(interp, istate);

		// Free any remaining memory associated with the object.

		contextObj.data = null;

		if (contextObj.constructed != null) {
			contextObj.constructed.clear();
			contextObj.constructed = null;
		}
		if (contextObj.destructed != null) {
			contextObj.destructed.clear();
			contextObj.destructed = null;
		}
		Util.ReleaseData(contextObj.classDefn);
	}

	/*
	 * ------------------------------------------------------------------------
	 * ItclCreateObjVar -> Objects.CreateObjVar
	 * 
	 * Creates one variable acting as a data member for a specific object.
	 * Initializes the variable according to its definition, and sets up its
	 * reference count so that it cannot be deleted by ordinary means. Installs
	 * the new variable directly into the data array for the specified object.
	 * ------------------------------------------------------------------------
	 */

	static void CreateObjVar(Interp interp, // interpreter managing this object
			ItclVarDefn vdefn, // variable definition
			ItclObject contextObj) // object being updated
	{
		Var var;
		ItclVarLookup vlookup;
		ItclContext context;

		var = Migrate.NewVar();
		ItclAccess.createObjVar(var, vdefn.member.name,
				vdefn.member.classDefn.namesp, dangleTable);

		// Install the new variable in the object's data array.
		// Look up the appropriate index for the object using
		// the data table in the class definition.

		vlookup = (ItclVarLookup) contextObj.classDefn.resolveVars
				.get(vdefn.member.fullname);

		if (vlookup != null) {
			contextObj.data[vlookup.index] = var;
		}

		// If this variable has an initial value, initialize it
		// here using a "set" command.
		//
		// TRICKY NOTE: We push an object context for the class that
		// owns the variable, so that we don't have any trouble
		// accessing it.

		if (vdefn.init != null) {
			context = new ItclContext(interp);
			try {
				Methods.PushContext(interp, null, vdefn.member.classDefn,
						contextObj, context);
				interp.setVar(vdefn.member.fullname, TclString
						.newInstance(vdefn.init), 0);
			} catch (TclException ex) {
				// No-op
			} finally {
				Methods.PopContext(interp, context);
			}
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ScopedVarResolver -> Objects.ScopedVarResolver
	 * 
	 * This procedure is installed to handle variable resolution throughout an
	 * entire interpreter. It looks for scoped variable references of the form:
	 * 
	 * @itcl ::namesp::namesp::object variable
	 * 
	 * If a reference like this is recognized, this procedure finds the desired
	 * variable in the object and returns the variable. If the variable does not
	 * start with "@itcl", this procedure returns null and variable resolution
	 * continues using the normal rules. If anything goes wrong, this procedure
	 * raises a TclException and variable access is denied.
	 * ------------------------------------------------------------------------
	 */

	static Var ScopedVarResolver(Interp interp, // current interpreter
			String name, // variable name being resolved
			Namespace contextNs, // current namespace context
			int flags) // TCL.LEAVE_ERR_MSG => leave error message
			throws TclException {
		ItclObject contextObj;
		ItclVarLookup vlookup;

		// See if the variable starts with "@itcl". If not, then
		// let the variable resolution process continue.

		if (!name.startsWith("@itcl")) {
			return null;
		}

		// Break the variable name into parts and extract the object
		// name and the variable name.

		// Note: Always assume that an exception should be raised on error
		// which ignores TCL.LEAVE_ERR_MSG.

		TclObject list = TclString.newInstance(name);
		TclObject[] elems = TclList.getElements(interp, list);

		if (elems.length != 3) {
			throw new TclException(interp, "scoped variable \"" + name
					+ "\" is malformed: " + "should be: @itcl object variable");
		}

		// Look for the command representing the object and extract
		// the object context.

		WrappedCommand wcmd = Namespace.findCommand(interp,
				elems[1].toString(), null, 0);
		if (Objects.IsObject(wcmd)) {
			contextObj = Objects.GetContextFromObject(wcmd);
		} else {
			throw new TclException(interp, "can't resolve scoped variable \""
					+ name + "\": " + "can't find object " + elems[1]);
		}

		// Resolve the variable with respect to the most-specific
		// class definition.

		vlookup = (ItclVarLookup) contextObj.classDefn.resolveVars.get(elems[2]
				.toString());
		if (vlookup == null) {
			throw new TclException(interp, "can't resolve scoped variable \""
					+ name + "\": " + "no such data member " + elems[2]);
		}

		return contextObj.data[vlookup.index];
	}

	static class ScopedVarResolverImpl implements Resolver {
		public WrappedCommand resolveCmd(Interp interp, // The current
				// interpreter.
				String name, // Command name to resolve.
				Namespace context, // The namespace to look in.
				int flags) // 0 or TCL.LEAVE_ERR_MSG.
				throws TclException // Tcl exceptions are thrown for Tcl errors.
		{
			return null; // Do not resolve anything
		}

		public Var resolveVar(Interp interp, // The current interpreter.
				String name, // Variable name to resolve.
				Namespace context, // The namespace to look in.
				int flags) // 0 or TCL.LEAVE_ERR_MSG.
				throws TclException // Tcl exceptions are thrown for Tcl errors.
		{
			return Objects.ScopedVarResolver(interp, name, context, flags);
		}
	}

} // end class Objects

