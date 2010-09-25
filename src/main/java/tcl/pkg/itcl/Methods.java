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
 *  These procedures handle commands available within a class scope.
 *  In [incr Tcl], the term "method" is used for a procedure that has
 *  access to object-specific data, while the term "proc" is used for
 *  a procedure that has access only to common class data.
 *
 * ========================================================================
 *  AUTHOR:  Michael J. McLennan
 *           Bell Labs Innovations for Lucent Technologies
 *           mmclennan@lucent.com
 *           http://www.tcltk.com/itcl
 *
 *     RCS:  $Id: Methods.java,v 1.3 2006/01/26 19:49:18 mdejong Exp $
 * ========================================================================
 *           Copyright (c) 1993-1998  Lucent Technologies, Inc.
 * ------------------------------------------------------------------------
 * See the file "license.itcl" for information on usage and redistribution
 * of this file, and for a DISCLAIMER OF ALL WARRANTIES.
 */

package tcl.pkg.itcl;

import tcl.lang.CallFrame;
import tcl.lang.Command;
import tcl.lang.CommandWithDispose;
import tcl.lang.Interp;
import tcl.lang.Namespace;
import tcl.lang.Procedure;
import tcl.lang.TCL;
import tcl.lang.TclException;
import tcl.lang.TclList;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;
import tcl.lang.TclRuntimeError;
import tcl.lang.TclString;
import tcl.lang.Var;

public class Methods {

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_BodyCmd -> Methods.BodyCmd.cmdProc
	 * 
	 * Invoked by Tcl whenever the user issues an "itcl::body" command to define
	 * or redefine the implementation for a class method/proc. Handles the
	 * following syntax:
	 * 
	 * itcl::body <class>::<func> <arglist> <body>
	 * 
	 * Looks for an existing class member function with the name <func>, and if
	 * found, tries to assign the implementation. If an argument list was
	 * specified in the original declaration, it must match <arglist> or an
	 * error is flagged. If <body> has the form "@name" then it is treated as a
	 * reference to a C handling procedure; otherwise, it is taken as a body of
	 * Tcl statements.
	 * 
	 * Returns if successful, raises TclException if something goes wrong.
	 * ------------------------------------------------------------------------
	 */
	public static class BodyCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			String head, tail, token, arglist, body;
			ItclClass cdefn;
			ItclMemberFunc mfunc;

			if (objv.length != 4) {
				throw new TclNumArgsException(interp, 1, objv,
						"class::func arglist body");
			}

			// Parse the member name "namesp::namesp::class::func".
			// Make sure that a class name was specified, and that the
			// class exists.

			token = objv[1].toString();
			Util.ParseNamespPathResult res = Util.ParseNamespPath(token);
			head = res.head;
			tail = res.tail;

			if (head == null || head.length() == 0) {
				throw new TclException(interp,
						"missing class specifier for body declaration \""
								+ token + "\"");
			}

			cdefn = Class.FindClass(interp, head, true);
			if (cdefn == null) {
				throw new TclException(interp, interp.getResult().toString());
			}

			// Find the function and try to change its implementation.
			// Note that command resolution table contains *all* functions,
			// even those in a base class. Make sure that the class
			// containing the method definition is the requested class.

			mfunc = (ItclMemberFunc) cdefn.resolveCmds.get(tail);
			if (mfunc != null) {
				if (mfunc.member.classDefn != cdefn) {
					mfunc = null;
				}
			}

			if (mfunc == null) {
				throw new TclException(interp, "function \"" + tail
						+ "\" is not defined in class \"" + cdefn.fullname
						+ "\"");
			}

			arglist = objv[2].toString();
			body = objv[3].toString();

			Methods.ChangeMemberFunc(interp, mfunc, arglist, body);
		}
	} // end class BodyCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ConfigBodyCmd -> Methods.ConfigBodyCmd.cmdProc
	 * 
	 * Invoked by Tcl whenever the user issues an "itcl::configbody" command to
	 * define or redefine the configuration code associated with a public
	 * variable. Handles the following syntax:
	 * 
	 * itcl::configbody <class>::<publicVar> <body>
	 * 
	 * Looks for an existing public variable with the name <publicVar>, and if
	 * found, tries to assign the implementation. If <body> has the form "@name"
	 * then it is treated as a reference to a C handling procedure; otherwise,
	 * it is taken as a body of Tcl statements.
	 * 
	 * Returns if successful, raises TclException if something goes wrong.
	 * ------------------------------------------------------------------------
	 */
	public static class ConfigBodyCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			String head, tail, token;
			ItclClass cdefn;
			ItclVarLookup vlookup;
			ItclMember member;
			ItclMemberCode mcode;

			if (objv.length != 3) {
				throw new TclNumArgsException(interp, 1, objv,
						"class::option body");
			}

			// Parse the member name "namesp::namesp::class::option".
			// Make sure that a class name was specified, and that the
			// class exists.

			token = objv[1].toString();
			Util.ParseNamespPathResult res = Util.ParseNamespPath(token);
			head = res.head;
			tail = res.tail;

			if (head == null || head.length() == 0) {
				throw new TclException(interp,
						"missing class specifier for body declaration \""
								+ token + "\"");
			}

			cdefn = Class.FindClass(interp, head, true);
			if (cdefn == null) {
				throw new TclException(interp, interp.getResult().toString());
			}

			// Find the variable and change its implementation.
			// Note that variable resolution table has *all* variables,
			// even those in a base class. Make sure that the class
			// containing the variable definition is the requested class.

			vlookup = (ItclVarLookup) cdefn.resolveVars.get(tail);
			if (vlookup != null) {
				if (vlookup.vdefn.member.classDefn != cdefn) {
					vlookup = null;
				}
			}

			if (vlookup == null) {
				throw new TclException(interp, "option \"" + tail
						+ "\" is not defined in class \"" + cdefn.fullname
						+ "\"");
			}
			member = vlookup.vdefn.member;

			if (member.protection != Itcl.PUBLIC) {
				throw new TclException(interp, "option \"" + member.fullname
						+ "\" is not a public configuration option");
			}

			token = objv[2].toString();

			mcode = Methods.CreateMemberCode(interp, cdefn, null, token);

			Util.PreserveData(mcode);
			// Itcl_EventuallyFree(mcode, Itcl_DeleteMemberCode);

			if (member.code != null) {
				Util.ReleaseData(member.code);
			}
			member.code = mcode;
		}
	} // end class ConfigBodyCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_CreateMethod -> Methods.CreateMethod
	 * 
	 * Installs a method into the namespace associated with a class. If another
	 * command with the same name is already installed, then it is overwritten.
	 * 
	 * Returns if successful, raises TclException if something goes wrong.
	 * ------------------------------------------------------------------------
	 */

	static void CreateMethod(Interp interp, // interpreter managing this action
			ItclClass cdefn, // class definition
			String name, // name of new method
			String arglist, // space-separated list of arg names
			String body) // body of commands for the method
			throws TclException {
		ItclMemberFunc mfunc;
		StringBuffer buffer;
		String qname;

		// Make sure that the method name does not contain anything
		// goofy like a "::" scope qualifier.

		if (name.indexOf("::") != -1) {
			throw new TclException(interp, "bad method name \"" + name + "\"");
		}

		// Create the method definition.

		mfunc = Methods.CreateMemberFunc(interp, cdefn, name, arglist, body);

		// Build a fully-qualified name for the method, and install
		// the command handler.

		buffer = new StringBuffer(64);
		buffer.append(cdefn.namesp.fullName);
		buffer.append("::");
		buffer.append(name);
		qname = buffer.toString();

		Util.PreserveData(mfunc);
		interp.createCommand(qname, new ExecMethod(mfunc));

		mfunc.w_accessCmd = Namespace.findCommand(interp, qname, null,
				TCL.NAMESPACE_ONLY);
		mfunc.accessCmd = mfunc.w_accessCmd.cmd;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_CreateProc -> Methods.CreateProc
	 * 
	 * Installs a class proc into the namespace associated with a class. If
	 * another command with the same name is already installed, then it is
	 * overwritten. Returns if successful, raises TclException if something goes
	 * wrong.
	 * ------------------------------------------------------------------------
	 */

	static void CreateProc(Interp interp, // interpreter managing this action
			ItclClass cdefn, // class definition
			String name, // name of new proc
			String arglist, // space-separated list of arg names
			String body) // body of commands for the proc
			throws TclException {
		ItclMemberFunc mfunc;
		StringBuffer buffer;
		String qname;

		// Make sure that the proc name does not contain anything
		// goofy like a "::" scope qualifier.

		if (name.indexOf("::") != -1) {
			throw new TclException(interp, "bad proc name \"" + name + "\"");
		}

		// Create the proc definition.

		mfunc = Methods.CreateMemberFunc(interp, cdefn, name, arglist, body);

		// Mark procs as "common". This distinguishes them from methods.

		mfunc.member.flags |= ItclInt.COMMON;

		// Build a fully-qualified name for the proc, and install
		// the command handler.

		buffer = new StringBuffer(64);
		buffer.append(cdefn.namesp.fullName);
		buffer.append("::");
		buffer.append(name);
		qname = buffer.toString();

		Util.PreserveData(mfunc);
		interp.createCommand(qname, new ExecProc(mfunc));

		mfunc.w_accessCmd = Namespace.findCommand(interp, qname, null,
				TCL.NAMESPACE_ONLY);
		mfunc.accessCmd = mfunc.w_accessCmd.cmd;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_CreateMemberFunc -> Methods.CreateMemberFunc
	 * 
	 * Creates the data record representing a member function. This includes the
	 * argument list and the body of the function. If the body is of the form
	 * "@name", then it is treated as a label for a Java procedure registered by
	 * Itcl_RegisterC().
	 * 
	 * If any errors are encountered, this procedure raises a TclException.
	 * Otherwise, it returns a new ItclMemberFunc reference.
	 * ------------------------------------------------------------------------
	 */

	static ItclMemberFunc CreateMemberFunc(Interp interp, // interpreter
			// managing this
			// action
			ItclClass cdefn, // class definition
			String name, // name of new member
			String arglist, // space-separated list of arg names
			String body) // body of commands for the method
			throws TclException {
		boolean newEntry;
		ItclMemberFunc mfunc;
		ItclMemberCode mcode;

		// Add the member function to the list of functions for
		// the class. Make sure that a member function with the
		// same name doesn't already exist.

		newEntry = (cdefn.functions.get(name) == null);
		if (!newEntry) {
			throw new TclException(interp, "\"" + name
					+ "\" already defined in class \"" + cdefn.fullname + "\"");
		}

		// Try to create the implementation for this command member.

		try {
			mcode = Methods.CreateMemberCode(interp, cdefn, arglist, body);
		} catch (TclException ex) {
			cdefn.functions.remove(name);
			throw ex;
		}
		Util.PreserveData(mcode);
		// Util.EventuallyFree(mcode, Itcl_DeleteMemberCode);

		// Allocate a member function definition and return.

		mfunc = new ItclMemberFunc();
		mfunc.member = Class.CreateMember(interp, cdefn, name);
		mfunc.member.code = mcode;

		if (mfunc.member.protection == Itcl.DEFAULT_PROTECT) {
			mfunc.member.protection = Itcl.PUBLIC;
		}

		mfunc.arglist = null;
		mfunc.argcount = 0;
		mfunc.accessCmd = null;

		if (arglist != null) {
			mfunc.member.flags |= ItclInt.ARG_SPEC;
		}
		if (mcode.arglist != null) {
			CreateArgListResult cr = Methods.CreateArgList(interp, arglist);
			mfunc.arglist = cr.arglist;
			mfunc.argcount = cr.argcount;
		}

		if (name.equals("constructor")) {
			mfunc.member.flags |= ItclInt.CONSTRUCTOR;
		}
		if (name.equals("destructor")) {
			mfunc.member.flags |= ItclInt.DESTRUCTOR;
		}

		cdefn.functions.put(name, mfunc);
		Util.PreserveData(mfunc);
		// Util.EventuallyFree(mfunc, Itcl_DeleteMemberFunc);

		return mfunc;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ChangeMemberFunc -> Methods.ChangeMemberFunc
	 * 
	 * Modifies the data record representing a member function. This is usually
	 * the body of the function, but can include the argument list if it was not
	 * defined when the member was first created. If the body is of the form
	 * "@name", then it is treated as a label for a Java procedure registered by
	 * Itcl_RegisterC().
	 * 
	 * Returns if successful, raises TclException if something goes wrong.
	 * ------------------------------------------------------------------------
	 */

	static void ChangeMemberFunc(Interp interp, // interpreter managing this
			// action
			ItclMemberFunc mfunc, // command member being changed
			String arglist, // space-separated list of arg names
			String body) // body of commands for the method
			throws TclException {
		ItclMemberCode mcode = null;
		TclObject obj;

		// Try to create the implementation for this command member.

		mcode = Methods.CreateMemberCode(interp, mfunc.member.classDefn,
				arglist, body);

		// If the argument list was defined when the function was
		// created, compare the arg lists or usage strings to make sure
		// that the interface is not being redefined.

		if ((mfunc.member.flags & ItclInt.ARG_SPEC) != 0
				&& !Methods.EquivArgLists(mfunc.arglist, mfunc.argcount,
						mcode.arglist, mcode.argcount)) {

			obj = Methods.ArgList(mfunc.argcount, mfunc.arglist);

			StringBuffer buffer = new StringBuffer(64);
			buffer.append("argument list changed for function \"");
			buffer.append(mfunc.member.fullname);
			buffer.append("\": should be \"");
			buffer.append(obj.toString());
			buffer.append("\"");

			Methods.DeleteMemberCode(mcode);

			throw new TclException(interp, buffer.toString());
		}

		// Free up the old implementation and install the new one.

		Util.PreserveData(mcode);
		// Util.EventuallyFree(mcode, Itcl_DeleteMemberCode);

		Util.ReleaseData(mfunc.member.code);
		mfunc.member.code = mcode;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_DeleteMemberFunc -> Methods.DeleteMemberFunc
	 * 
	 * Destroys all data associated with the given member function definition.
	 * Usually invoked by the interpreter when a member function is deleted.
	 * ------------------------------------------------------------------------
	 */

	static void DeleteMemberFunc(ItclMemberFunc mfunc) // ref to member function
	// definition
	{
		if (mfunc != null) {
			Class.DeleteMember(mfunc.member);

			if (mfunc.arglist != null) {
				Methods.DeleteArgList(mfunc.arglist);
			}
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_CreateMemberCode -> Methods.CreateMemberCode
	 * 
	 * Creates the data record representing the implementation behind a class
	 * member function. This includes the argument list and the body of the
	 * function. If the body is of the form "@name", then it is treated as a
	 * label for a C procedure registered by Itcl_RegisterC().
	 * 
	 * The implementation is kept by the member function definition, and
	 * controlled by a preserve/release paradigm. That way, if it is in use
	 * while it is being redefined, it will stay around long enough to avoid a
	 * core dump.
	 * 
	 * If any errors are encountered, this procedure raises a TclException.
	 * Otherwise, it returns a new ItclMemberCode reference.
	 * ------------------------------------------------------------------------
	 */

	static ItclMemberCode CreateMemberCode(Interp interp, // interpreter
			// managing this
			// action
			ItclClass cdefn, // class containing this member
			String arglist, // space-separated list of arg names
			String body) // body of commands for the method
			throws TclException {
		ItclMemberCode mcode;

		// Allocate some space to hold the implementation.

		mcode = new ItclMemberCode();
		mcode.flags = 0;
		mcode.argcount = 0;
		mcode.arglist = null;
		mcode.proc = null;
		mcode.objCmd = null;
		// mcode.clientData = null;

		if (arglist != null) {
			CreateArgListResult cr;

			try {
				cr = Methods.CreateArgList(interp, arglist);
			} catch (TclException ex) {
				Methods.DeleteMemberCode(mcode);
				throw ex;
			}
			mcode.argcount = cr.argcount;
			mcode.arglist = cr.arglist;
			mcode.flags |= ItclInt.ARG_SPEC;
		} else {
			// No-op
		}

		// NOTE: Don't bother creating a Procedure object here,
		// just eval() the command body later.

		// Create a Tcl Procedure object for this code body.
		// This Procedure will not actually be used to push
		// a call frame or setup locals before code is
		// evaluated in a procedures scope.

		// String proc_name = "itcl_member_code";
		// TclObject proc_body;

		if (body != null) {
			// proc_body = TclString.newInstance(body);
			mcode.body = body;
		} else {
			// proc_body = TclString.newInstance("");
			mcode.body = null;
		}

		mcode.proc = null;

		// Note: Skipped compiled locals processing.

		// If the body definition starts with '@', then treat the value
		// as a symbolic name for a C procedure.

		if (body == null) {
			mcode.flags |= ItclInt.IMPLEMENT_NONE;
		} else if (body.length() >= 2 && body.charAt(0) == '@') {
			String rbody = body.substring(1);
			ItclJavafunc jfunc = Linkage.FindC(interp, rbody);

			if (jfunc == null) {
				Methods.DeleteMemberCode(mcode);
				throw new TclException(interp,
						"no registered C procedure with name \"" + rbody + "\"");
			} else {
				mcode.flags = ItclInt.IMPLEMENT_OBJCMD;
				mcode.objCmd = jfunc.objCmdProc;
			}
		}
		// Otherwise, treat the body as a chunk of Tcl code.
		else {
			mcode.flags |= ItclInt.IMPLEMENT_TCL;
		}

		return mcode;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Methods.IsMemberCodeImplemented
	 * 
	 * Return true if the "body" for a given command has been implemented.
	 * ------------------------------------------------------------------------
	 */

	static boolean IsMemberCodeImplemented(ItclMemberCode mcode) {
		return ((mcode.flags & ItclInt.IMPLEMENT_NONE) == 0);
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_DeleteMemberCode -> Methods.DeleteMemberCode
	 * 
	 * Destroys all data associated with the given command implementation.
	 * Invoked automatically by Util.ReleaseData() when the implementation is no
	 * longer being used.
	 * ------------------------------------------------------------------------
	 */

	static void DeleteMemberCode(ItclMemberCode mcode) // ref to member function
	// definition
	{
		if (mcode.arglist != null) {
			Methods.DeleteArgList(mcode.arglist);
		}
		if (mcode.proc != null) {
			mcode.proc = null;
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_GetMemberCode -> Methods.GetMemberCode
	 * 
	 * Makes sure that the implementation for an [incr Tcl] code body is ready
	 * to run. Note that a member function can be declared without being
	 * defined. The class definition may contain a declaration of the member
	 * function, but its body may be defined in a separate file. If an undefined
	 * function is encountered, this routine automatically attempts to autoload
	 * it. If the body is implemented via Tcl code, then it is compiled here as
	 * well.
	 * 
	 * Raises a TclException if an error is encountered, or if the
	 * implementation is not defined and cannot be autoloaded. Returns if
	 * implementation is ready to use.
	 * ------------------------------------------------------------------------
	 */

	static void GetMemberCode(Interp interp, // interpreter managing this action
			ItclMember member) // member containing code body
			throws TclException {
		ItclMemberCode mcode = member.code;

		int result;

		// If the implementation has not yet been defined, try to
		// autoload it now.

		if (!IsMemberCodeImplemented(mcode)) {
			try {
				interp.eval("::auto_load " + member.fullname);
			} catch (TclException ex) {
				interp.addErrorInfo("\n    (while autoloading code for \""
						+ member.fullname + "\")");
				throw ex;
			}
			interp.resetResult(); // get rid of 1/0 status
		}

		// If the implementation is still not available, then
		// autoloading must have failed.
		//
		// TRICKY NOTE: If code has been autoloaded, then the
		// old mcode pointer is probably invalid. Go back to
		// the member and look at the current code pointer again.

		mcode = member.code;

		if (!IsMemberCodeImplemented(mcode)) {
			throw new TclException(interp, "member function \""
					+ member.fullname
					+ "\" is not defined and cannot be autoloaded");
		}

		// Skip compiling Tcl code for constructor or body
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_EvalMemberCode -> Methods.EvalMemberCode
	 * 
	 * Used to execute an ItclMemberCode representation of a code fragment. This
	 * code may be a body of Tcl commands, or a Java handler procedure.
	 * 
	 * Executes the command with the given objv arguments.
	 * ------------------------------------------------------------------------
	 */

	static void EvalMemberCode(Interp interp, // current interpreter
			ItclMemberFunc mfunc, // member func, or null (for error messages)
			ItclMember member, // command member containing code
			ItclObject contextObj, // object context, or null
			TclObject[] objv) // argument objects
			throws TclException {
		CallFrame oldFrame = null;

		boolean transparent;
		ItclObjectInfo info;
		ItclMemberCode mcode;
		ItclContext context;
		CallFrame frame, transFrame;

		// If this code does not have an implementation yet, then
		// try to autoload one. Also, if this is Tcl code, make sure
		// that it's compiled and ready to use.

		GetMemberCode(interp, member);
		mcode = member.code;

		// Bump the reference count on this code, in case it is
		// redefined or deleted during execution.

		// FIXME: It is possible that bumping this ref could be
		// in error if this function fails before entring the
		// try block below. The C version seems to have a problem
		// here too. Could this be moved into the try block?

		Util.PreserveData(mcode);

		// Install a new call frame context for the current code.
		// If the current call frame is marked as "transparent", then
		// do an "uplevel" operation to move past it. Transparent
		// call frames are installed by Itcl_HandleInstance. They
		// provide a way of entering an object context without
		// interfering with the normal call stack.

		transparent = false;

		info = member.classDefn.info;
		frame = Migrate.GetCallFrame(interp, 0);
		for (int i = Util.GetStackSize(info.transparentFrames) - 1; i >= 0; i--) {
			transFrame = (CallFrame) Util.GetStackValue(info.transparentFrames,
					i);

			if (frame == transFrame) {
				transparent = true;
				break;
			}
		}

		if (transparent) {
			frame = Migrate.GetCallFrame(interp, 1);
			oldFrame = Migrate.ActivateCallFrame(interp, frame);
		}

		context = new ItclContext(interp);
		Methods.PushContext(interp, member, member.classDefn, contextObj,
				context);

		try { // start try block that releases context

			// If this is a method with a Tcl implementation, or a
			// constructor with initCode, then parse its arguments now.

			if (mfunc != null && objv.length > 0) {
				if ((mcode.flags & ItclInt.IMPLEMENT_TCL) != 0
						|| ((member.flags & ItclInt.CONSTRUCTOR) != 0 && (member.classDefn.initCode != null))) {
					AssignArgs(interp, objv, mfunc);
				}
			}

			// If this code is a constructor, and if it is being invoked
			// when an object is first constructed (i.e., the "constructed"
			// table is still active within the object), then handle the
			// "initCode" associated with the constructor and make sure that
			// all base classes are properly constructed.
			//
			// TRICKY NOTE:
			// The "initCode" must be executed here. This is the only
			// opportunity where the arguments of the constructor are
			// available in a call frame.

			if ((member.flags & ItclInt.CONSTRUCTOR) != 0 && contextObj != null
					&& contextObj.constructed != null) {

				ConstructBase(interp, contextObj, member.classDefn);
			}

			// Execute the code body...

			if ((mcode.flags & ItclInt.IMPLEMENT_OBJCMD) != 0) {
				// FIXME: Need to handle unexpected return results
				// via the interp somehow.
				// FIXME: we are not firing any execution traces here,
				// because I don't see a WrappedCommand - Dan Bodoh 9/23/10
				mcode.objCmd.cmdProc(interp, objv);
			} else if ((mcode.flags & ItclInt.IMPLEMENT_ARGCMD) != 0) {
				throw new TclRuntimeError("unexpected IMPLEMENT_ARGCMD");
			} else if ((mcode.flags & ItclInt.IMPLEMENT_TCL) != 0) {
				interp.eval(mcode.body);
			} else {
				throw new TclRuntimeError("bad implementation flag for "
						+ member.fullname);
			}

			// If this is a constructor or destructor, and if it is being
			// invoked at the appropriate time, keep track of which methods
			// have been called. This information is used to implicitly
			// invoke constructors/destructors as needed.

			if ((member.flags & ItclInt.DESTRUCTOR) != 0 && contextObj != null
					&& contextObj.destructed != null) {

				contextObj.destructed.put(member.classDefn.name, "");
			}
			if ((member.flags & ItclInt.CONSTRUCTOR) != 0 && contextObj != null
					&& contextObj.constructed != null) {

				contextObj.constructed.put(member.classDefn.name, "");
			}

		} finally { // end try block that releases context
			Methods.PopContext(interp, context);

			if (transparent) {
				Migrate.ActivateCallFrame(interp, oldFrame);
			}

			Util.ReleaseData(mcode);
		}

		return;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_CreateArgList -> Methods.CreateArgList
	 * 
	 * Parses a Tcl list representing an argument declaration and returns a
	 * linked list of CompiledLocal values. Usually invoked as part of
	 * Itcl_CreateMemberFunc() when a new method or procedure is being defined.
	 * ------------------------------------------------------------------------
	 */

	static CreateArgListResult CreateArgList(Interp interp, // interpreter
			// managing this
			// function
			String decl) // string representing argument list
			throws TclException {
		int argc = 0;
		CompiledLocal local, last;
		CompiledLocal retLocal;
		TclObject[] argv, fargv;

		retLocal = last = null;

		try {

			if (decl != null) {
				argv = TclList.getElements(interp, TclString.newInstance(decl));
				argc = argv.length;

				for (int i = 0; i < argv.length; i++) {
					fargv = TclList.getElements(interp, argv[i]);
					local = null;

					if (fargv.length == 0 || fargv[0].toString().length() == 0) {
						throw new TclException(interp, "argument #" + i
								+ " has no name");
					} else if (fargv.length > 2) {
						throw new TclException(interp,
								"too many fields in argument specifier \""
										+ argv[i] + "\"");
					} else if (fargv[0].toString().indexOf("::") != -1) {
						throw new TclException(interp, "bad argument name \""
								+ fargv[0] + "\"");
					} else if (fargv.length == 1) {
						local = CreateArg(fargv[0].toString(), null);
					} else {
						local = CreateArg(fargv[0].toString(), fargv[1]
								.toString());
					}

					if (local != null) {
						// local.frameIndex = i;

						if (retLocal == null) {
							retLocal = last = local;
						} else {
							last.next = local;
							last = local;
						}
					}
					// ckfree(fargv);
				}
				// ckfree(argv);
			}

		} catch (TclException ex) {
			// If anything went wrong, destroy whatever arguments were
			// created and rethrow the TclException

			DeleteArgList(retLocal);
			throw ex;
		}

		CreateArgListResult res = new CreateArgListResult();
		res.arglist = retLocal;
		res.argcount = argc;

		return res;
	}

	public static class CreateArgListResult {
		int argcount;
		CompiledLocal arglist;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_CreateArg -> Methods.CreateArg
	 * 
	 * Creates a new Tcl Arg structure and fills it with the given information.
	 * Returns a pointer to the new Arg structure.
	 * ------------------------------------------------------------------------
	 */

	static CompiledLocal CreateArg(String name, // name of new argument
			String init) // initial value
	{
		CompiledLocal local = null;

		local = new CompiledLocal();

		local.next = null;
		// localPtr->flags = VAR_SCALAR | VAR_ARGUMENT;

		if (init != null) {
			local.defValue = TclString.newInstance(init);
			local.defValue.preserve();
		} else {
			local.defValue = null;
		}

		local.name = name;
		return local;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_DeleteArgList -> Methods.DeleteArgList
	 * 
	 * Destroys a chain of arguments acting as an argument list. Usually invoked
	 * when a method/proc is being destroyed, to discard its argument list.
	 * ------------------------------------------------------------------------
	 */

	static void DeleteArgList(CompiledLocal arglist) // first argument in arg
	// list chain
	{
		CompiledLocal local, next;

		for (local = arglist; local != null; local = next) {
			if (local.defValue != null) {
				local.defValue.release();
				local.defValue = null;
			}
			local.name = null;
			next = local.next;
			local.next = null;
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ArgList -> Methods.ArgList
	 * 
	 * Returns a TclObject containing the string representation for the given
	 * argument list.
	 * ------------------------------------------------------------------------
	 */

	static TclObject ArgList(int argc, // number of arguments
			CompiledLocal arglist) // first argument in arglist
	{
		String val;
		TclObject obj;
		StringBuffer buffer;
		buffer = new StringBuffer(64);

		while (arglist != null && argc-- > 0) {
			if (arglist.defValue != null) {
				val = arglist.defValue.toString();

				Util.StartSublist(buffer);
				Util.AppendElement(buffer, arglist.name);
				Util.AppendElement(buffer, val);
				Util.EndSublist(buffer);
			} else {
				Util.AppendElement(buffer, arglist.name);
			}
			arglist = arglist.next;
		}

		obj = TclString.newInstance(buffer.toString());
		return obj;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_EquivArgLists -> Methods.EquivArgLists
	 * 
	 * Compares two argument lists to see if they are equivalent. The first list
	 * is treated as a prototype, and the second list must match it. Argument
	 * names may be different, but they must match in meaning. If one argument
	 * is optional, the corresponding argument must also be optional. If the
	 * prototype list ends with the magic "args" argument, then it matches
	 * everything in the other list.
	 * 
	 * Returns true if the argument lists are equivalent.
	 * ------------------------------------------------------------------------
	 */

	static boolean EquivArgLists(CompiledLocal arg1, // prototype argument list
			int arg1c, // number of args in prototype arg list
			CompiledLocal arg2, // another argument list to match against
			int arg2c) // number of args in matching list
	{
		String dval1, dval2;

		while (arg1 != null && arg1c > 0 && arg2 != null && arg2c > 0) {
			// If the prototype argument list ends with the magic "args"
			// argument, then it matches everything in the other list.

			if (arg1c == 1 && arg1.name.equals("args")) {
				return true;
			}

			// If one has a default value, then the other must have the
			// same default value.

			if (arg1.defValue != null) {
				if (arg2.defValue == null) {
					return false;
				}

				dval1 = arg1.defValue.toString();
				dval2 = arg2.defValue.toString();
				if (!dval1.equals(dval2)) {
					return false;
				}
			} else if (arg2.defValue != null) {
				return false;
			}

			arg1 = arg1.next;
			arg1c--;
			arg2 = arg2.next;
			arg2c--;
		}
		if (arg1c == 1 && arg1.name.equals("args")) {
			return true;
		}
		return (arg1c == 0 && arg2c == 0);
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_GetMemberFuncUsage -> Methods.GetMemberFuncUsage
	 * 
	 * Returns a string showing how a command member should be invoked. If the
	 * command member is a method, then the specified object name is reported as
	 * part of the invocation path:
	 * 
	 * obj method arg ?arg arg ...?
	 * 
	 * Otherwise, the "obj" pointer is ignored, and the class name is used as
	 * the invocation path:
	 * 
	 * class::proc arg ?arg arg ...?
	 * 
	 * Returns the string by appending it onto the TclObject passed in as an
	 * argument.
	 * ------------------------------------------------------------------------
	 */

	static void GetMemberFuncUsage(ItclMemberFunc mfunc, // command member being
			// examined
			ItclObject contextObj, // invoked with respect to this object
			StringBuffer buffer) // returns: string showing usage
	{
		int argcount;
		String name;
		CompiledLocal arglist, arg;
		ItclMemberFunc mf;
		ItclClass cdefn;

		// If the command is a method and an object context was
		// specified, then add the object context. If the method
		// was a constructor, and if the object is being created,
		// then report the invocation via the class creation command.

		if ((mfunc.member.flags & ItclInt.COMMON) == 0) {
			if ((mfunc.member.flags & ItclInt.CONSTRUCTOR) != 0
					&& contextObj.constructed != null) {

				cdefn = contextObj.classDefn;
				mf = (ItclMemberFunc) cdefn.resolveCmds.get("constructor");

				if (mf == mfunc) {
					String fname = contextObj.classDefn.interp
							.getCommandFullName(contextObj.classDefn.w_accessCmd);
					buffer.append(fname);
					buffer.append(" ");
					name = contextObj.classDefn.interp
							.getCommandName(contextObj.w_accessCmd);
					buffer.append(name);
				} else {
					buffer.append(mfunc.member.fullname);
				}
			} else if (contextObj != null && contextObj.accessCmd != null) {
				name = contextObj.classDefn.interp
						.getCommandName(contextObj.w_accessCmd);
				buffer.append(name);
				buffer.append(" ");
				buffer.append(mfunc.member.name);
			} else {
				buffer.append("<object> ");
				buffer.append(mfunc.member.name);
			}
		} else {
			buffer.append(mfunc.member.fullname);
		}

		// Add the argument usage info.

		if (mfunc.member.code != null) {
			arglist = mfunc.member.code.arglist;
			argcount = mfunc.member.code.argcount;
		} else if (mfunc.arglist != null) {
			arglist = mfunc.arglist;
			argcount = mfunc.argcount;
		} else {
			arglist = null;
			argcount = 0;
		}

		if (arglist != null) {
			for (arg = arglist; arg != null && argcount > 0; arg = arg.next, argcount--) {

				if (argcount == 1 && arg.name.equals("args")) {
					buffer.append(" ?arg arg ...?");
				} else if (arg.defValue != null) {
					buffer.append(" ?");
					buffer.append(arg.name);
					buffer.append("?");
				} else {
					buffer.append(" ");
					buffer.append(arg.name);
				}
			}
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ExecMethod -> Methods.ExecMethod.cmdProc
	 * 
	 * Invoked by Tcl to handle the execution of a user-defined method. A method
	 * is similar to the usual Tcl proc, but has access to object-specific data.
	 * If for some reason there is no current object context, then a method call
	 * is inappropriate, and an error is returned.
	 * 
	 * Methods are implemented either as Tcl code fragments, or as Java-coded
	 * procedures. For Tcl code fragments, command arguments are parsed
	 * according to the argument list, and the body is executed in the scope of
	 * the class where it was defined. For Java procedures, the arguments are
	 * passed in "as-is", and the procedure is executed in the most-specific
	 * class scope.
	 * ------------------------------------------------------------------------
	 */
	public static class ExecMethod implements CommandWithDispose {
		final ItclMemberFunc mfunc;

		ExecMethod(ItclMemberFunc mfunc) {
			if (mfunc == null) {
				throw new NullPointerException();
			}
			this.mfunc = mfunc;
		}

		public void disposeCmd() {
			Util.ReleaseData(mfunc);
		}

		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			ItclMemberFunc mfunc = this.mfunc;
			ItclMember member = mfunc.member;

			String token;
			ItclClass contextClass;
			ItclObject contextObj;

			// Make sure that the current namespace context includes an
			// object that is being manipulated. Methods can be executed
			// only if an object context exists.

			Methods.GetContextResult gcr = Methods.GetContext(interp);
			contextClass = gcr.cdefn;
			contextObj = gcr.odefn;

			if (contextObj == null) {
				throw new TclException(interp,
						"cannot access object-specific info without an object context");
			}

			// Make sure that this command member can be accessed from
			// the current namespace context.

			if (mfunc.member.protection != Itcl.PUBLIC) {
				Namespace contextNs = Util.GetTrueNamespace(interp,
						contextClass.info);

				if (!Util.CanAccessFunc(mfunc, contextNs)) {
					throw new TclException(interp, "can't access \""
							+ member.fullname + "\": "
							+ Util.ProtectionStr(member.protection)
							+ " function");
				}
			}

			// All methods should be "virtual" unless they are invoked with
			// a "::" scope qualifier.
			//
			// To implement the "virtual" behavior, find the most-specific
			// implementation for the method by looking in the "resolveCmds"
			// table for this class.

			token = objv[0].toString();
			if (token.indexOf("::") == -1) {
				ItclMemberFunc tmp = (ItclMemberFunc) contextObj.classDefn.resolveCmds
						.get(member.name);
				if (tmp != null) {
					mfunc = tmp;
					member = mfunc.member;
				}
			}

			// Execute the code for the method. Be careful to protect
			// the method in case it gets deleted during execution.

			Util.PreserveData(mfunc);

			try {
				Methods.EvalMemberCode(interp, mfunc, member, contextObj, objv);
			} catch (TclException ex) {
				Methods.ReportFuncErrors(interp, mfunc, contextObj, ex);
			} finally {
				Util.ReleaseData(mfunc);
			}
		}
	} // end class ExecMethod

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ExecProc -> Methods.ExecProc.cmdProc
	 * 
	 * Invoked by Tcl to handle the execution of a user-defined proc.
	 * 
	 * Procs are implemented either as Tcl code fragments, or as Java-coded
	 * procedures. For Tcl code fragments, command arguments are parsed
	 * according to the argument list, and the body is executed in the scope of
	 * the class where it was defined. For Java procedures, the arguments are
	 * passed in "as-is", and the procedure is executed in the most-specific
	 * class scope.
	 * ------------------------------------------------------------------------
	 */
	public static class ExecProc implements CommandWithDispose {
		ItclMemberFunc mfunc;

		ExecProc(ItclMemberFunc mfunc) {
			this.mfunc = mfunc;
		}

		public void disposeCmd() {
			Util.ReleaseData(mfunc);
		}

		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			ItclMember member = mfunc.member;

			// Make sure that this command member can be accessed from
			// the current namespace context.

			if (mfunc.member.protection != Itcl.PUBLIC) {
				Namespace contextNs = Util.GetTrueNamespace(interp,
						mfunc.member.classDefn.info);

				if (!Util.CanAccessFunc(mfunc, contextNs)) {
					throw new TclException(interp, "can't access \""
							+ member.fullname + "\": "
							+ Util.ProtectionStr(member.protection)
							+ " function");
				}
			}

			// Execute the code for the proc. Be careful to protect
			// the proc in case it gets deleted during execution.

			Util.PreserveData(mfunc);

			try {
				Methods.EvalMemberCode(interp, mfunc, member, null, objv);
			} catch (TclException ex) {
				Methods.ReportFuncErrors(interp, mfunc, null, ex);
			} finally {
				Util.ReleaseData(mfunc);
			}
		}
	} // end class ExecProc

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_PushContext -> Methods.PushContext
	 * 
	 * Sets up the class/object context so that a body of [incr Tcl] code can be
	 * executed. This procedure pushes a call frame with the proper namespace
	 * context for the class. If an object context is supplied, the object's
	 * instance variables are integrated into the call frame so they can be
	 * accessed as local variables. Returns if successful, raises TclException
	 * if something goes wrong.
	 * ------------------------------------------------------------------------
	 */

	static void PushContext(Interp interp, // interpreter managing this body of
			// code
			ItclMember member, // member containing code body
			ItclClass contextClass, // class context
			ItclObject contextObj, // object context, or null
			ItclContext context) // storage space for class/object context
			throws TclException {
		CallFrame frame = context.frame;

		int localCt, newEntry;
		ItclMemberCode mcode;
		Procedure proc;

		// Activate the call frame. If this fails, we'll bail out
		// before allocating any resources.
		//
		// NOTE: Always push a call frame that looks like a proc.
		// This causes global variables to be handled properly
		// inside methods/procs.

		Namespace.pushCallFrame(interp, frame, contextClass.namesp, true);

		context.classDefn = contextClass;
		// context.compiledLocals = new Var[20];

		// If this is an object context, register it in a hash table
		// of all known contexts. We'll need this later if we
		// call Itcl_GetContext to get the object context for the
		// current call frame.

		if (contextObj != null) {
			contextClass.info.contextFrames.put(frame, contextObj);
			Util.PreserveData(contextObj);
		}

		// Set up the compiled locals in the call frame and assign
		// argument variables.

		if (member != null) {
			// mcode = member.code;
			// proc = mcode.proc;

			// If there are too many compiled locals to fit in the default
			// storage space for the context, then allocate more space.

			// localCt = proc.numCompiledLocals; // C impl
			// localCt = mcode.argcount; // Jacl Procedure has no
			// numCompiledLocals
			// if (localCt > context.compiledLocals.length) {
			// context.compiledLocals = new Var[localCt];
			// }

			// Initialize and resolve compiled variable references.
			// Class variables will have special resolution rules.
			// In that case, we call their "resolver" procs to get our
			// hands on the variable, and we make the compiled local a
			// link to the real variable.

			// frame.proc = proc;
			// frame.numCompiledLocals = localCt;
			// frame.compiledLocals = context.compiledLocals;

			// This method will plug Var objects into the
			// compiled local list that starts at frame.proc.firstLocal.
			// It makes use of the type flags for each CompiledLocal
			// and resolves to actual Var references. Not clear if this
			// is something we want for this port.

			// TclInitCompiledLocals(interp, framePtr,
			// (Namespace*)contextClass->namesp);
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_PopContext -> Methods.PopContext
	 * 
	 * Removes a class/object context previously set up by PushContext. Usually
	 * called after an [incr Tcl] code body has been executed, to clean up.
	 * ------------------------------------------------------------------------
	 */

	static void PopContext(Interp interp, // interpreter managing this body of
			// code
			ItclContext context) // storage space for class/object context
	{
		CallFrame frame;
		ItclObjectInfo info;
		ItclObject contextObj;

		// See if the current call frame has an object context
		// associated with it. If so, release the claim on the
		// object info.

		frame = Migrate.GetCallFrame(interp, 0);
		info = context.classDefn.info;

		contextObj = (ItclObject) info.contextFrames.get(frame);
		if (contextObj != null) {
			Util.ReleaseData(contextObj);
			info.contextFrames.remove(frame);
		}

		// Remove the call frame.

		Namespace.popCallFrame(interp);

		// Release compiledLocals

		// context.compiledLocals = null;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_GetContext -> Methods.GetContext
	 * 
	 * Convenience routines for looking up the current object/class context.
	 * Useful in implementing methods/procs to see what class or what object, is
	 * active.
	 * 
	 * Returns the current class and or object. Raises a TclException if a class
	 * namespace is not active.
	 * ------------------------------------------------------------------------
	 */

	static GetContextResult GetContext(Interp interp) // current interpreter
			throws TclException {
		Namespace activeNs = Namespace.getCurrentNamespace(interp);
		ItclObjectInfo info;
		CallFrame frame;
		ItclClass cdefn;
		ItclObject odefn;

		// Return null for anything that cannot be found.

		cdefn = null;
		odefn = null;

		// If the active namespace is a class namespace, then return
		// all known info. See if the current call frame is a known
		// object context, and if so, return that context.

		if (Class.IsClassNamespace(activeNs)) {
			cdefn = Class.GetClassFromNamespace(activeNs);

			frame = Migrate.GetCallFrame(interp, 0);

			info = cdefn.info;
			odefn = (ItclObject) info.contextFrames.get(frame);

			return new GetContextResult(cdefn, odefn);
		}

		// If there is no class/object context, return an error message.

		throw new TclException(interp, "namespace \"" + activeNs.fullName
				+ "\" is not a class namespace");
	}

	public static class GetContextResult {
		ItclClass cdefn;
		ItclObject odefn;

		public GetContextResult(ItclClass cdefn, ItclObject odefn) {
			this.cdefn = cdefn;
			this.odefn = odefn;
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_AssignArgs -> Methods.AssignArgs
	 * 
	 * Matches a list of arguments against a Tcl argument specification.
	 * Supports all of the rules regarding arguments for Tcl procs, including
	 * default arguments and variable-length argument lists.
	 * 
	 * Assumes that a local call frame is already installed. As variables are
	 * successfully matched, they are stored as variables in the call frame.
	 * Raises a TclException if anything goes wrong.
	 * ------------------------------------------------------------------------
	 */

	static void AssignArgs(Interp interp, // interpreter
			TclObject[] objv, // argument objects
			ItclMemberFunc mfunc) // member function info (for error messages)
			throws TclException {
		ItclMemberCode mcode = mfunc.member.code;

		int defargc;
		String[] defargv = null;
		TclObject[] defobjv = null;
		int configc = 0;
		ItclVarDefn[] configVars = null;
		String[] configVals = null;

		int vi, argsLeft;
		ItclClass contextClass;
		ItclObject contextObj;
		CompiledLocal arg;
		CallFrame frame;
		Var var;
		TclObject obj, list;
		String value;
		int objvi, objc;
		ParseConfigResult pcr = null;

		frame = Migrate.GetCallFrame(interp, 0);
		ItclAccess.setCallFrameObjv(frame, objv); // ref counts for args are
		// incremented below

		// See if there is a current object context. We may need
		// it later on.

		try {
			Methods.GetContextResult gcr = Methods.GetContext(interp);
			contextClass = gcr.cdefn;
			contextObj = gcr.odefn;
		} catch (TclException ex) {
			contextClass = null;
			contextObj = null;
		}
		interp.resetResult();

		// Match the actual arguments against the procedure's formal
		// parameters to compute local variables.

		// varPtr = framePtr->compiledLocals;

		try { // start of argErrors: finally block

			for (argsLeft = mcode.argcount, arg = mcode.arglist, objvi = 1, objc = objv.length - 1; argsLeft > 0; arg = arg.next, argsLeft--, /*
																																			 * varPtr++
																																			 * ,
																																			 */objvi++, objc--) {
				// if (!TclIsVarArgument(argPtr)) {
				// Tcl_Panic("local variable %s is not argument but should be",
				// argPtr->name);
				// return TCL_ERROR;
				// }
				// if (TclIsVarTemporary(argPtr)) {
				// Tcl_Panic("local variable is temporary but should be an argument");
				// return TCL_ERROR;
				// }

				// Handle the special case of the last formal being "args".
				// When it occurs, assign it a list consisting of all the
				// remaining actual arguments.

				if ((argsLeft == 1) && arg.name.equals("args")) {
					// listPtr = Tcl_NewListObj(objc, objv);
					// varPtr->value.objPtr = listPtr;
					// Tcl_IncrRefCount(listPtr); /* local var is a reference */
					// varPtr->flags &= ~VAR_UNDEFINED;
					// objc = 0;

					if (objc < 0)
						objc = 0;

					list = TclList.newInstance();
					for (int i = objvi; i < (objvi + objc); i++) {
						TclList.append(interp, list, objv[i]);
					}
					AssignLocal(interp, "args", list, frame);
					objc = 0;
					break;
				}

				// Handle the special case of the last formal being "config".
				// When it occurs, treat all remaining arguments as public
				// variable assignments. Set the local "config" variable
				// to the list of public variables assigned.

				else if ((argsLeft == 1) && arg.name.equals("config")
						&& contextObj != null) {
					// If this is not an old-style method, discourage against
					// the use of the "config" argument.

					if ((mfunc.member.flags & ItclInt.OLD_STYLE) == 0) {
						throw new TclException(
								interp,
								"\"config\" argument is an anachronism\n"
										+ "[incr Tcl] no longer supports the \"config\" argument.\n"
										+ "Instead, use the \"args\" argument and then use the\n"
										+ "built-in configure method to handle args like this:\n"
										+ "  eval configure $args");
					}

					// Otherwise, handle the "config" argument in the usual
					// way...
					// - parse all "-name value" assignments
					// - set "config" argument to the list of variable names

					if (objc > 0) { // still have some arguments left?

						pcr = ParseConfig(interp, objc, objv, objvi, contextObj);
						configc = pcr.num_variables;
						configVars = pcr.variables;
						configVals = pcr.values;

						list = TclList.newInstance();
						for (vi = 0; vi < configc; vi++) {
							StringBuffer buffer = new StringBuffer(64);
							buffer.append(configVars[vi].member.classDefn.name);
							buffer.append("::");
							buffer.append(configVars[vi].member.name);
							obj = TclString.newInstance(buffer.toString());
							TclList.append(interp, list, obj);
						}

						// varPtr->value.objPtr = listPtr;
						// Tcl_IncrRefCount(listPtr); // local var is a
						// reference
						// varPtr->flags &= ~VAR_UNDEFINED;

						// FIXME: is setting a local named "config" correct?
						AssignLocal(interp, arg.name, list, frame);
						objc = 0; // all remaining args handled
					}

					else if (arg.defValue != null) {
						// value = arg.defValue.toString();
						// defargv = null;
						// defargc = 0;
						defobjv = TclList.getElements(interp, arg.defValue);
						defargc = defobjv.length;

						for (vi = 0; vi < defargc; vi++) {
							defobjv[vi].preserve();
						}

						pcr = ParseConfig(interp, defargc, defobjv, 0,
								contextObj);
						configc = pcr.num_variables;
						configVars = pcr.variables;
						configVals = pcr.values;

						list = TclList.newInstance();
						for (vi = 0; vi < configc; vi++) {
							StringBuffer buffer = new StringBuffer(64);
							buffer.append(configVars[vi].member.classDefn.name);
							buffer.append("::");
							buffer.append(configVars[vi].member.name);

							obj = TclString.newInstance(buffer.toString());
							TclList.append(interp, list, obj);
						}

						// varPtr->value.objPtr = listPtr;
						// Tcl_IncrRefCount(listPtr); // local var is a
						// reference
						// varPtr->flags &= ~VAR_UNDEFINED;
						AssignLocal(interp, arg.name, list, frame);
					} else {
						// objPtr = Tcl_NewStringObj("", 0);
						// varPtr->value.objPtr = objPtr;
						// Tcl_IncrRefCount(objPtr); /* local var is a reference
						// */
						// varPtr->flags &= ~VAR_UNDEFINED;
						obj = TclString.newInstance("");
						AssignLocal(interp, arg.name, obj, frame);
					}
				}

				// Resume the usual processing of arguments...

				else if (objc > 0) { // take next arg as value
					// objPtr = *objv;
					// varPtr->value.objPtr = objPtr;
					// varPtr->flags &= ~VAR_UNDEFINED;
					// Tcl_IncrRefCount(objPtr); // local var is a reference
					obj = objv[objvi];
					AssignLocal(interp, arg.name, obj, frame);
				} else if (arg.defValue != null) { // ...or use default value
					// objPtr = argPtr->defValuePtr;
					// varPtr->value.objPtr = objPtr;
					// varPtr->flags &= ~VAR_UNDEFINED;
					// Tcl_IncrRefCount(objPtr); // local var is a reference
					obj = arg.defValue;
					AssignLocal(interp, arg.name, obj, frame);
				} else {
					if (mfunc != null) {
						StringBuffer buffer = new StringBuffer(64);
						buffer.append("wrong # args: should be \"");
						GetMemberFuncUsage(mfunc, contextObj, buffer);
						buffer.append("\"");
						throw new TclException(interp, buffer.toString());
					} else {
						throw new TclException(interp,
								"no value given for parameter \"" + arg.name
										+ "\"");
					}
				}
			}

			if (objc > 0) {
				if (mfunc != null) {
					StringBuffer buffer = new StringBuffer(64);
					buffer.append("wrong # args: should be \"");
					GetMemberFuncUsage(mfunc, contextObj, buffer);
					buffer.append("\"");
					throw new TclException(interp, buffer.toString());
				} else {
					throw new TclException(interp, "too many arguments");
				}
			}

			// Handle any "config" assignments.

			if (configc > 0) {
				HandleConfig(interp, pcr, contextObj);
			}

		} finally { // end of argErrors: finally block
			if (defobjv != null) {
				for (vi = 0; vi < defobjv.length; vi++) {
					defobjv[vi].release();
				}
			}
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * Methods.AssignLocal
	 * 
	 * Assign a local variable by adding a Var to the varTable for the current
	 * frame. This is needed so that we don't accidently overwrite common or
	 * instance vars while setting the values/defaults for arguments.
	 * ------------------------------------------------------------------------
	 */

	static void AssignLocal(Interp interp, // interpreter
			String name, // local variable name
			TclObject val, // value of variable
			CallFrame frame) // frame that contains the local varTable
			throws TclException {
		ItclAccess.assignLocalVar(interp, name, val, frame);
	}

	/*
	 * ------------------------------------------------------------------------
	 * ItclParseConfig -> Methods.ParseConfig
	 * 
	 * Parses a set of arguments as "-variable value" assignments. Interprets
	 * all variable names in the most-specific class scope, so that an inherited
	 * method with a "config" parameter will work correctly. Returns an objects
	 * containing the number of variables accessed, an array of public variable
	 * names, and their corresponding values. These values are passed to
	 * HandleConfig to perform assignments.
	 * ------------------------------------------------------------------------
	 */

	static ParseConfigResult ParseConfig(Interp interp, // interpreter
			int objc, // count of objects remaining (after objvIndex)
			TclObject[] objv, // argument objects
			int objvIndex, // index of first object in objv to consider
			ItclObject contextObj) // object whose public vars are being
			// config'd
			throws TclException {
		ItclVarLookup vlookup;
		String varName, value;

		int rargc; // return: number of variables accessed
		ItclVarDefn[] rvars; // return: list of variables
		String[] rvals; // return: list of values

		if (objc < 0)
			objc = 0;
		rargc = 0;
		rvars = new ItclVarDefn[objc];
		rvals = new String[objc];

		while (objc-- > 0) {
			// Next argument should be "-variable"

			varName = objv[objvIndex].toString();
			if (varName.length() < 2 || varName.charAt(0) != '-') {
				throw new TclException(interp,
						"syntax error in config assignment \"" + varName
								+ "\": should be \"-variable value\"");
			} else if (objc-- <= 0) {
				throw new TclException(
						interp,
						"syntax error in config assignment \""
								+ varName
								+ "\": should be \"-variable value\" (missing value)");
			}

			vlookup = (ItclVarLookup) contextObj.classDefn.resolveVars
					.get(varName.substring(1));

			if (vlookup != null) {
				value = objv[objvIndex + 1].toString();

				rvars[rargc] = vlookup.vdefn; // variable definition
				rvals[rargc] = value; // config value
				rargc++;
				objvIndex += 2;
			} else {
				throw new TclException(interp,
						"syntax error in config assignment \"" + varName
								+ "\": unrecognized variable");
			}
		}

		ParseConfigResult pcr = new ParseConfigResult();
		pcr.num_variables = rargc;
		pcr.variables = rvars;
		pcr.values = rvals;
		return pcr;
	}

	public static class ParseConfigResult {
		int num_variables;
		ItclVarDefn[] variables;
		String[] values;
	}

	/*
	 * ------------------------------------------------------------------------
	 * ItclHandleConfig -> Methods.HandleConfig
	 * 
	 * Handles the assignment of "config" values to public variables. The list
	 * of assignments is parsed in ParseConfig(), but the actual assignments are
	 * performed here. If the variables have any associated "config" code, it is
	 * invoked here as well. If errors are detected during assignment or
	 * "config" code execution, the variable is set back to its previous value
	 * and an exception is raised.
	 * 
	 * Raises a TclException if anything goes wrong.
	 * ------------------------------------------------------------------------
	 */

	static void HandleConfig(Interp interp, // interpreter currently in control
			ParseConfigResult pres, // assignments, variables, and values
			ItclObject contextObj) // object whose public vars are being
			// config'd
			throws TclException {
		TclObject valObj;
		String val;
		StringBuffer lastval;
		ItclContext context;
		CallFrame oldFrame, uplevelFrame;

		int argc = pres.num_variables;
		ItclVarDefn[] vars = pres.variables;
		String[] vals = pres.values;

		lastval = new StringBuffer(64);

		// All "config" assignments are performed in the most-specific
		// class scope, so that inherited methods with "config" arguments
		// will work correctly.

		context = new ItclContext(interp);
		Methods.PushContext(interp, null, contextObj.classDefn, contextObj,
				context);

		try {

			// Perform each assignment and execute the "config" code
			// associated with each variable. If any errors are encountered,
			// set the variable back to its previous value, and return an error.

			for (int i = 0; i < argc; i++) {
				valObj = interp.getVar(vars[i].member.fullname, 0);
				if (valObj == null) {
					val = "";
				} else {
					val = valObj.toString();
				}
				lastval.setLength(0);
				lastval.append(val);

				// Set the variable to the specified value.

				try {
					// FIXME: is this set going to change and of the
					// local variables or will it be effected by
					// local setting that happened?
					interp.setVar(vars[i].member.fullname, TclString
							.newInstance(vals[i]), 0);
				} catch (TclException ex) {
					interp
							.addErrorInfo("\n    (while configuring public variable \""
									+ vars[i].member.fullname + "\")");
					throw ex;
				}

				// If the variable has a "config" condition, then execute it.
				// If it fails, put the variable back the way it was and return
				// an error.
				//
				// TRICKY NOTE: Be careful to evaluate the code one level
				// up in the call stack, so that it's executed in the
				// calling context, and not in the context that we've
				// set up for public variable access.

				if (vars[i].member.code != null) {

					uplevelFrame = Migrate.GetCallFrame(interp, 1);
					oldFrame = Migrate.ActivateCallFrame(interp, uplevelFrame);

					TclException evalEx = null;

					try {
						Methods.EvalMemberCode(interp, null, vars[i].member,
								contextObj, null);
					} catch (TclException ex) {
						evalEx = ex;
					} finally {
						Migrate.ActivateCallFrame(interp, oldFrame);
					}

					if (evalEx != null) {
						interp
								.addErrorInfo("\n    (while configuring public variable \""
										+ vars[i].member.fullname + "\")");

						interp.setVar(vars[i].member.fullname, TclString
								.newInstance(lastval.toString()), 0);

						throw evalEx;
					}
				}
			}

		} finally {
			// Clean up before returning
			Methods.PopContext(interp, context);
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ConstructBase -> Methods.ConstructBase
	 * 
	 * Usually invoked just before executing the body of a constructor when an
	 * object is first created. This procedure makes sure that all base classes
	 * are properly constructed. If an "initCode" fragment was defined with the
	 * constructor for the class, then it is invoked. After that, the list of
	 * base classes is checked for constructors that are defined but have not
	 * yet been invoked. Each of these is invoked implicitly with no arguments.
	 * 
	 * Assumes that a local call frame is already installed, and that
	 * constructor arguments have already been matched and are sitting in this
	 * frame. Raises a TclException if anything goes wrong.
	 * ------------------------------------------------------------------------
	 */

	static void ConstructBase(Interp interp, // interpreter
			ItclObject contextObj, // object being constructed
			ItclClass contextClass) // current class being constructed
			throws TclException {
		Itcl_ListElem elem;
		ItclClass cdefn;

		// If the class has an "initCode", invoke it in the current context.
		//
		// TRICKY NOTE:
		// This context is the call frame containing the arguments
		// for the constructor. The "initCode" makes sense right
		// now--just before the body of the constructor is executed.

		if (contextClass.initCode != null) {
			interp.eval(contextClass.initCode.toString());
		}

		// Scan through the list of base classes and see if any of these
		// have not been constructed. Invoke base class constructors
		// implicitly, as needed. Go through the list of base classes
		// in reverse order, so that least-specific classes are constructed
		// first.

		elem = Util.LastListElem(contextClass.bases);
		while (elem != null) {
			cdefn = (ItclClass) Util.GetListValue(elem);

			if (contextObj.constructed.get(cdefn.name) == null) {

				Methods.InvokeMethodIfExists(interp, "constructor", cdefn,
						contextObj, null);

				// The base class may not have a constructor, but its
				// own base classes could have one. If the constructor
				// wasn't found in the last step, then other base classes
				// weren't constructed either. Make sure that all of its
				// base classes are properly constructed.

				if (cdefn.functions.get("constructor") == null) {
					Methods.ConstructBase(interp, contextObj, cdefn);
				}
			}
			elem = Util.PrevListElem(elem);
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_InvokeMethodIfExists -> Methods.InvokeMethodIfExists
	 * 
	 * Looks for a particular method in the specified class. If the method is
	 * found, it is invoked with the given arguments. Any protection level
	 * (protected/private) for the method is ignored. If the method does not
	 * exist, this procedure does nothing.
	 * 
	 * This procedure is used primarily to invoke the constructor/destructor
	 * when an object is created/destroyed.
	 * 
	 * Raises a TclException if anything goes wrong.
	 * ------------------------------------------------------------------------
	 */

	static void InvokeMethodIfExists(Interp interp, // interpreter
			String name, // name of desired method
			ItclClass contextClass, // current class being constructed
			ItclObject contextObj, // object being constructed
			TclObject[] objv) // argument objects, can be null
			throws TclException {
		ItclMemberFunc mfunc;
		ItclMember member;
		TclObject cmdline;
		TclObject[] cmdlinev;

		// Scan through the list of base classes and see if any of these
		// have not been constructed. Invoke base class constructors
		// implicitly, as needed. Go through the list of base classes
		// in reverse order, so that least-specific classes are constructed
		// first.

		mfunc = (ItclMemberFunc) contextClass.functions.get(name);

		if (mfunc != null) {
			member = mfunc.member;

			// Prepend the method name to the list of arguments.

			cmdline = Util.CreateArgs(interp, name, objv, 0);
			cmdlinev = TclList.getElements(interp, cmdline);

			// Execute the code for the method. Be careful to protect
			// the method in case it gets deleted during execution.

			Util.PreserveData(mfunc);

			try {
				EvalMemberCode(interp, mfunc, member, contextObj, cmdlinev);
			} catch (TclException ex) {
				ReportFuncErrors(interp, mfunc, contextObj, ex);
			} finally {
				Util.ReleaseData(mfunc);
			}
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ReportFuncErrors -> Methods.ReportFuncErrors
	 * 
	 * Used to interpret the status code returned when the body of a Tcl-style
	 * proc is executed. Handles the "errorInfo" and "errorCode" variables
	 * properly, and adds error information into the interpreter if anything
	 * went wrong. Returns a new status code that should be treated as the
	 * return status code for the command.
	 * 
	 * This same operation is usually buried in the Tcl InterpProc() procedure.
	 * It is defined here so that it can be reused more easily.
	 * ------------------------------------------------------------------------
	 */

	static void ReportFuncErrors(Interp interp, // interpreter being modified
			ItclMemberFunc mfunc, // command member that was invoked
			ItclObject contextObj, // object context for this command
			TclException exp) // TclException from proc body
			throws TclException {
		StringBuffer buffer;

		if (exp != null) {
			int code = exp.getCompletionCode();

			if (code == TCL.RETURN) {
				code = interp.updateReturnInfo();

				if (code != TCL.OK && code != TCL.ERROR) {
					interp.processUnexpectedResult(code);
				} else if (code != TCL.OK) {
					exp.setCompletionCode(code);
					throw exp;
				} else if (code == TCL.OK) {
					return;
				}
			} else if (code != TCL.ERROR) {
				exp.printStackTrace(System.out);
				throw new TclRuntimeError(
						"unexpected TclException completion code : " + code);
			}
			buffer = new StringBuffer(64);
			buffer.append("\n    ");

			if ((mfunc.member.flags & ItclInt.CONSTRUCTOR) != 0) {
				buffer.append("while constructing object \"");
				buffer.append(contextObj.classDefn.interp
						.getCommandFullName(contextObj.w_accessCmd));
				buffer.append("\" in ");
				buffer.append(mfunc.member.fullname);
				if ((mfunc.member.code.flags & ItclInt.IMPLEMENT_TCL) != 0) {
					buffer.append(" (");
				}
			} else if ((mfunc.member.flags & ItclInt.DESTRUCTOR) != 0) {
				buffer.append("while deleting object \"");
				buffer.append(contextObj.classDefn.interp
						.getCommandFullName(contextObj.w_accessCmd));
				buffer.append("\" in ");
				buffer.append(mfunc.member.fullname);
				if ((mfunc.member.code.flags & ItclInt.IMPLEMENT_TCL) != 0) {
					buffer.append(" (");
				}
			} else {
				buffer.append("(");

				if (contextObj != null && contextObj.accessCmd != null) {
					buffer.append("object \"");
					buffer.append(contextObj.classDefn.interp
							.getCommandFullName(contextObj.w_accessCmd));
					buffer.append("\" ");
				}

				if ((mfunc.member.flags & ItclInt.COMMON) != 0) {
					buffer.append("procedure");
				} else {
					buffer.append("method");
				}

				buffer.append(" \"");
				buffer.append(mfunc.member.fullname);
				buffer.append("\" ");
			}

			if ((mfunc.member.code.flags & ItclInt.IMPLEMENT_TCL) != 0) {
				buffer.append("body line ");
				buffer.append(interp.getErrorLine());
				buffer.append(")");
			} else {
				buffer.append(")");
			}

			interp.addErrorInfo(buffer.toString());
			throw exp;
		}
	}

} // end class Methods

// This class is like the CompiledLocal struct in the C version
// of Tcl. It is not really "compiled" in Jacl, but the name
// is the same.

class CompiledLocal {
	CompiledLocal next; // Next local var or null for last local.

	/*
	 * int flags; // Flag bits for the local variable. Same as // the flags for
	 * the Var structure above, // although only VAR_SCALAR, VAR_ARRAY, //
	 * VAR_LINK, VAR_ARGUMENT, VAR_TEMPORARY, and // VAR_RESOLVED make sense.
	 */

	TclObject defValue; // default argument value, null if not
	// and argument or no default.

	String name; // Name of local variable, can be null.
}
