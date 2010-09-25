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
 *  These procedures handle built-in class methods, including the
 *  "isa" method (to query hierarchy info) and the "info" method
 *  (to query class/object data).
 *
 * ========================================================================
 *  AUTHOR:  Michael J. McLennan
 *           Bell Labs Innovations for Lucent Technologies
 *           mmclennan@lucent.com
 *           http://www.tcltk.com/itcl
 *
 *     RCS:  $Id: BiCmds.java,v 1.3 2006/01/26 19:49:18 mdejong Exp $
 * ========================================================================
 *           Copyright (c) 1993-1998  Lucent Technologies, Inc.
 * ------------------------------------------------------------------------
 * See the file "license.itcl" for information on usage and redistribution
 * of this file, and for a DISCLAIMER OF ALL WARRANTIES.
 */

package tcl.pkg.itcl;

import java.util.Iterator;
import java.util.Map;

import tcl.lang.CallFrame;
import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.Namespace;
import tcl.lang.TCL;
import tcl.lang.TclException;
import tcl.lang.TclIndex;
import tcl.lang.TclList;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;
import tcl.lang.TclRuntimeError;
import tcl.lang.TclString;
import tcl.lang.WrappedCommand;

//
//  Standard list of built-in methods for all objects.
//
class BiMethod {
	String name; // method name
	String usage; // string describing usage
	String registration; // registration name for Java command
	Command proc; // implementation Java command

	BiMethod(String name, String usage, String registration, Command proc) {
		this.name = name;
		this.usage = usage;
		this.registration = registration;
		this.proc = proc;
	}
}

class BiCmds {

	private static BiMethod[] BiMethodList = {
			new BiMethod("cget", "-option", "@itcl-builtin-cget",
					new BiCgetCmd()),
			new BiMethod("configure", "?-option? ?value -option value...?",
					"@itcl-builtin-configure", new BiConfigureCmd()),
			new BiMethod("isa", "className", "@itcl-builtin-isa",
					new BiIsaCmd()) };

	private static final int BiMethodListLen = BiMethodList.length;

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_BiInit -> BiCmds.BiInit
	 * 
	 * Creates a namespace full of built-in methods/procs for [incr Tcl]
	 * classes. This includes things like the "isa" method and "info" for
	 * querying class info. Usually invoked by Itcl_Init() when [incr Tcl] is
	 * first installed into an interpreter.
	 * 
	 * Will raise a TclException to indicate failure.
	 * ------------------------------------------------------------------------
	 */

	public static void BiInit(Interp interp) // current interpreter
			throws TclException {
		Namespace itclBiNs;

		// Declare all of the built-in methods as Java procedures.

		for (int i = 0; i < BiMethodListLen; i++) {
			Linkage.RegisterObjC(interp, BiMethodList[i].registration
					.substring(1), BiMethodList[i].proc);
		}

		// Create the "::itcl::builtin" namespace for built-in class
		// commands. These commands are imported into each class
		// just before the class definition is parsed.

		interp.createCommand("::itcl::builtin::chain", new BiChainCmd());

		Ensemble.CreateEnsemble(interp, "::itcl::builtin::info");

		Ensemble.AddEnsemblePart(interp, "::itcl::builtin::info", "class", "",
				new BiInfoClassCmd());

		Ensemble.AddEnsemblePart(interp, "::itcl::builtin::info", "inherit",
				"", new BiInfoInheritCmd());

		Ensemble.AddEnsemblePart(interp, "::itcl::builtin::info", "heritage",
				"", new BiInfoHeritageCmd());

		Ensemble.AddEnsemblePart(interp, "::itcl::builtin::info", "function",
				"?name? ?-protection? ?-type? ?-name? ?-args? ?-body?",
				new BiInfoFunctionCmd());

		Ensemble
				.AddEnsemblePart(
						interp,
						"::itcl::builtin::info",
						"variable",
						"?name? ?-protection? ?-type? ?-name? ?-init? ?-value? ?-config?",
						new BiInfoVariableCmd());

		Ensemble.AddEnsemblePart(interp, "::itcl::builtin::info", "args",
				"procname", new BiInfoArgsCmd());

		Ensemble.AddEnsemblePart(interp, "::itcl::builtin::info", "body",
				"procname", new BiInfoBodyCmd());

		// Add an error handler to support all of the usual inquiries
		// for the "info" command in the global namespace.

		Ensemble.AddEnsemblePart(interp, "::itcl::builtin::info", "@error", "",
				new DefaultInfoCmd());

		// Export all commands in the built-in namespace so we can
		// import them later on.

		itclBiNs = Namespace.findNamespace(interp, "::itcl::builtin", null,
				TCL.LEAVE_ERR_MSG);
		if (itclBiNs == null) {
			throw new TclException(interp, interp.getResult().toString());
		}

		Namespace.exportList(interp, itclBiNs, "*", true);
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_InstallBiMethods -> BiCmds.InstallBiMethods
	 * 
	 * Invoked when a class is first created, just after the class definition
	 * has been parsed, to add definitions for built-in methods to the class. If
	 * a method already exists in the class with the same name as the built-in,
	 * then the built-in is skipped. Otherwise, a method definition for the
	 * built-in method is added.
	 * 
	 * Raises a TclException if anything goes wrong.
	 * ------------------------------------------------------------------------
	 */

	public static void InstallBiMethods(Interp interp, // current interpreter
			ItclClass cdefn) // class definition to be updated
			throws TclException {
		ItclHierIter hier;
		ItclClass cd;
		boolean foundMatch = false;

		// Scan through all of the built-in methods and see if
		// that method already exists in the class. If not, add
		// it in.
		//
		// TRICKY NOTE: The virtual tables haven't been built yet,
		// so look for existing methods the hard way--by scanning
		// through all classes.

		for (int i = 0; i < BiMethodListLen; i++) {
			hier = new ItclHierIter();
			Class.InitHierIter(hier, cdefn);
			cd = Class.AdvanceHierIter(hier);
			while (cd != null) {
				if (cd.functions.containsKey(BiMethodList[i].name)) {
					foundMatch = true;
					break;
				}
				cd = Class.AdvanceHierIter(hier);
			}
			Class.DeleteHierIter(hier);

			if (!foundMatch) {
				Methods.CreateMethod(interp, cdefn, BiMethodList[i].name,
						BiMethodList[i].usage, BiMethodList[i].registration);
			}
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_BiIsaCmd -> BiCmds.BiIsaCmd.cmdProc
	 * 
	 * Invoked whenever the user issues the "isa" method for an object. Handles
	 * the following syntax:
	 * 
	 * <objName> isa <className>
	 * 
	 * Checks to see if the object has the given <className> anywhere in its
	 * heritage. Set the interpreter result to 1 if so, and to 0 otherwise.
	 * ------------------------------------------------------------------------
	 */

	static class BiIsaCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			ItclClass contextClass, cdefn;
			ItclObject contextObj;
			String token;

			// Make sure that this command is being invoked in the proper
			// context.

			Methods.GetContextResult gcr = Methods.GetContext(interp);
			contextClass = gcr.cdefn;
			contextObj = gcr.odefn;

			if (contextObj == null) {
				throw new TclException(interp,
						"improper usage: should be \"object isa className\"");
			}

			if (objv.length != 2) {
				token = objv[0].toString();
				throw new TclException(interp,
						"wrong # args: should be \"object " + token
								+ " className\"");
			}

			// Look for the requested class. If it is not found, then
			// try to autoload it. If it absolutely cannot be found,
			// signal an error.

			token = objv[1].toString();
			cdefn = Class.FindClass(interp, token, true);
			if (cdefn == null) {
				throw new TclException(interp, interp.getResult().toString());
			}

			if (Objects.ObjectIsa(contextObj, cdefn)) {
				interp.setResult(true);
			} else {
				interp.setResult(false);
			}
		}
	} // end class BiIsaCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_BiConfigureCmd -> BiCmds.BiConfigureCmd.cmdProc
	 * 
	 * Invoked whenever the user issues the "configure" method for an object.
	 * Handles the following syntax:
	 * 
	 * <objName> configure ?-<option>? ?<value> -<option> <value>...?
	 * 
	 * Allows access to public variables as if they were configuration options.
	 * With no arguments, this command returns the current list of public
	 * variable options. If -<option> is specified, this returns the information
	 * for just one option:
	 * 
	 * -<optionName> <initVal> <currentVal>
	 * 
	 * Otherwise, the list of arguments is parsed, and values are assigned to
	 * the various public variable options. When each option changes, a big of
	 * "config" code associated with the option is executed, to bring the object
	 * up to date.
	 * ------------------------------------------------------------------------
	 */

	static class BiConfigureCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			ItclClass contextClass;
			ItclObject contextObj;

			String lastval;
			String token;
			ItclClass cd;
			ItclVarDefn vdefn;
			ItclVarLookup vlookup;
			ItclMember member;
			ItclMemberCode mcode;
			ItclHierIter hier;
			TclObject result, obj;
			StringBuffer buffer;
			ItclContext context;
			CallFrame oldFrame, uplevelFrame;

			// Make sure that this command is being invoked in the proper
			// context.

			Methods.GetContextResult gcr = Methods.GetContext(interp);
			contextClass = gcr.cdefn;
			contextObj = gcr.odefn;

			if (contextObj == null) {
				throw new TclException(
						interp,
						"improper usage: should be "
								+ "\"object configure ?-option? ?value -option value...?\"");
			}

			// BE CAREFUL: work in the virtual scope!

			contextClass = contextObj.classDefn;

			// HANDLE: configure

			if (objv.length == 1) {
				result = TclList.newInstance();

				hier = new ItclHierIter();
				Class.InitHierIter(hier, contextClass);
				while ((cd = Class.AdvanceHierIter(hier)) != null) {
					for (Iterator iter = cd.variables.entrySet().iterator(); iter
							.hasNext();) {
						Map.Entry entry = (Map.Entry) iter.next();
						String key = (String) entry.getKey();
						vdefn = (ItclVarDefn) entry.getValue();

						if (vdefn.member.protection == Itcl.PUBLIC) {
							obj = ReportPublicOpt(interp, vdefn, contextObj);
							TclList.append(interp, result, obj);
						}
					}
				}
				Class.DeleteHierIter(hier);

				interp.setResult(result);
				return;
			}

			// HANDLE: configure -option

			else if (objv.length == 2) {
				token = objv[1].toString();
				if (token.length() < 2 || token.charAt(0) != '-') {
					throw new TclException(
							interp,
							"improper usage: should be "
									+ "\"object configure ?-option? ?value -option value...?\"");
				}

				vlookup = (ItclVarLookup) contextClass.resolveVars.get(token
						.substring(1));
				if (vlookup != null) {
					if (vlookup.vdefn.member.protection != Itcl.PUBLIC) {
						vlookup = null;
					}
				}

				if (vlookup == null) {
					throw new TclException(interp, "unknown option \"" + token
							+ "\"");
				}

				result = ReportPublicOpt(interp, vlookup.vdefn, contextObj);
				interp.setResult(result);
				return;
			}

			// HANDLE: configure -option value -option value...
			//
			// Be careful to work in the virtual scope. If this "configure"
			// method was defined in a base class, the current namespace
			// (from Itcl_ExecMethod()) will be that base class. Activate
			// the derived class namespace here, so that instance variables
			// are accessed properly.

			context = new ItclContext(interp);
			Methods.PushContext(interp, null, contextObj.classDefn, contextObj,
					context);

			try {

				buffer = new StringBuffer(64);

				for (int i = 1; i < objv.length; i += 2) {
					vlookup = null;
					token = objv[i].toString();
					if (token.length() >= 2 && token.charAt(0) == '-') {
						vlookup = (ItclVarLookup) contextClass.resolveVars
								.get(token.substring(1));
					}

					if (vlookup == null
							|| vlookup.vdefn.member.protection != Itcl.PUBLIC) {
						throw new TclException(interp, "unknown option \""
								+ token + "\"");
					}
					if (i == objv.length - 1) {
						throw new TclException(interp, "value for \"" + token
								+ "\" missing");
					}

					member = vlookup.vdefn.member;
					TclObject tmp = interp.getVar(member.fullname, 0);
					buffer.setLength(0);
					if (tmp != null) {
						lastval = tmp.toString();
						buffer.append(lastval);
					}

					token = objv[i + 1].toString();

					try {
						interp.setVar(member.fullname, TclString
								.newInstance(token), 0);
					} catch (TclException ex) {
						interp
								.addErrorInfo("\n    (error in configuration of public variable \""
										+ member.fullname + "\")");
						throw ex;
					}

					// If this variable has some "config" code, invoke it now.
					//
					// TRICKY NOTE: Be careful to evaluate the code one level
					// up in the call stack, so that it's executed in the
					// calling context, and not in the context that we've
					// set up for public variable access.

					mcode = member.code;
					if (mcode != null && Methods.IsMemberCodeImplemented(mcode)) {
						String body = mcode.body;

						uplevelFrame = Migrate.GetCallFrame(interp, 1);
						oldFrame = Migrate.ActivateCallFrame(interp,
								uplevelFrame);

						try {
							Methods.EvalMemberCode(interp, null, member,
									contextObj, null);
							interp.resetResult();
						} catch (TclException ex) {
							String msg = "\n    (error in configuration of public variable \""
									+ member.fullname + "\")";
							interp.addErrorInfo(msg);

							interp.setVar(member.fullname, TclString
									.newInstance(buffer.toString()), 0);

							throw ex;
						} finally {
							Migrate.ActivateCallFrame(interp, oldFrame);
						}
					}
				}

			} finally {
				Methods.PopContext(interp, context);
			}
		}
	} // end class BiConfigureCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_BiCgetCmd -> BiCmds.BiCgetCmd.cmdProc
	 * 
	 * Invoked whenever the user issues the "cget" method for an object. Handles
	 * the following syntax:
	 * 
	 * <objName> cget -<option>
	 * 
	 * Allows access to public variables as if they were configuration options.
	 * Mimics the behavior of the usual "cget" method for Tk widgets. Returns
	 * the current value of the public variable with name <option>.
	 * ------------------------------------------------------------------------
	 */

	static class BiCgetCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			ItclClass contextClass;
			ItclObject contextObj;

			String name, val;
			ItclVarLookup vlookup;

			// Make sure that this command is being invoked in the proper
			// context.

			Methods.GetContextResult gcr = Methods.GetContext(interp);
			contextClass = gcr.cdefn;
			contextObj = gcr.odefn;

			if (contextObj == null || objv.length != 2) {
				throw new TclException(interp,
						"improper usage: should be \"object cget -option\"");
			}

			// BE CAREFUL: work in the virtual scope!

			contextClass = contextObj.classDefn;

			name = objv[1].toString();

			vlookup = null;
			vlookup = (ItclVarLookup) contextClass.resolveVars.get(name
					.substring(1));

			if (vlookup == null
					|| vlookup.vdefn.member.protection != Itcl.PUBLIC) {
				throw new TclException(interp, "unknown option \"" + name
						+ "\"");
			}

			val = Objects.GetInstanceVar(interp, vlookup.vdefn.member.fullname,
					contextObj, contextObj.classDefn);

			if (val != null) {
				interp.setResult(val);
			} else {
				interp.setResult("<undefined>");
			}
		}
	} // end class BiCgetCmd

	/*
	 * ------------------------------------------------------------------------
	 * ItclReportPublicOpt -> BiCmds.ReportPublicOpt
	 * 
	 * Returns information about a public variable formatted as a configuration
	 * option:
	 * 
	 * -<varName> <initVal> <currentVal>
	 * 
	 * Used by Itcl_BiConfigureCmd() to report configuration options. Returns a
	 * TclObject containing the information.
	 * ------------------------------------------------------------------------
	 */

	private static TclObject ReportPublicOpt(Interp interp, // interpreter
			// containing the
			// object
			ItclVarDefn vdefn, // public variable to be reported
			ItclObject contextObj) // object containing this variable
	{
		String val;
		ItclClass cdefn;
		ItclVarLookup vlookup;
		StringBuffer optName;
		TclObject list, obj;

		list = TclList.newInstance();

		// Determine how the option name should be reported.
		// If the simple name can be used to find it in the virtual
		// data table, then use the simple name. Otherwise, this
		// is a shadowed variable; use the full name.

		optName = new StringBuffer(64);
		optName.append("-");

		cdefn = contextObj.classDefn;
		vlookup = (ItclVarLookup) cdefn.resolveVars.get(vdefn.member.fullname);
		Util.Assert(vlookup != null, "vlookup != null");
		optName.append(vlookup.leastQualName);

		obj = TclString.newInstance(optName.toString());
		try {
			TclList.append(interp, list, obj);
		} catch (TclException ex) {
			throw new TclRuntimeError("unexpected TclException "
					+ ex.getMessage());
		}
		optName = null;

		if (vdefn.init != null) {
			obj = TclString.newInstance(vdefn.init);
		} else {
			obj = TclString.newInstance("<undefined>");
		}
		try {
			TclList.append(interp, list, obj);
		} catch (TclException ex) {
			throw new TclRuntimeError("unexpected TclException "
					+ ex.getMessage());
		}

		val = Objects.GetInstanceVar(interp, vdefn.member.fullname, contextObj,
				contextObj.classDefn);

		if (val != null) {
			obj = TclString.newInstance(val);
		} else {
			obj = TclString.newInstance("<undefined>");
		}
		try {
			TclList.append(interp, list, obj);
		} catch (TclException ex) {
			throw new TclRuntimeError("unexpected TclException "
					+ ex.getMessage());
		}
		return list;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_BiChainCmd -> BiCmds.BiChainCmd.cmdProc
	 * 
	 * Invoked to handle the "chain" command, to access the version of a method
	 * or proc that exists in a base class. Handles the following syntax:
	 * 
	 * chain ?<arg> <arg>...?
	 * 
	 * Looks up the inheritance hierarchy for another implementation of the
	 * method/proc that is currently executing. If another implementation is
	 * found, it is invoked with the specified <arg> arguments. If it is not
	 * found, this command does nothing. This allows a base class method to be
	 * called out in a generic way, so the code will not have to change if the
	 * base class changes.
	 * ------------------------------------------------------------------------
	 */

	static class BiChainCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			ItclClass contextClass;
			ItclObject contextObj;

			String cmd, head;
			ItclClass cdefn;
			ItclHierIter hier;
			ItclMemberFunc mfunc;
			CallFrame frame;
			TclObject cmdline;
			TclObject[] newobjv;
			TclObject[] fobjv;

			// If this command is not invoked within a class namespace,
			// signal an error.

			Methods.GetContextResult gcr;

			try {
				gcr = Methods.GetContext(interp);
				contextClass = gcr.cdefn;
				contextObj = gcr.odefn;
			} catch (TclException ex) {
				interp.resetResult();
				throw new TclException(interp,
						"cannot chain functions outside of a class context");
			}

			// Try to get the command name from the current call frame.
			// If it cannot be determined, do nothing. Otherwise, trim
			// off any leading path names.

			frame = Migrate.GetCallFrame(interp, 0);
			fobjv = ItclAccess.getCallFrameObjv(frame);
			if (frame == null || fobjv == null) {
				return;
			}
			cmd = fobjv[0].toString();
			Util.ParseNamespPathResult res = Util.ParseNamespPath(cmd);
			head = res.head;
			cmd = res.tail;

			// Look for the specified command in one of the base classes.
			// If we have an object context, then start from the most-specific
			// class and walk up the hierarchy to the current context. If
			// there is multiple inheritance, having the entire inheritance
			// hierarchy will allow us to jump over to another branch of
			// the inheritance tree.
			//
			// If there is no object context, just start with the current
			// class context.

			if (contextObj != null) {
				hier = new ItclHierIter();
				Class.InitHierIter(hier, contextObj.classDefn);
				while ((cdefn = Class.AdvanceHierIter(hier)) != null) {
					if (cdefn == contextClass) {
						break;
					}
				}
			} else {
				hier = new ItclHierIter();
				Class.InitHierIter(hier, contextClass);
				Class.AdvanceHierIter(hier); // skip the current class
			}

			// Now search up the class hierarchy for the next implementation.
			// If found, execute it. Otherwise, do nothing.

			while ((cdefn = Class.AdvanceHierIter(hier)) != null) {
				mfunc = (ItclMemberFunc) cdefn.functions.get(cmd);
				if (mfunc != null) {
					// NOTE: Avoid the usual "virtual" behavior of
					// methods by passing the full name as
					// the command argument.

					cmdline = Util.CreateArgs(interp, mfunc.member.fullname,
							objv, 1);

					try {
						newobjv = TclList.getElements(interp, cmdline);
					} catch (TclException ex) {
						throw new TclRuntimeError("unexpected TclException "
								+ ex.getMessage());
					}

					Util.EvalArgs(interp, newobjv);
					break;
				}
			}

			Class.DeleteHierIter(hier);
		}
	} // end class BiChainCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_BiInfoClassCmd -> BiCmds.BiInfoClassCmd.cmdProc
	 * 
	 * Returns information regarding the class for an object. This command can
	 * be invoked with or without an object context:
	 * 
	 * <objName> info class <= returns most-specific class name info class <=
	 * returns active namespace name
	 * 
	 * Returns a status TCL_OK/TCL_ERROR to indicate success/failure.
	 * ------------------------------------------------------------------------
	 */

	static class BiInfoClassCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			Namespace activeNs = Namespace.getCurrentNamespace(interp), contextNs = null;

			ItclClass contextClass;
			ItclObject contextObj;

			String name;

			if (objv.length != 1) {
				throw new TclNumArgsException(interp, 1, objv, "");
			}

			// If this command is not invoked within a class namespace,
			// signal an error.

			Methods.GetContextResult gcr;

			try {
				gcr = Methods.GetContext(interp);
				contextClass = gcr.cdefn;
				contextObj = gcr.odefn;
			} catch (TclException ex) {
				throw new TclException(interp, "\nget info like this instead: "
						+ "\n  namespace eval className { info " + objv[0]
						+ "... }");
			}

			// If there is an object context, then return the most-specific
			// class for the object. Otherwise, return the class namespace
			// name. Use normal class names when possible.

			if (contextObj != null) {
				contextNs = contextObj.classDefn.namesp;
			} else {
				Util.Assert(contextClass != null, "contextClass != null");
				Util.Assert(contextClass.namesp != null,
						"contextClass.namesp != null");
				contextNs = contextClass.namesp;
			}

			if (contextNs == null) {
				name = activeNs.fullName;
			} else if (contextNs.parent == activeNs) {
				name = contextNs.name;
			} else {
				name = contextNs.fullName;
			}

			interp.setResult(name);
		}
	} // end class BiInfoClassCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_BiInfoInheritCmd -> BiCmds.BiInfoInheritCmd.cmdProc
	 * 
	 * Returns the list of base classes for the current class context. Returns a
	 * status TCL_OK/TCL_ERROR to indicate success/failure.
	 * ------------------------------------------------------------------------
	 */

	static class BiInfoInheritCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			Namespace activeNs = Namespace.getCurrentNamespace(interp);

			ItclClass contextClass;
			ItclObject contextObj;

			ItclClass cdefn;
			Itcl_ListElem elem;
			TclObject list, obj;

			if (objv.length != 1) {
				throw new TclNumArgsException(interp, 1, objv, "");
			}

			// If this command is not invoked within a class namespace,
			// signal an error.

			Methods.GetContextResult gcr;

			try {
				gcr = Methods.GetContext(interp);
				contextClass = gcr.cdefn;
				contextObj = gcr.odefn;
			} catch (TclException ex) {
				String name = objv[0].toString();
				throw new TclException(interp, "\nget info like this instead: "
						+ "\n  namespace eval className { info " + name
						+ "... }");
			}

			// Return the list of base classes.

			list = TclList.newInstance();

			elem = Util.FirstListElem(contextClass.bases);
			while (elem != null) {
				cdefn = (ItclClass) Util.GetListValue(elem);
				if (cdefn.namesp.parent == activeNs) {
					obj = TclString.newInstance(cdefn.namesp.name);
				} else {
					obj = TclString.newInstance(cdefn.namesp.fullName);
				}
				TclList.append(interp, list, obj);
				elem = Util.NextListElem(elem);
			}

			interp.setResult(list);
		}
	} // end class BiInfoInheritCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_BiInfoHeritageCmd -> BiCmds.BiInfoHeritageCmd.cmdProc
	 * 
	 * Returns the entire derivation hierarchy for this class, presented in the
	 * order that classes are traversed for finding data members and member
	 * functions.
	 * 
	 * Returns a status TCL_OK/TCL_ERROR to indicate success/failure.
	 * ------------------------------------------------------------------------
	 */

	static class BiInfoHeritageCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			Namespace activeNs = Namespace.getCurrentNamespace(interp);

			ItclClass contextClass;
			ItclObject contextObj;

			ItclHierIter hier;
			TclObject list, obj;
			ItclClass cdefn;

			if (objv.length != 1) {
				throw new TclNumArgsException(interp, 1, objv, "");
			}

			// If this command is not invoked within a class namespace,
			// signal an error.

			Methods.GetContextResult gcr;

			try {
				gcr = Methods.GetContext(interp);
				contextClass = gcr.cdefn;
				contextObj = gcr.odefn;
			} catch (TclException ex) {
				String name = objv[0].toString();
				throw new TclException(interp, "\nget info like this instead: "
						+ "\n  namespace eval className { info " + name
						+ "... }");
			}

			// Traverse through the derivation hierarchy and return
			// base class names.

			list = TclList.newInstance();

			hier = new ItclHierIter();
			Class.InitHierIter(hier, contextClass);
			while ((cdefn = Class.AdvanceHierIter(hier)) != null) {
				if (cdefn.namesp.parent == activeNs) {
					obj = TclString.newInstance(cdefn.namesp.name);
				} else {
					obj = TclString.newInstance(cdefn.namesp.fullName);
				}
				TclList.append(interp, list, obj);
			}
			Class.DeleteHierIter(hier);

			interp.setResult(list);
		}
	} // end class BiInfoHeritageCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_BiInfoFunctionCmd -> BiCmds.BiInfoFunctionCmd.cmdProc
	 * 
	 * Returns information regarding class member functions (methods/procs).
	 * Handles the following syntax:
	 * 
	 * info function ?cmdName? ?-protection? ?-type? ?-name? ?-args? ?-body?
	 * 
	 * If the ?cmdName? is not specified, then a list of all known command
	 * members is returned. Otherwise, the information for a specific command is
	 * returned. Raises a TclException if anything goes wrong.
	 * ------------------------------------------------------------------------
	 */

	static class BiInfoFunctionCmd implements Command {
		static String[] options = { "-args", "-body", "-name", "-protection",
				"-type", };

		static final private int BIfArgsIdx = 0;
		static final private int BIfBodyIdx = 1;
		static final private int BIfNameIdx = 2;
		static final private int BIfProtectIdx = 3;
		static final private int BIfTypeIdx = 4;

		static int[] DefInfoFunction = { BIfProtectIdx, BIfTypeIdx, BIfNameIdx,
				BIfArgsIdx, BIfBodyIdx };

		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			String cmdName = null;
			TclObject result = null;
			TclObject obj = null;

			ItclClass contextClass, cdefn;
			ItclObject contextObj;

			int[] iflist;
			int[] iflistStorage = new int[5];

			String name, val;
			ItclMemberFunc mfunc;
			ItclMemberCode mcode;
			ItclHierIter hier;
			int objc, skip;

			// If this command is not invoked within a class namespace,
			// signal an error.

			Methods.GetContextResult gcr;

			try {
				gcr = Methods.GetContext(interp);
				contextClass = gcr.cdefn;
				contextObj = gcr.odefn;
			} catch (TclException ex) {
				name = objv[0].toString();
				interp.resetResult();
				throw new TclException(interp, "\nget info like this instead: "
						+ "\n  namespace eval className { info " + name
						+ "... }");
			}

			// Process args:
			// ?cmdName? ?-protection? ?-type? ?-name? ?-args? ?-body?

			objc = objv.length;
			skip = 0;

			skip++; // skip over command name
			objc--;

			if (objc > 0) {
				cmdName = objv[skip].toString();
				objc--;
				skip++;
			}

			// Return info for a specific command.

			if (cmdName != null) {
				mfunc = (ItclMemberFunc) contextClass.resolveCmds.get(cmdName);

				if (mfunc == null) {
					throw new TclException(interp, "\"" + cmdName
							+ "\" isn't a member function in class \""
							+ contextClass.namesp.fullName + "\"");
				}
				mcode = mfunc.member.code;

				// By default, return everything.

				if (objc == 0) {
					objc = 5;
					iflist = DefInfoFunction;
				}

				// Otherwise, scan through all remaining flags and
				// figure out what to return.

				else {
					iflist = iflistStorage;
					for (int i = 0; i < objc; i++) {
						iflist[i] = TclIndex.get(interp, objv[i + skip],
								options, "option", 0);
					}
				}

				if (objc > 1) {
					result = TclList.newInstance();
				}

				for (int i = 0; i < objc; i++) {
					switch (iflist[i]) {
					case BIfArgsIdx:
						if (mcode != null && mcode.arglist != null) {
							obj = Methods
									.ArgList(mcode.argcount, mcode.arglist);
						} else if ((mfunc.member.flags & ItclInt.ARG_SPEC) != 0) {
							obj = Methods
									.ArgList(mfunc.argcount, mfunc.arglist);
						} else {
							obj = TclString.newInstance("<undefined>");
						}
						break;

					case BIfBodyIdx:
						if (mcode != null
								&& Methods.IsMemberCodeImplemented(mcode)) {
							obj = TclString.newInstance(mcode.body);
						} else {
							obj = TclString.newInstance("<undefined>");
						}
						break;

					case BIfNameIdx:
						obj = TclString.newInstance(mfunc.member.fullname);
						break;

					case BIfProtectIdx:
						val = Util.ProtectionStr(mfunc.member.protection);
						obj = TclString.newInstance(val);
						break;

					case BIfTypeIdx:
						val = ((mfunc.member.flags & ItclInt.COMMON) != 0) ? "proc"
								: "method";
						obj = TclString.newInstance(val);
						break;
					}

					if (objc == 1) {
						result = obj;
					} else {
						TclList.append(interp, result, obj);
					}
				}
				interp.setResult(result);
			}

			// Return the list of available commands.

			else {
				result = TclList.newInstance();

				hier = new ItclHierIter();
				Class.InitHierIter(hier, contextClass);
				while ((cdefn = Class.AdvanceHierIter(hier)) != null) {
					for (Iterator iter = cdefn.functions.entrySet().iterator(); iter
							.hasNext();) {
						Map.Entry entry = (Map.Entry) iter.next();
						String key = (String) entry.getKey();
						mfunc = (ItclMemberFunc) entry.getValue();
						obj = TclString.newInstance(mfunc.member.fullname);
						TclList.append(interp, result, obj);
					}
				}
				Class.DeleteHierIter(hier);
				interp.setResult(result);
			}
		}
	} // end class BiInfoFunctionCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_BiInfoVariableCmd -> BiCmds.BiInfoVariableCmd.cmdProc
	 * 
	 * Returns information regarding class data members (variables and commons).
	 * Handles the following syntax:
	 * 
	 * info variable ?varName? ?-protection? ?-type? ?-name? ?-init? ?-config?
	 * ?-value?
	 * 
	 * If the ?varName? is not specified, then a list of all known data members
	 * is returned. Otherwise, the information for a specific member is
	 * returned. Raises a TclException if anything goes wrong.
	 * ------------------------------------------------------------------------
	 */

	static class BiInfoVariableCmd implements Command {
		static String[] options = { "-config", "-init", "-name", "-protection",
				"-type", "-value" };

		static final private int BIvConfigIdx = 0;
		static final private int BIvInitIdx = 1;
		static final private int BIvNameIdx = 2;
		static final private int BIvProtectIdx = 3;
		static final private int BIvTypeIdx = 4;
		static final private int BIvValueIdx = 5;

		static int[] DefInfoVariable = { BIvProtectIdx, BIvTypeIdx, BIvNameIdx,
				BIvInitIdx, BIvValueIdx };

		static int[] DefInfoPubVariable = { BIvProtectIdx, BIvTypeIdx,
				BIvNameIdx, BIvInitIdx, BIvConfigIdx, BIvValueIdx };

		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			String varName = null;
			TclObject result = null;
			TclObject obj = null;

			int[] ivlist;
			int[] ivlistStorage = new int[6];

			ItclClass contextClass;
			ItclObject contextObj;

			String val, name;
			ItclClass cdefn;
			ItclVarDefn vdefn;
			ItclVarLookup vlookup;
			ItclMember member;
			ItclHierIter hier;
			int objc, skip;

			// If this command is not invoked within a class namespace,
			// signal an error.

			Methods.GetContextResult gcr;

			try {
				gcr = Methods.GetContext(interp);
				contextClass = gcr.cdefn;
				contextObj = gcr.odefn;
			} catch (TclException ex) {
				name = objv[0].toString();
				interp.resetResult();
				throw new TclException(interp, "\nget info like this instead: "
						+ "\n  namespace eval className { info " + name
						+ "... }");
			}

			// Process args:
			// ?varName? ?-protection? ?-type? ?-name? ?-init? ?-config?
			// ?-value?

			objc = objv.length;
			skip = 0;

			skip++; // skip over command name
			objc--;

			if (objc > 0) {
				varName = objv[skip].toString();
				objc--;
				skip++;
			}

			// Return info for a specific variable.

			if (varName != null) {
				vlookup = (ItclVarLookup) contextClass.resolveVars.get(varName);
				if (vlookup == null) {
					throw new TclException(interp, "\"" + varName
							+ "\" isn't a variable in class \""
							+ contextClass.namesp.fullName + "\"");
				}
				member = vlookup.vdefn.member;

				// By default, return everything.

				if (objc == 0) {
					if (member.protection == Itcl.PUBLIC
							&& ((member.flags & ItclInt.COMMON) == 0)) {
						ivlist = DefInfoPubVariable;
						objc = 6;
					} else {
						ivlist = DefInfoVariable;
						objc = 5;
					}
				}

				// Otherwise, scan through all remaining flags and
				// figure out what to return.

				else {
					ivlist = ivlistStorage;
					for (int i = 0; i < objc; i++) {
						ivlist[i] = TclIndex.get(interp, objv[i + skip],
								options, "option", 0);
					}
				}

				if (objc > 1) {
					result = TclList.newInstance();
				}

				for (int i = 0; i < objc; i++) {
					switch (ivlist[i]) {
					case BIvConfigIdx:
						if (member.code != null
								&& Methods.IsMemberCodeImplemented(member.code)) {
							obj = TclString.newInstance(member.code.body);
						} else {
							obj = TclString.newInstance("");
						}
						break;

					case BIvInitIdx:
						// If this is the built-in "this" variable, then
						// report the object name as its initialization string.

						if ((member.flags & ItclInt.THIS_VAR) != 0) {
							if (contextObj != null
									&& contextObj.accessCmd != null) {
								name = contextObj.classDefn.interp
										.getCommandFullName(contextObj.w_accessCmd);
								obj = TclString.newInstance(name);
							} else {
								obj = TclString.newInstance("<objectName>");
							}
						} else if (vlookup.vdefn.init != null) {
							obj = TclString.newInstance(vlookup.vdefn.init);
						} else {
							obj = TclString.newInstance("<undefined>");
						}
						break;

					case BIvNameIdx:
						obj = TclString.newInstance(member.fullname);
						break;

					case BIvProtectIdx:
						val = Util.ProtectionStr(member.protection);
						obj = TclString.newInstance(val);
						break;

					case BIvTypeIdx:
						val = ((member.flags & ItclInt.COMMON) != 0) ? "common"
								: "variable";
						obj = TclString.newInstance(val);
						break;

					case BIvValueIdx:
						if ((member.flags & ItclInt.COMMON) != 0) {
							val = Class.GetCommonVar(interp, member.fullname,
									member.classDefn);
						} else if (contextObj == null) {
							interp.resetResult();
							throw new TclException(interp,
									"cannot access object-specific info "
											+ "without an object context");
						} else {
							val = Objects.GetInstanceVar(interp,
									member.fullname, contextObj,
									member.classDefn);
						}

						if (val == null) {
							val = "<undefined>";
						}
						obj = TclString.newInstance(val);
						break;
					}

					if (objc == 1) {
						result = obj;
					} else {
						TclList.append(interp, result, obj);
					}
				}
				interp.setResult(result);
			}

			// Return the list of available variables. Report the built-in
			// "this" variable only once, for the most-specific class.

			else {
				result = TclList.newInstance();

				hier = new ItclHierIter();
				Class.InitHierIter(hier, contextClass);
				while ((cdefn = Class.AdvanceHierIter(hier)) != null) {
					for (Iterator iter = cdefn.variables.entrySet().iterator(); iter
							.hasNext();) {
						Map.Entry entry = (Map.Entry) iter.next();
						String key = (String) entry.getKey();
						vdefn = (ItclVarDefn) entry.getValue();

						if ((vdefn.member.flags & ItclInt.THIS_VAR) != 0) {
							if (cdefn == contextClass) {
								obj = TclString
										.newInstance(vdefn.member.fullname);
								TclList.append(interp, result, obj);
							}
						} else {
							obj = TclString.newInstance(vdefn.member.fullname);
							TclList.append(interp, result, obj);
						}
					}
				}
				Class.DeleteHierIter(hier);

				interp.setResult(result);
			}
		}
	} // end class BiInfoVariableCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_BiInfoBodyCmd -> BiCmds.BiInfoBodyCmd.cmdProc
	 * 
	 * Handles the usual "info body" request, returning the body for a specific
	 * proc. Included here for backward compatibility, since otherwise Tcl would
	 * complain that class procs are not real "procs". Raises a TclException if
	 * anything goes wrong.
	 * ------------------------------------------------------------------------
	 */

	static class BiInfoBodyCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			String name;
			ItclClass contextClass;
			ItclObject contextObj;
			ItclMemberFunc mfunc;
			ItclMemberCode mcode;
			TclObject obj;

			if (objv.length != 2) {
				throw new TclNumArgsException(interp, 1, objv, "function");
			}

			// If this command is not invoked within a class namespace,
			// then treat the procedure name as a normal Tcl procedure.

			if (!Class.IsClassNamespace(Namespace.getCurrentNamespace(interp))) {
				name = objv[1].toString();
				interp.eval("::info body {" + name + "}");
				return;
			}

			// Otherwise, treat the name as a class method/proc.

			Methods.GetContextResult gcr;

			try {
				gcr = Methods.GetContext(interp);
				contextClass = gcr.cdefn;
				contextObj = gcr.odefn;
			} catch (TclException ex) {
				name = objv[0].toString();
				interp.resetResult();
				throw new TclException(interp, "\nget info like this instead: "
						+ "\n  namespace eval className { info " + name
						+ "... }");
			}

			name = objv[1].toString();
			mfunc = (ItclMemberFunc) contextClass.resolveCmds.get(name);
			if (mfunc == null) {
				throw new TclException(interp, "\"" + name
						+ "\" isn't a procedure");
			}
			mcode = mfunc.member.code;

			// Return a string describing the implementation.

			if (mcode != null && Methods.IsMemberCodeImplemented(mcode)) {
				obj = TclString.newInstance(mcode.body);
			} else {
				obj = TclString.newInstance("<undefined>");
			}
			interp.setResult(obj);
		}
	} // end class BiInfoBodyCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_BiInfoArgsCmd -> BiCmds.BiInfoArgsCmd.cmdProc
	 * 
	 * Handles the usual "info args" request, returning the argument list for a
	 * specific proc. Included here for backward compatibility, since otherwise
	 * Tcl would complain that class procs are not real "procs". Raises a
	 * TclException if anything goes wrong.
	 * ------------------------------------------------------------------------
	 */

	static class BiInfoArgsCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			String name;
			ItclClass contextClass;
			ItclObject contextObj;
			ItclMemberFunc mfunc;
			ItclMemberCode mcode;
			TclObject obj;

			if (objv.length != 2) {
				throw new TclNumArgsException(interp, 1, objv, "function");
			}

			name = objv[1].toString();

			// If this command is not invoked within a class namespace,
			// then treat the procedure name as a normal Tcl procedure.

			if (!Class.IsClassNamespace(Namespace.getCurrentNamespace(interp))) {
				name = objv[1].toString();
				interp.eval("::info args {" + name + "}");
				return;
			}

			// Otherwise, treat the name as a class method/proc.

			Methods.GetContextResult gcr;

			try {
				gcr = Methods.GetContext(interp);
				contextClass = gcr.cdefn;
				contextObj = gcr.odefn;
			} catch (TclException ex) {
				name = objv[0].toString();
				interp.resetResult();
				throw new TclException(interp, "\nget info like this instead: "
						+ "\n  namespace eval className { info " + name
						+ "... }");
			}

			mfunc = (ItclMemberFunc) contextClass.resolveCmds.get(name);
			if (mfunc == null) {
				throw new TclException(interp, "\"" + name
						+ "\" isn't a procedure");
			}
			mcode = mfunc.member.code;

			// Return a string describing the argument list.

			if (mcode != null && mcode.arglist != null) {
				obj = Methods.ArgList(mcode.argcount, mcode.arglist);
			} else if ((mfunc.member.flags & ItclInt.ARG_SPEC) != 0) {
				obj = Methods.ArgList(mfunc.argcount, mfunc.arglist);
			} else {
				obj = TclString.newInstance("<undefined>");
			}
			interp.setResult(obj);
		}
	} // end class BiInfoArgsCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_DefaultInfoCmd -> BiCmds.DefaultInfoCmd.cmdProc
	 * 
	 * Handles any unknown options for the "itcl::builtin::info" command by
	 * passing requests on to the usual "::info" command. If the option is
	 * recognized, then it is handled. Otherwise, if it is still unknown, then
	 * an error message is returned with the list of possible options.
	 * 
	 * Raises a TclException if anything goes wrong.
	 * ------------------------------------------------------------------------
	 */

	static class DefaultInfoCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			String name;
			WrappedCommand wcmd;
			Command cmd;
			StringBuffer result;

			// Look for the usual "::info" command, and use it to
			// evaluate the unknown option.

			wcmd = Namespace.findCommand(interp, "::info", null, 0);
			if (wcmd == null) {
				name = objv[0].toString();
				interp.resetResult();

				result = new StringBuffer(64);
				result.append("bad option \"" + name
						+ "\" should be one of...\n");
				Ensemble.GetEnsembleUsageForObj(interp, objv[0], result);

				throw new TclException(interp, result.toString());
			}

			cmd = wcmd.cmd;

			try {
				if (wcmd.mustCallInvoke(interp)) wcmd.invoke(interp, objv);
				else cmd.cmdProc(interp, objv);
			} catch (TclException ex) {
				// If the option was not recognized by the usual "info" command,
				// then we got a "bad option" error message. Add the options
				// for the current ensemble to the error message.

				String ires = interp.getResult().toString();
				if (ires.startsWith("bad option")) {
					result = new StringBuffer(64);
					result.append(ires);
					result.append("\nor");
					Ensemble.GetEnsembleUsageForObj(interp, objv[0], result);
					throw new TclException(interp, result.toString());
				}
			}
		}
	} // end class DefaultInfoCmd

} // end class BiCmds

