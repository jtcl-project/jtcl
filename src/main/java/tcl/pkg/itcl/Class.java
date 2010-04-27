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
 *  These procedures handle class definitions.  Classes are composed of
 *  data members (public/protected/common) and the member functions
 *  (methods/procs) that operate on them.  Each class has its own
 *  namespace which manages the class scope.
 *
 * ========================================================================
 *  AUTHOR:  Michael J. McLennan
 *           Bell Labs Innovations for Lucent Technologies
 *           mmclennan@lucent.com
 *           http://www.tcltk.com/itcl
 *
 *     RCS:  $Id: Class.java,v 1.4 2006/01/26 19:49:18 mdejong Exp $
 * ========================================================================
 *           Copyright (c) 1993-1998  Lucent Technologies, Inc.
 * ------------------------------------------------------------------------
 * See the file "license.terms" for information on usage and redistribution
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
import tcl.lang.Var;
import tcl.lang.WrappedCommand;

// Note: ItclResolvedVarInfo structure not ported since it seems
// to be used only in the bytecode compiler implementation.

class Class {

	static int itclCompatFlags = Cmds.itclCompatFlags;

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_CreateClass -> Class.CreateClass
	 * 
	 * Creates a namespace and its associated class definition data. If a
	 * namespace already exists with that name, then this routine will raise a
	 * TclException to indicate failure. If successful, a reference to a new
	 * class definition will be returned.
	 * ------------------------------------------------------------------------
	 */

	static ItclClass CreateClass(Interp interp, // interpreter that will contain
			// new class
			String path, // name of new class
			ItclObjectInfo info) // info for all known objects
			throws TclException {
		String head, tail;
		WrappedCommand wcmd;
		Namespace classNs;
		ItclClass cd;
		ItclVarDefn vdefn;

		// Make sure that a class with the given name does not
		// already exist in the current namespace context. If a
		// namespace exists, that's okay. It may have been created
		// to contain stubs during a "namespace import" operation.
		// We'll just replace the namespace data below with the
		// proper class data.

		classNs = Namespace.findNamespace(interp, path, null, 0);

		if (classNs != null && Class.IsClassNamespace(classNs)) {
			throw new TclException(interp, "class \"" + path
					+ "\" already exists");
		}

		// Make sure that a command with the given class name does not
		// already exist in the current namespace. This prevents the
		// usual Tcl commands from being clobbered when a programmer
		// makes a bogus call like "class info".

		wcmd = Namespace.findCommand(interp, path, null, TCL.NAMESPACE_ONLY);

		if (wcmd != null && !Cmds.IsStub(wcmd)) {
			StringBuffer buffer = new StringBuffer(64);

			buffer.append("command \"" + path + "\" already exists");

			if (path.indexOf("::") == -1) {
				buffer
						.append(" in namespace \""
								+ Namespace.getCurrentNamespace(interp).fullName
								+ "\"");
			}

			throw new TclException(interp, buffer.toString());
		}

		// Make sure that the class name does not have any goofy
		// characters:
		//
		// . => reserved for member access like: class.publicVar

		Util.ParseNamespPathResult res = Util.ParseNamespPath(path);
		head = res.head;
		tail = res.tail;

		if (tail.indexOf(".") != -1) {
			throw new TclException(interp, "bad class name \"" + tail + "\"");
		}

		// Allocate class definition data.

		cd = new ItclClass();
		cd.name = null;
		cd.fullname = null;
		cd.interp = interp;
		cd.info = info;
		Util.PreserveData(info);
		cd.namesp = null;
		cd.accessCmd = null;
		cd.w_accessCmd = null;

		cd.variables = new HashMap();
		cd.functions = new HashMap();

		cd.numInstanceVars = 0;
		cd.resolveVars = new HashMap();
		cd.resolveCmds = new HashMap();

		cd.bases = new Itcl_List();
		Util.InitList(cd.bases);
		cd.derived = new Itcl_List();
		Util.InitList(cd.derived);

		cd.initCode = null;
		cd.unique = 0;
		cd.flags = 0;

		// Initialize the heritage info--each class starts with its
		// own class definition in the heritage. Base classes are
		// added to the heritage from the "inherit" statement.

		cd.heritage = new HashMap();
		cd.heritage.put(cd, "");

		// Create a namespace to represent the class. Add the class
		// definition info as client data for the namespace. If the
		// namespace already exists, then replace any existing client
		// data with the class data.

		Util.PreserveData(cd);

		if (classNs == null) {
			classNs = Namespace.createNamespace(interp, path,
					new DestroyClassNamespImpl(cd));
		} else {
			if (classNs.deleteProc != null) {
				classNs.deleteProc.delete();
			}
			classNs.deleteProc = new DestroyClassNamespImpl(cd);
		}

		// Util.EventuallyFree(cd, ItclFreeClass);

		if (classNs == null) {
			Util.ReleaseData(cd);
			throw new TclException(interp, interp.getResult().toString());
		}

		cd.namesp = classNs;

		cd.name = classNs.name;

		cd.fullname = classNs.fullName;

		// Add special name resolution procedures to the class namespace
		// so that members are accessed according to the rules for
		// [incr Tcl].

		Resolver resolver = new ClassResolverImpl();
		Namespace.setNamespaceResolver(classNs, resolver);

		// Add the built-in "this" variable to the list of data members.

		try {
			vdefn = CreateVarDefn(interp, cd, "this", null, null);
		} catch (TclException ex) {
			throw new TclRuntimeError("unexpected TclException");
		}

		vdefn.member.protection = Itcl.PROTECTED; // always "protected"
		vdefn.member.flags |= ItclInt.THIS_VAR; // mark as "this" variable

		cd.variables.put("this", vdefn);

		// Create a command in the current namespace to manage the class:
		// <className>
		// <className> <objName> ?<constructor-args>?

		Util.PreserveData(cd);

		interp.createCommand(cd.fullname, new HandleClassCmd(cd));

		cd.w_accessCmd = Namespace.findCommand(interp, cd.fullname, null,
				TCL.NAMESPACE_ONLY);
		cd.accessCmd = cd.w_accessCmd.cmd;

		return cd;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_DeleteClass -> Class.DeleteClass
	 * 
	 * Deletes a class by deleting all derived classes and all objects in that
	 * class, and finally, by destroying the class namespace. This procedure
	 * provides a friendly way of doing this. If any errors are detected along
	 * the way, the process is aborted.
	 * 
	 * Will raise a TclException to indicate failure.
	 * ------------------------------------------------------------------------
	 */

	static void DeleteClass(Interp interp, // interpreter managing this class
			ItclClass cdefn) // class namespace
			throws TclException {
		ItclClass cd = null;

		Itcl_ListElem elem;
		ItclObject contextObj;

		// Destroy all derived classes, since these lose their meaning
		// when the base class goes away. If anything goes wrong,
		// abort with an error.
		//
		// TRICKY NOTE: When a derived class is destroyed, it
		// automatically deletes itself from the "derived" list.

		elem = Util.FirstListElem(cdefn.derived);
		while (elem != null) {
			cd = (ItclClass) Util.GetListValue(elem);
			elem = Util.NextListElem(elem); // advance here--elem will go away

			try {
				Class.DeleteClass(interp, cd);
			} catch (TclException ex) {
				DeleteClassFailed(interp, cd.namesp.fullName, ex);
			}
		}

		// Scan through and find all objects that belong to this class.
		// Note that more specialized objects have already been
		// destroyed above, when derived classes were destroyed.
		// Destroy objects and report any errors.

		for (Iterator iter = cdefn.info.objects.entrySet().iterator(); iter
				.hasNext();) {
			Map.Entry entry = (Map.Entry) iter.next();
			contextObj = (ItclObject) entry.getValue();

			if (contextObj.classDefn == cdefn) {
				try {
					Objects.DeleteObject(interp, contextObj);
				} catch (TclException ex) {
					cd = cdefn;
					DeleteClassFailed(interp, cd.namesp.fullName, ex);
				}

				// Fix 227804: Whenever an object to delete was found we
				// have to reset the search to the beginning as the
				// current entry in the search was deleted and accessing it
				// is therefore not allowed anymore.

				iter = cdefn.info.objects.entrySet().iterator();
			}
		}

		// Destroy the namespace associated with this class.
		//
		// TRICKY NOTE:
		// The cleanup procedure associated with the namespace is
		// invoked automatically. It does all of the same things
		// above, but it also disconnects this class from its
		// base-class lists, and removes the class access command.

		Namespace.deleteNamespace(cdefn.namesp);
	}

	// Helper function used when DeleteClass fails

	static void DeleteClassFailed(Interp interp, String fullName,
			TclException ex) throws TclException {
		StringBuffer buffer = new StringBuffer(64);

		buffer.append("\n    (while deleting class \"");
		buffer.append(fullName);
		buffer.append("\")");
		interp.addErrorInfo(buffer.toString());

		throw ex;
	}

	/*
	 * ------------------------------------------------------------------------
	 * ItclDestroyClass -> Class.DestroyClass
	 * 
	 * Invoked whenever the access command for a class is destroyed. Destroys
	 * the namespace associated with the class, which also destroys all objects
	 * in the class and all derived classes. Disconnects this class from the
	 * "derived" class lists of its base classes, and releases any claim to the
	 * class definition data. If this is the last use of that data, the class
	 * will completely vanish at this point.
	 * ------------------------------------------------------------------------
	 */

	static void DestroyClass(ItclClass cdefn) // class definition to be
	// destroyed
	{
		cdefn.accessCmd = null;
		cdefn.w_accessCmd = null;

		Namespace.deleteNamespace(cdefn.namesp);
		Util.ReleaseData(cdefn);
	}

	/*
	 * ------------------------------------------------------------------------
	 * ItclDestroyClassNamesp -> Class.DestroyClassNamesp
	 * 
	 * Invoked whenever the namespace associated with a class is destroyed.
	 * Destroys all objects associated with this class and all derived classes.
	 * Disconnects this class from the "derived" class lists of its base
	 * classes, and removes the class access command. Releases any claim to the
	 * class definition data. If this is the last use of that data, the class
	 * will completely vanish at this point.
	 * ------------------------------------------------------------------------
	 */

	static void DestroyClassNamesp(ItclClass cdefn) {
		ItclObject contextObj;
		Itcl_ListElem elem, belem;
		ItclClass cd, base, derived;

		// Destroy all derived classes, since these lose their meaning
		// when the base class goes away.
		//
		// TRICKY NOTE: When a derived class is destroyed, it
		// automatically deletes itself from the "derived" list.

		elem = Util.FirstListElem(cdefn.derived);
		while (elem != null) {
			cd = (ItclClass) Util.GetListValue(elem);
			Namespace.deleteNamespace(cd.namesp);

			// As the first namespace is now destroyed we have to get the
			// new first element of the hash table. We cannot go to the
			// next element from the current one, because the current one
			// is deleted. itcl Patch #593112, for Bug #577719.

			elem = Util.FirstListElem(cdefn.derived);
		}

		// Scan through and find all objects that belong to this class.
		// Destroy them quietly by deleting their access command.

		for (Iterator iter = cdefn.info.objects.entrySet().iterator(); iter
				.hasNext();) {
			Map.Entry entry = (Map.Entry) iter.next();
			contextObj = (ItclObject) entry.getValue();

			if (contextObj.classDefn == cdefn) {
				cdefn.interp.deleteCommandFromToken(contextObj.w_accessCmd);

				// Fix 227804: Whenever an object to delete was found we
				// have to reset the search to the beginning as the
				// current entry in the search was deleted and accessing it
				// is therefore not allowed anymore.

				iter = cdefn.info.objects.entrySet().iterator();
			}
		}

		// Next, remove this class from the "derived" list in
		// all base classes.

		belem = Util.FirstListElem(cdefn.bases);
		while (belem != null) {
			base = (ItclClass) Util.GetListValue(belem);

			elem = Util.FirstListElem(base.derived);
			while (elem != null) {
				derived = (ItclClass) Util.GetListValue(elem);
				if (derived == cdefn) {
					Util.ReleaseData((ItclClass) Util.GetListValue(elem));
					elem = Util.DeleteListElem(elem);
				} else {
					elem = Util.NextListElem(elem);
				}
			}
			belem = Util.NextListElem(belem);
		}

		// Next, destroy the access command associated with the class.

		if (cdefn.accessCmd != null) {
			HandleClassCmd hcc = (HandleClassCmd) cdefn.accessCmd;

			// Set flag in HandleClassCmd instance so that
			// Util.ReleaseData() will be invoked
			// at command destroy time instead of DestroyClass().
			hcc.release = true;

			cdefn.interp.deleteCommandFromToken(cdefn.w_accessCmd);
		}

		// Release the namespace's claim on the class definition.

		Util.ReleaseData(cdefn);
	}

	// Helper class that implements namespace delete callback.
	// Pass as the deleteproc argument to createNamespace.

	static class DestroyClassNamespImpl implements Namespace.DeleteProc {
		ItclClass cdefn;

		DestroyClassNamespImpl(ItclClass cdefn) {
			this.cdefn = cdefn;
		}

		public void delete() {
			DestroyClassNamesp(cdefn);
		}

	} // end class DestroyClassNamesp

	/*
	 * ------------------------------------------------------------------------
	 * ItclFreeClass -> Class.FreeClass
	 * 
	 * Frees all memory associated with a class definition. This is usually
	 * invoked automatically by Itcl_ReleaseData(), when class data is no longer
	 * being used.
	 * ------------------------------------------------------------------------
	 */

	static void FreeClass(ItclClass cdefn) // class definition to be destroyed
	{
		Itcl_ListElem elem;
		ItclVarDefn vdefn;
		ItclVarLookup vlookup;
		Var var;
		HashMap varTable;

		// Tear down the list of derived classes. This list should
		// really be empty if everything is working properly, but
		// release it here just in case.

		elem = Util.FirstListElem(cdefn.derived);
		while (elem != null) {
			Util.ReleaseData((ItclClass) Util.GetListValue(elem));
			elem = Util.NextListElem(elem);
		}
		Util.DeleteList(cdefn.derived);
		cdefn.derived = null;

		// Tear down the variable resolution table. Some records
		// appear multiple times in the table (for x, foo::x, etc.)
		// so each one has a reference count.

		varTable = new HashMap();

		for (Iterator iter = cdefn.resolveVars.entrySet().iterator(); iter
				.hasNext();) {
			Map.Entry entry = (Map.Entry) iter.next();
			vlookup = (ItclVarLookup) entry.getValue();

			if (--vlookup.usage == 0) {
				// If this is a common variable owned by this class,
				// then release the class's hold on it. If it's no
				// longer being used, move it into a variable table
				// for destruction.

				if ((vlookup.vdefn.member.flags & ItclInt.COMMON) != 0
						&& vlookup.vdefn.member.classDefn == cdefn) {
					var = (Var) vlookup.common;
					if (ItclAccess.decrVarRefCount(var) == 0) {
						varTable.put(vlookup.vdefn.member.fullname, var);
					}
				}
			}
		}
		ItclAccess.deleteVars(cdefn.interp, varTable);
		cdefn.resolveVars = null;

		// Tear down the virtual method table...

		cdefn.resolveCmds.clear();
		cdefn.resolveCmds = null;

		// Delete all variable definitions.

		for (Iterator iter = cdefn.variables.entrySet().iterator(); iter
				.hasNext();) {
			Map.Entry entry = (Map.Entry) iter.next();
			vdefn = (ItclVarDefn) entry.getValue();
			DeleteVarDefn(vdefn);
		}
		cdefn.variables.clear();
		cdefn.variables = null;

		// Delete all function definitions.

		for (Iterator iter = cdefn.functions.entrySet().iterator(); iter
				.hasNext();) {
			Map.Entry entry = (Map.Entry) iter.next();
			ItclMemberFunc mfunc = (ItclMemberFunc) entry.getValue();
			Util.ReleaseData(mfunc);
		}
		cdefn.functions.clear();
		cdefn.functions = null;

		// Release the claim on all base classes.

		elem = Util.FirstListElem(cdefn.bases);
		while (elem != null) {
			Util.ReleaseData((ItclClass) Util.GetListValue(elem));
			elem = Util.NextListElem(elem);
		}
		Util.DeleteList(cdefn.bases);
		cdefn.bases = null;
		cdefn.heritage.clear();
		cdefn.heritage = null;

		// Free up the object initialization code.

		if (cdefn.initCode != null) {
			cdefn.initCode.release();
		}

		Util.ReleaseData(cdefn.info);

		cdefn.name = null;
		cdefn.fullname = null;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_IsClassNamespace -> Class.IsClassNamespace
	 * 
	 * Checks to see whether or not the given namespace represents an [incr Tcl]
	 * class. Returns true if so, and false otherwise.
	 * ------------------------------------------------------------------------
	 */

	static boolean IsClassNamespace(Namespace ns) // namespace being tested
	{
		if (ns != null) {
			return (ns.deleteProc instanceof DestroyClassNamespImpl);
		}
		return false;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Class.GetClassFromNamespace
	 * 
	 * Return the ItclClass associated with a given class namespace. This
	 * function assumes that IsClassNamespace() returns true for this namespace.
	 * ------------------------------------------------------------------------
	 */

	static ItclClass GetClassFromNamespace(Namespace ns) // namespace being
	// tested
	{
		if (ns == null || !(ns.deleteProc instanceof DestroyClassNamespImpl)) {
			throw new TclRuntimeError("namespace is not a class namespace");
		}
		return ((Class.DestroyClassNamespImpl) ns.deleteProc).cdefn;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_IsClass -> Class.IsClass
	 * 
	 * Checks the given Tcl command to see if it represents an itcl class.
	 * Returns true if the command is associated with a class.
	 * ------------------------------------------------------------------------
	 */

	static boolean IsClass(WrappedCommand wcmd) // command being tested
	{
		HandleClassCmd hcc = null;
		WrappedCommand origCmd;

		if (wcmd.cmd instanceof HandleClassCmd) {
			hcc = (HandleClassCmd) wcmd.cmd;
		} else {
			// May be an imported command
			origCmd = Namespace.getOriginalCommand(wcmd);
			if ((origCmd != null) && (origCmd.cmd instanceof HandleClassCmd)) {
				hcc = (HandleClassCmd) origCmd.cmd;
			}
		}

		if (hcc != null && hcc.release == false) {
			return true;
		}
		return false;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_FindClass -> Class.FindClass
	 * 
	 * Searches for the specified class in the active namespace. If the class is
	 * found, this procedure returns a pointer to the class definition.
	 * Otherwise, if the autoload flag is true, an attempt will be made to
	 * autoload the class definition. If it still can't be found, this procedure
	 * returns null.
	 * ------------------------------------------------------------------------
	 */

	static ItclClass FindClass(Interp interp, // interpreter containing class
			String path, // path name for class
			boolean autoload) // should class be loaded automatically
	{
		Namespace classNs;

		// Search for a namespace with the specified name, and if
		// one is found, see if it is a class namespace.

		classNs = FindClassNamespace(interp, path);

		if (classNs != null && IsClassNamespace(classNs)) {
			return GetClassFromNamespace(classNs);
		}

		// If the autoload flag is set, try to autoload the class
		// definition.

		if (autoload) {
			try {
				interp.eval("::auto_load \"" + path + "\"");
			} catch (TclException ex) {
				interp
						.addErrorInfo("\n    (while attempting to autoload class \""
								+ path + "\")");
				return null;
			}
			interp.resetResult();

			classNs = FindClassNamespace(interp, path);
			if (classNs != null && IsClassNamespace(classNs)) {
				return GetClassFromNamespace(classNs);
			}
		}

		String result = interp.getResult().toString();
		StringBuffer sb = new StringBuffer(64);
		sb.append(result);
		sb.append("class \"");
		sb.append(path);
		sb.append("\" not found in context \"");
		sb.append(Namespace.getCurrentNamespace(interp).fullName);
		sb.append("\"");
		interp.setResult(sb.toString());

		return null;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_FindClassNamespace -> Class.FindClassNamespace
	 * 
	 * Searches for the specified class namespace. The normal Tcl procedure
	 * Tcl_FindNamespace also searches for namespaces, but only in the current
	 * namespace context. This makes it hard to find one class from within
	 * another. For example, suppose. you have two namespaces Foo and Bar. If
	 * you're in the context of Foo and you look for Bar, you won't find it with
	 * Tcl_FindNamespace. This behavior is okay for namespaces, but wrong for
	 * classes.
	 * 
	 * This procedure search for a class namespace. If the name is absolute
	 * (i.e., starts with "::"), then that one name is checked, and the class is
	 * either found or not. But if the name is relative, it is sought in the
	 * current namespace context and in the global context, just like the normal
	 * command lookup.
	 * 
	 * This procedure returns a reference to the desired namespace, or null if
	 * the namespace was not found.
	 * ------------------------------------------------------------------------
	 */

	static Namespace FindClassNamespace(Interp interp, // interpreter containing
			// class
			String path) // path name for class
	{
		Namespace contextNs = Namespace.getCurrentNamespace(interp);
		Namespace classNs;
		StringBuffer buffer;

		// Look up the namespace. If the name is not absolute, then
		// see if it's the current namespace, and try the global
		// namespace as well.

		classNs = Namespace.findNamespace(interp, path, null, 0);

		if (classNs == null && contextNs.parent != null
				&& (!path.startsWith("::"))) {

			if (contextNs.name.equals(path)) {
				classNs = contextNs;
			} else {
				buffer = new StringBuffer(64);
				buffer.append("::");
				buffer.append(path);

				classNs = Namespace.findNamespace(interp, buffer.toString(),
						null, 0);
			}
		}
		return classNs;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_HandleClass -> Class.HandleClassCmd.cmdProc
	 * 
	 * Invoked by Tcl whenever the user issues the command associated with a
	 * class name. Handles the following syntax:
	 * 
	 * <className> <objName> ?<args>...?
	 * 
	 * If arguments are specified, then this procedure creates a new object
	 * named <objName> in the appropriate class. Note that if <objName> contains
	 * "#auto", that part is automatically replaced by a unique string built
	 * from the class name.
	 * ------------------------------------------------------------------------
	 */

	static class HandleClassCmd implements CommandWithDispose {
		ItclClass cdefn;
		boolean release = false;

		HandleClassCmd(ItclClass cdefn) {
			this.cdefn = cdefn;
		}

		public void disposeCmd() {
			if (release == false) {
				DestroyClass(cdefn);
			} else {
				Util.ReleaseData(cdefn);
			}
		}

		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			StringBuffer unique; // buffer used for unique part of object names
			StringBuffer buffer; // buffer used to build object names
			String token, objName, start;
			TclObject cmdline;
			TclObject[] cmdlinev;

			ItclObject newObj;
			CallFrame frame;

			// If the command is invoked without an object name, then do
			// nothing.
			// This used to support autoloading--that the class name could be
			// invoked as a command by itself, prompting the autoloader to
			// load the class definition. We retain the behavior here for
			// backward-compatibility with earlier releases.

			if (objv.length == 1) {
				return;
			}

			// If the object name is "::", and if this is an old-style class
			// definition, then treat the remaining arguments as a command
			// in the class namespace. This used to be the way of invoking
			// a class proc, but the new syntax is "class::proc" (without
			// spaces).

			token = objv[1].toString();
			if (token.equals("::") && (objv.length > 2)) {
				if ((cdefn.flags & ItclInt.OLD_STYLE) != 0) {

					frame = ItclAccess.newCallFrame(interp);
					Namespace.pushCallFrame(interp, frame, cdefn.namesp, false);

					cmdline = Util.CreateArgs(interp, null, objv, 2);
					cmdlinev = TclList.getElements(interp, cmdline);

					try {
						Util.EvalArgs(interp, cmdlinev);
						return;
					} finally {
						Namespace.popCallFrame(interp);
					}
				}

				// If this is not an old-style class, then return an error
				// describing the syntax change.

				throw new TclException(
						interp,
						"syntax \"class :: proc\" is an anachronism\n"
								+ "[incr Tcl] no longer supports this syntax.\n"
								+ "Instead, remove the spaces from your procedure invocations:\n"
								+ "  " + objv[0] + "::" + objv[2] + " ?args?");
			}

			// Otherwise, we have a proper object name. Create a new instance
			// with that name. If the name contains "#auto", replace this with
			// a uniquely generated string based on the class name.

			buffer = new StringBuffer(64);
			objName = null;

			start = token;

			if (start.indexOf("#auto") != -1) {
				String prefix;
				String suffix;

				if (start.equals("#auto")) {
					prefix = null;
					suffix = null;
				} else if (start.startsWith("#auto")) {
					prefix = null;
					suffix = start.substring(5);
				} else if (start.endsWith("#auto")) {
					prefix = start.substring(0, start.length() - 5);
					suffix = null;
				} else {
					int index = start.indexOf("#auto");
					prefix = start.substring(0, index);
					suffix = start.substring(index + 5);
				}

				// Substitute a unique part in for "#auto", and keep
				// incrementing a counter until a valid name is found.

				unique = new StringBuffer(64);

				while (true) {
					String first = cdefn.name.substring(0, 1).toLowerCase();
					unique.setLength(0);
					unique.append(first);
					unique.append(cdefn.name.substring(1));
					unique.append(cdefn.unique++);

					buffer.setLength(0);
					if (prefix != null) {
						buffer.append(prefix);
					}
					buffer.append(unique);
					if (suffix != null) {
						buffer.append(suffix);
					}

					objName = buffer.toString();

					// Check for any commands with the given name, not just
					// objects.

					if (interp.getCommand(objName) == null)
						break; // No command with this name, use it
				}
			}

			// If "#auto" was not found, then just use object name as-is.

			if (objName == null) {
				objName = token;
			}

			// Try to create a new object. If successful, return the
			// object name as the result of this command.

			cmdline = Util.CreateArgs(interp, null, objv, 2);
			cmdlinev = TclList.getElements(interp, cmdline);

			newObj = Objects.CreateObject(interp, objName, cdefn, cmdlinev);
			interp.setResult(objName);
		}

	} // end class HandleClassCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ClassCmdResolver -> Class.ClassCmdResolver
	 * 
	 * Used by the class namespaces to handle name resolution for all commands.
	 * This procedure looks for references to class methods and procs, and
	 * returns the WrappedCommand if found. If a command is private a
	 * TclException will be raised and access to the command is denied. If a
	 * command is not recognized, this procedure returns null and the lookup
	 * continues via the normal Tcl name resolution rules.
	 * ------------------------------------------------------------------------
	 */

	static WrappedCommand ClassCmdResolver(Interp interp, // current interpreter
			String name, // name of the command being accessed
			Namespace context, // namespace performing the resolution
			int flags) // TCL.LEAVE_ERR_MSG => leave error messages
			// in interp if anything goes wrong
			throws TclException {
		ItclClass cdefn = GetClassFromNamespace(context);

		ItclMemberFunc mfunc;
		WrappedCommand wcmd;

		boolean isCmdDeleted;

		// If the command is a member function, and if it is
		// accessible, return its Tcl command handle.

		mfunc = (ItclMemberFunc) cdefn.resolveCmds.get(name);

		if (mfunc == null) {
			return null; // Command not resolved
		}

		// For protected/private functions, figure out whether or
		// not the function is accessible from the current context.
		//
		// TRICKY NOTE: Use Itcl_GetTrueNamespace to determine
		// the current context. If the current call frame is
		// "transparent", this handles it properly.

		if (mfunc.member.protection != Itcl.PUBLIC) {
			context = Util.GetTrueNamespace(interp, cdefn.info);

			if (!Util.CanAccessFunc(mfunc, context)) {

				// Throw exception even if TCL.LEAVE_ERR_MSG is zero

				throw new TclException(interp, "can't access \"" + name
						+ "\": " + Util.ProtectionStr(mfunc.member.protection)
						+ " variable");

				// FIXME: The above says variable, but it should be command,
				// right?
				// this is likely something that is not tested.
			}
		}

		// Looks like we found an accessible member function.
		//
		// TRICKY NOTE: Check to make sure that the command handle
		// is still valid. If someone has deleted or renamed the
		// command, it may not be. This is just the time to catch
		// it--as it is being resolved again by the compiler.

		wcmd = mfunc.w_accessCmd;
		isCmdDeleted = wcmd.deleted;

		if (isCmdDeleted) {
			// disallow access!

			mfunc.accessCmd = null;
			mfunc.w_accessCmd = null;

			// Ignored TCL.LEAVE_ERR_MSG

			throw new TclException(interp, "can't access \"" + name
					+ "\": deleted or redefined\n"
					+ "(use the \"body\" command to redefine methods/procs)");
		}

		return mfunc.w_accessCmd;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ClassVarResolver -> Class.ClassVarResolver
	 * 
	 * Used by the class namespaces to handle name resolution for runtime
	 * variable accesses. This procedure looks for references to both common
	 * variables and instance variables at runtime.
	 * 
	 * If a variable is found, this procedure returns it. If a particular
	 * variable is private, this procedure raises a TclException and access to
	 * the variable is denied. If a variable is not recognized, this procedure
	 * returns null and lookup continues via the normal Tcl name resolution
	 * rules.
	 * ------------------------------------------------------------------------
	 */

	static Var ClassVarResolver(Interp interp, // current interpreter
			String name, // name of the variable being accessed
			Namespace context, // namespace performing the resolution
			int flags) // TCL.LEAVE_ERR_MSG => leave error messages
			// in interp if anything goes wrong
			throws TclException {
		CallFrame varFrame = ItclAccess.getVarFrame(interp);

		ItclClass cdefn;
		ItclObject contextObj;
		CallFrame frame;
		Var var;
		ItclVarLookup vlookup;
		HashMap vtable;

		Util.Assert(IsClassNamespace(context), "IsClassNamespace(context)");
		cdefn = GetClassFromNamespace(context);

		// If this is a global variable, handle it in the usual
		// Tcl manner.

		if ((flags & TCL.GLOBAL_ONLY) != 0) {
			return null;
		}

		// See if this is a formal parameter in the current proc scope.
		// If so, that variable has precedence. Look it up and return
		// it here. This duplicates some of the functionality of
		// TclLookupVar, but we return it here (instead of returning
		// null) to avoid looking it up again later.

		if (varFrame != null && ItclAccess.isProcCallFrame(varFrame)
				&& name.indexOf("::") == -1) {

			// Skip "compiled locals" search here.

			// Look in the frame's var hash table.

			vtable = ItclAccess.getVarTable(varFrame);
			if (vtable != null) {
				var = (Var) vtable.get(name);
				if (var != null) {
					return var;
				}
			}
		}

		// See if the variable is a known data member and accessible.

		vlookup = (ItclVarLookup) cdefn.resolveVars.get(name);
		if (vlookup == null) {
			return null;
		}
		if (!vlookup.accessible) {
			return null;
		}

		// If this is a common data member, then its variable
		// is easy to find. Return it directly.

		if ((vlookup.vdefn.member.flags & ItclInt.COMMON) != 0) {
			return vlookup.common;
		}

		// If this is an instance variable, then we have to
		// find the object context, then index into its data
		// array to get the actual variable.

		frame = Migrate.GetCallFrame(interp, 0);

		contextObj = (ItclObject) cdefn.info.contextFrames.get(frame);
		if (contextObj == null) {
			return null;
		}

		// TRICKY NOTE: We've resolved the variable in the current
		// class context, but we must also be careful to get its
		// index from the most-specific class context. Variables
		// are arranged differently depending on which class
		// constructed the object.

		if (contextObj.classDefn != vlookup.vdefn.member.classDefn) {
			ItclVarLookup tmp = (ItclVarLookup) contextObj.classDefn.resolveVars
					.get(vlookup.vdefn.member.fullname);
			if (tmp != null) {
				vlookup = tmp;
			}
		}
		return contextObj.data[vlookup.index];
	}

	// Note: Itcl_ClassCompiledVarResolver not ported
	// Note: ItclClassRuntimeVarResolver not ported

	// Helper class that implements var and cmd resolver
	// for a namespace.

	static class ClassResolverImpl implements Resolver {
		public WrappedCommand resolveCmd(Interp interp, // The current
				// interpreter.
				String name, // Command name to resolve.
				Namespace context, // The namespace to look in.
				int flags) // 0 or TCL.LEAVE_ERR_MSG.
				throws TclException // Tcl exceptions are thrown for Tcl errors.
		{
			return Class.ClassCmdResolver(interp, name, context, flags);
		}

		public Var resolveVar(Interp interp, // The current interpreter.
				String name, // Variable name to resolve.
				Namespace context, // The namespace to look in.
				int flags) // 0 or TCL.LEAVE_ERR_MSG.
				throws TclException // Tcl exceptions are thrown for Tcl errors.
		{
			return Class.ClassVarResolver(interp, name, context, flags);
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_BuildVirtualTables -> Class.BuildVirtualTables
	 * 
	 * Invoked whenever the class heritage changes or members are added or
	 * removed from a class definition to rebuild the member lookup tables.
	 * There are two tables:
	 * 
	 * METHODS: resolveCmds Used primarily in Itcl_ClassCmdResolver() to resolve
	 * all command references in a namespace.
	 * 
	 * DATA MEMBERS: resolveVars Used primarily in Itcl_ClassVarResolver() to
	 * quickly resolve variable references in each class scope.
	 * 
	 * These tables store every possible name for each command/variable (member,
	 * class::member, namesp::class::member, etc.). Members in a derived class
	 * may shadow members with the same name in a base class. In that case, the
	 * simple name in the resolution table will point to the most-specific
	 * member.
	 * ------------------------------------------------------------------------
	 */

	static void BuildVirtualTables(ItclClass cdefn) // class definition being
	// updated
	{
		ItclVarLookup vlookup;
		ItclVarDefn vdefn;
		ItclMemberFunc mfunc;
		ItclHierIter hier;
		ItclClass cd;
		Namespace ns;
		StringBuffer buffer, buffer2;
		boolean newEntry;
		String key;

		buffer = new StringBuffer(64);
		buffer2 = new StringBuffer(64);

		// Clear the variable resolution table.

		for (Iterator iter = cdefn.resolveVars.entrySet().iterator(); iter
				.hasNext();) {
			Map.Entry entry = (Map.Entry) iter.next();
			vlookup = (ItclVarLookup) entry.getValue();
			if (--vlookup.usage == 0) {
				// ckfree(vlookup);
			}
		}

		cdefn.resolveVars.clear();
		cdefn.resolveVars = new HashMap();
		cdefn.numInstanceVars = 0;

		// Set aside the first object-specific slot for the built-in
		// "this" variable. Only allocate one of these, even though
		// there is a definition for "this" in each class scope.

		cdefn.numInstanceVars++;

		// Scan through all classes in the hierarchy, from most to
		// least specific. Add a lookup entry for each variable
		// into the table.

		hier = new ItclHierIter();
		Class.InitHierIter(hier, cdefn);
		cd = Class.AdvanceHierIter(hier);
		while (cd != null) {
			for (Iterator iter = cd.variables.entrySet().iterator(); iter
					.hasNext();) {
				Map.Entry entry = (Map.Entry) iter.next();
				key = (String) entry.getKey();
				vdefn = (ItclVarDefn) entry.getValue();

				vlookup = new ItclVarLookup();
				vlookup.vdefn = vdefn;
				vlookup.usage = 0;
				vlookup.leastQualName = null;

				// If this variable is PRIVATE to another class scope,
				// then mark it as "inaccessible".

				vlookup.accessible = (vdefn.member.protection != Itcl.PRIVATE || vdefn.member.classDefn == cdefn);

				// If this is a common variable, then keep a reference to
				// the variable directly. Otherwise, keep an index into
				// the object's variable table.

				if ((vdefn.member.flags & ItclInt.COMMON) != 0) {
					ns = cd.namesp;
					vlookup.common = (Var) ns.varTable.get(vdefn.member.name);
					Util.Assert(vlookup.common != null,
							"vlookup.common != null");
				} else {
					// If this is a reference to the built-in "this"
					// variable, then its index is "0". Otherwise,
					// add another slot to the end of the table.

					if ((vdefn.member.flags & ItclInt.THIS_VAR) != 0) {
						vlookup.index = 0;
					} else {
						vlookup.index = cdefn.numInstanceVars++;
					}
				}

				// Create all possible names for this variable and enter
				// them into the variable resolution table:
				// var
				// class::var
				// namesp1::class::var
				// namesp2::namesp1::class::var
				// ...

				buffer.setLength(0);
				buffer.append(vdefn.member.name);
				ns = cd.namesp;

				while (true) {
					key = buffer.toString();
					newEntry = (cdefn.resolveVars.get(key) == null);

					if (newEntry) {
						cdefn.resolveVars.put(key, vlookup);
						vlookup.usage++;

						if (vlookup.leastQualName == null) {
							vlookup.leastQualName = key;
						}
					}

					if (ns == null) {
						break;
					}
					buffer2.setLength(0);
					buffer2.append(key);
					buffer.setLength(0);
					buffer.append(ns.name);
					buffer.append("::");
					buffer.append(buffer2.toString());

					ns = ns.parent;
				}

				// If this record is not needed, free it now.

				if (vlookup.usage == 0) {
					// ckfree(vlookup);
				}
			}
			cd = Class.AdvanceHierIter(hier);
		}
		Class.DeleteHierIter(hier);

		// Clear the command resolution table.

		cdefn.resolveCmds.clear();
		cdefn.resolveCmds = new HashMap();

		// Scan through all classes in the hierarchy, from most to
		// least specific. Look for the first (most-specific) definition
		// of each member function, and enter it into the table.

		Class.InitHierIter(hier, cdefn);
		cd = Class.AdvanceHierIter(hier);
		while (cd != null) {
			for (Iterator iter = cd.functions.entrySet().iterator(); iter
					.hasNext();) {
				Map.Entry entry = (Map.Entry) iter.next();
				key = (String) entry.getKey();
				mfunc = (ItclMemberFunc) entry.getValue();

				// Create all possible names for this function and enter
				// them into the command resolution table:
				// func
				// class::func
				// namesp1::class::func
				// namesp2::namesp1::class::func
				// ...

				buffer.setLength(0);
				buffer.append(mfunc.member.name);
				ns = cd.namesp;

				while (true) {
					key = buffer.toString();
					newEntry = (cdefn.resolveCmds.get(key) == null);

					if (newEntry) {
						cdefn.resolveCmds.put(key, mfunc);
					}

					if (ns == null) {
						break;
					}
					buffer2.setLength(0);
					buffer2.append(key);
					buffer.setLength(0);
					buffer.append(ns.name);
					buffer.append("::");
					buffer.append(buffer2.toString());

					ns = ns.parent;
				}
			}
			cd = Class.AdvanceHierIter(hier);
		}
		Class.DeleteHierIter(hier);
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_CreateVarDefn -> Class.CreateVarDefn
	 * 
	 * Creates a new class variable definition. If this is a public variable, it
	 * may have a bit of "config" code that is used to update the object
	 * whenever the variable is modified via the built-in "configure" method.
	 * 
	 * Raises a TclException if anything goes wrong. Otherwise, returns a
	 * reference to a new variable definition.
	 * ------------------------------------------------------------------------
	 */

	static ItclVarDefn CreateVarDefn(Interp interp, // interpreter managing this
			// transaction
			ItclClass cdefn, // class containing this variable
			String name, // variable name
			String init, // initial value
			String config) // code invoked when variable is configured
			throws TclException {
		boolean newEntry;
		ItclVarDefn vdefn;
		ItclMemberCode mcode;

		// Add this variable to the variable table for the class.
		// Make sure that the variable name does not already exist.

		newEntry = (cdefn.variables.containsKey(name) == false);

		if (!newEntry) {
			throw new TclException(interp, "variable name \"" + name
					+ "\" already defined in class \"" + cdefn.fullname + "\"");
		}

		// If this variable has some "config" code, try to capture
		// its implementation.

		if (config != null) {
			mcode = Methods.CreateMemberCode(interp, cdefn, null, config);

			Util.PreserveData(mcode);
			// Util.EventuallyFree(mcode, Itcl_DeleteMemberCode);
		} else {
			mcode = null;
		}

		// If everything looks good, create the variable definition.

		vdefn = new ItclVarDefn();
		vdefn.member = CreateMember(interp, cdefn, name);
		vdefn.member.code = mcode;

		if (vdefn.member.protection == Itcl.DEFAULT_PROTECT) {
			vdefn.member.protection = Itcl.PROTECTED;
		}

		vdefn.init = init;

		cdefn.variables.put(name, vdefn);

		return vdefn;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_DeleteVarDefn -> Class.DeleteVarDefn
	 * 
	 * Destroys a variable definition created by CreateVarDefn(), freeing all
	 * resources associated with it.
	 * ------------------------------------------------------------------------
	 */

	static void DeleteVarDefn(ItclVarDefn vdefn) // variable definition to be
	// destroyed
	{
		DeleteMember(vdefn.member);
		vdefn.init = null;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_GetCommonVar -> Class.GetCommonVar
	 * 
	 * Returns the current value for a common class variable. The member name is
	 * interpreted with respect to the given class scope. That scope is
	 * installed as the current context before querying the variable. This
	 * by-passes the protection level in case the variable is "private".
	 * 
	 * If successful, this procedure returns a pointer to a string value which
	 * remains alive until the variable changes it value. If anything goes
	 * wrong, this returns null.
	 * ------------------------------------------------------------------------
	 */

	static String GetCommonVar(Interp interp, // current interpreter
			String name, // name of desired instance variable
			ItclClass contextClass) // name is interpreted in this scope
	{
		CallFrame frame;

		// Activate the namespace for the given class. That installs
		// the appropriate name resolution rules and by-passes any
		// security restrictions.

		frame = ItclAccess.newCallFrame(interp);
		Namespace.pushCallFrame(interp, frame, contextClass.namesp, false);

		try {
			TclObject val = interp.getVar(name, 0);
			if (val == null) {
				return null;
			} else {
				return val.toString();
			}
		} catch (TclException ex) {
			return null;
		} finally {
			Namespace.popCallFrame(interp);
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_CreateMember -> Class.CreateMember
	 * 
	 * Creates the data record representing a class member. This is the generic
	 * representation for a data member or member function. Returns a reference
	 * to the new representation.
	 * ------------------------------------------------------------------------
	 */

	static ItclMember CreateMember(Interp interp, // interpreter managing this
			// action
			ItclClass cdefn, // class definition
			String name) // name of new member
	{
		ItclMember mem;

		// Allocate the memory for a class member and fill in values.

		mem = new ItclMember();
		mem.interp = interp;
		mem.classDefn = cdefn;
		mem.flags = 0;
		mem.protection = Util.Protection(interp, 0);
		mem.code = null;

		StringBuffer buffer = new StringBuffer(64);
		buffer.append(cdefn.fullname);
		buffer.append("::");
		buffer.append(name);
		mem.fullname = buffer.toString();

		mem.name = name;

		return mem;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_DeleteMember -> Class.DeleteMember
	 * 
	 * Destroys all data associated with the given member function definition.
	 * Usually invoked by the interpreter when a member function is deleted.
	 * ------------------------------------------------------------------------
	 */

	static void DeleteMember(ItclMember mem) // pointer to member function
	// definition
	{
		if (mem != null) {
			mem.name = null;
			mem.fullname = null;

			if (mem.code != null) {
				Util.ReleaseData(mem.code);
			}
			mem.code = null;
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_InitHierIter -> Class.InitHierIter
	 * 
	 * Initializes an iterator for traversing the hierarchy of the given class.
	 * Subsequent calls to Itcl_AdvanceHierIter() will return the base classes
	 * in order from most-to-least specific.
	 * ------------------------------------------------------------------------
	 */

	static void InitHierIter(ItclHierIter iter, // iterator used for traversal
			ItclClass cdefn) // class definition for start of traversal
	{
		iter.stack = new Itcl_Stack();
		Util.InitStack(iter.stack);
		Util.PushStack(cdefn, iter.stack);
		iter.current = cdefn;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_DeleteHierIter -> Class.DeleteHierIter
	 * 
	 * Destroys an iterator for traversing class hierarchies, freeing all memory
	 * associated with it.
	 * ------------------------------------------------------------------------
	 */

	static void DeleteHierIter(ItclHierIter iter) // iterator used for traversal
	{
		Util.DeleteStack(iter.stack);
		iter.current = null;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_AdvanceHierIter -> Class.AdvanceHierIter
	 * 
	 * Moves a class hierarchy iterator forward to the next base class. Returns
	 * a pointer to the current class definition, or null when the end of the
	 * hierarchy has been reached.
	 * ------------------------------------------------------------------------
	 */

	static ItclClass AdvanceHierIter(ItclHierIter iter) // iterator used for
	// traversal
	{
		Itcl_ListElem elem;
		ItclClass cd;

		iter.current = (ItclClass) Util.PopStack(iter.stack);

		// Push classes onto the stack in reverse order, so that
		// they will be popped off in the proper order.

		if (iter.current != null) {
			cd = (ItclClass) iter.current;
			elem = Util.LastListElem(cd.bases);
			while (elem != null) {
				Util.PushStack(Util.GetListValue(elem), iter.stack);
				elem = Util.PrevListElem(elem);
			}
		}
		return iter.current;
	}

} // end Class Class

