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
 *  This file defines information that tracks classes and objects
 *  at a global level for a given interpreter.
 *
 * ========================================================================
 *  AUTHOR:  Michael J. McLennan
 *           Bell Labs Innovations for Lucent Technologies
 *           mmclennan@lucent.com
 *           http://www.tcltk.com/itcl
 *
 *     RCS:  $Id: Cmds.java,v 1.4 2006/01/26 19:49:18 mdejong Exp $
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
import tcl.lang.Command;
import tcl.lang.CommandWithDispose;
import tcl.lang.Interp;
import tcl.lang.Namespace;
import tcl.lang.Resolver;
import tcl.lang.TCL;
import tcl.lang.TclException;
import tcl.lang.TclInteger;
import tcl.lang.TclList;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;
import tcl.lang.TclRuntimeError;
import tcl.lang.TclString;
import tcl.lang.Var;
import tcl.lang.WrappedCommand;

class Cmds {

	// The following string is the startup script executed in new
	// interpreters. It locates the Tcl code in the [incr Tcl] library
	// directory and loads it in.

	static String initScript = "namespace eval ::itcl { source resource:/tcl/pkg/itcl/library/itcl.tcl }";

	// The following script is used to initialize Itcl in a safe interpreter.

	static String safeInitScript = "proc ::itcl::local {class name args} {\n"
			+ "    set ptr [uplevel [list $class $name] $args]\n"
			+ "    uplevel [list set itcl-local-$ptr $ptr]\n"
			+ "    set cmd [uplevel namespace which -command $ptr]\n"
			+ "    uplevel [list trace variable itcl-local-$ptr u \"::itcl::delete object $cmd; list\"]\n"
			+ "    return $ptr\n" + "}";

	static int itclCompatFlags = -1;

	/*
	 * ------------------------------------------------------------------------
	 * Initialize -> Cmds.Initialize
	 * 
	 * Invoked whenever a new interpeter is created to install the [incr Tcl]
	 * package. Usually invoked within Tcl_AppInit() at the start of execution.
	 * 
	 * Creates the "::itcl" namespace and installs access commands for creating
	 * classes and querying info.
	 * 
	 * Will raise a TclException to indicate failure.
	 * ------------------------------------------------------------------------
	 */

	static void Initialize(Interp interp) // interpreter to be updated
			throws TclException {
		Namespace itclNs;
		ItclObjectInfo info;

		String TCL_VERSION = "8.0";
		interp.pkgRequire("Tcl", TCL_VERSION, false);

		// See if [incr Tcl] is already installed.

		if (interp.getCommand("::itcl::class") != null) {
			throw new TclException(interp, "already installed: [incr Tcl]");
		}

		// Skip compatability options stuff

		itclCompatFlags = 0;

		// Initialize the ensemble package first, since we need this
		// for other parts of [incr Tcl].

		Ensemble.EnsembleInit(interp);

		// Create the top-level data structure for tracking objects.
		// Store this as "associated data" for easy access, but link
		// it to the itcl namespace for ownership.

		info = new ItclObjectInfo();
		info.interp = interp;
		info.objects = new HashMap();
		info.transparentFrames = new Itcl_Stack();
		Util.InitStack(info.transparentFrames);
		info.contextFrames = new HashMap();
		info.protection = Itcl.DEFAULT_PROTECT;
		info.cdefnStack = new Itcl_Stack();
		Util.InitStack(info.cdefnStack);

		interp.setAssocData(ItclInt.INTERP_DATA, info);

		// Install commands into the "::itcl" namespace.

		interp.createCommand("::itcl::class", new Parse.ClassCmd(info));
		Util.PreserveData(info);

		interp.createCommand("::itcl::body", new Methods.BodyCmd());
		interp.createCommand("::itcl::configbody", new Methods.ConfigBodyCmd());

		// Util.EventuallyFree(info, ItclDelObjectInfo);

		// Create the "itcl::find" command for high-level queries.

		Ensemble.CreateEnsemble(interp, "::itcl::find");
		Ensemble.AddEnsemblePart(interp, "::itcl::find", "classes",
				"?pattern?", new FindClassesCmd(info));
		Util.PreserveData(info);

		Ensemble.AddEnsemblePart(interp, "::itcl::find", "objects",
				"?-class className? ?-isa className? ?pattern?",
				new FindObjectsCmd(info));
		Util.PreserveData(info);

		// Create the "itcl::delete" command to delete objects
		// and classes.

		Ensemble.CreateEnsemble(interp, "::itcl::delete");
		Ensemble.AddEnsemblePart(interp, "::itcl::delete", "class",
				"name ?name...?", new DelClassCmd(info));
		Util.PreserveData(info);

		Ensemble.AddEnsemblePart(interp, "::itcl::delete", "object",
				"name ?name...?", new DelObjectCmd(info));
		Util.PreserveData(info);

		// Create the "itcl::is" command to test object
		// and classes existence.

		Ensemble.CreateEnsemble(interp, "::itcl::is");
		Ensemble.AddEnsemblePart(interp, "::itcl::is", "class", "name",
				new IsClassCmd(info));
		Util.PreserveData(info);

		Ensemble.AddEnsemblePart(interp, "::itcl::is", "object",
				"?-class classname? name", new IsObjectCmd(info));
		Util.PreserveData(info);

		// Add "code" and "scope" commands for handling scoped values.

		interp.createCommand("::itcl::code", new CodeCmd());
		interp.createCommand("::itcl::scope", new ScopeCmd());

		// Add commands for handling import stubs at the Tcl level.

		Ensemble.CreateEnsemble(interp, "::itcl::import::stub");
		Ensemble.AddEnsemblePart(interp, "::itcl::import::stub", "create",
				"name", new StubCreateCmd());
		Ensemble.AddEnsemblePart(interp, "::itcl::import::stub", "exists",
				"name", new StubExistsCmd());

		// Install a variable resolution procedure to handle scoped
		// values everywhere within the interpreter.

		Resolver resolver = new Objects.ScopedVarResolverImpl();
		interp.addInterpResolver("itcl", resolver);

		// Install the "itcl::parser" namespace used to parse the
		// class definitions.

		Parse.ParseInit(interp, info);

		// Create "itcl::builtin" namespace for commands that
		// are automatically built into class definitions.

		BiCmds.BiInit(interp);

		// Export all commands in the "itcl" namespace so that they
		// can be imported with something like "namespace import itcl::*"

		itclNs = Namespace.findNamespace(interp, "::itcl", null,
				TCL.LEAVE_ERR_MSG);

		if (itclNs == null) {
			throw new TclException(interp, interp.getResult().toString());
		}

		// This was changed from a glob export (itcl::*) to explicit
		// command exports, so that the itcl::is command can *not* be
		// exported. This is done for concern that the itcl::is command
		// imported might be confusing ("is").

		Namespace.exportList(interp, itclNs, "body", true);
		Namespace.exportList(interp, itclNs, "class", false);
		Namespace.exportList(interp, itclNs, "code", false);
		Namespace.exportList(interp, itclNs, "configbody", false);
		Namespace.exportList(interp, itclNs, "delete", false);
		Namespace.exportList(interp, itclNs, "delete_helper", false);
		Namespace.exportList(interp, itclNs, "ensemble", false);
		Namespace.exportList(interp, itclNs, "find", false);
		Namespace.exportList(interp, itclNs, "local", false);
		Namespace.exportList(interp, itclNs, "scope", false);

		// Set up the variables containing version info.

		interp.setVar("::itcl::patchLevel", TclString
				.newInstance(Itcl.PATCH_LEVEL), TCL.NAMESPACE_ONLY);

		interp.setVar("::itcl::version", TclString.newInstance(Itcl.VERSION),
				TCL.NAMESPACE_ONLY);

		// Package is now loaded.
		// Note that we don't run a pkgProvide here since it is done as
		// part of the package ifneeded script and so that Itcl can
		// be loaded via the java::load command.

		// interp.pkgProvide("Itcl", Itcl.PATCH_LEVEL);
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_Init -> Cmds.Init
	 * 
	 * Invoked whenever a new INTERPRETER is created to install the [incr Tcl]
	 * package. Usually invoked within Tcl_AppInit() at the start of execution.
	 * 
	 * Creates the "::itcl" namespace and installs access commands for creating
	 * classes and querying info.
	 * 
	 * Will raise a TclException to indicate failure.
	 * ------------------------------------------------------------------------
	 */

	static void Init(Interp interp) // interpreter to be updated
			throws TclException {
		Initialize(interp);
		interp.eval(initScript);
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_SafeInit -> Cmds.SafeInit
	 * 
	 * Invoked whenever a new SAFE INTERPRETER is created to install the [incr
	 * Tcl] package.
	 * 
	 * Creates the "::itcl" namespace and installs access commands for creating
	 * classes and querying info.
	 * 
	 * Will raise a TclException to indicate failure.
	 * ------------------------------------------------------------------------
	 */

	static void SafeInit(Interp interp) // interpreter to be updated
			throws TclException {
		Initialize(interp);
		interp.eval(safeInitScript);
	}

	/*
	 * ------------------------------------------------------------------------
	 * ItclDelObjectInfo -> Cmds.DelObjectInfo
	 * 
	 * Invoked when the management info for [incr Tcl] is no longer being used
	 * in an interpreter. This will only occur when all class manipulation
	 * commands are removed from the interpreter.
	 * ------------------------------------------------------------------------
	 */

	static void DelObjectInfo(ItclObjectInfo info) // client data for class
	// command
	{
		ItclObject contextObj;

		// Destroy all known objects by deleting their access
		// commands. Use FirstHashEntry to always reset the
		// search after deleteCommandFromToken() (Fix 227804).

		while ((contextObj = (ItclObject) ItclAccess
				.FirstHashEntry(info.objects)) != null) {
			info.interp.deleteCommandFromToken(contextObj.w_accessCmd);
		}
		info.objects.clear();
		info.objects = null;

		// Discard all known object contexts.

		for (Iterator iter = info.contextFrames.entrySet().iterator(); iter
				.hasNext();) {
			Map.Entry entry = (Map.Entry) iter.next();
			contextObj = (ItclObject) entry.getValue();
			Util.ReleaseData(contextObj);
		}
		info.contextFrames.clear();
		info.contextFrames = null;

		Util.DeleteStack(info.transparentFrames);
		info.transparentFrames = null;
		Util.DeleteStack(info.cdefnStack);
		info.cdefnStack = null;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_FindClassesCmd -> Cmds.FindClassesCmd.cmdProc
	 * 
	 * Invoked by Tcl whenever the user issues an "itcl::find classes" command
	 * to query the list of known classes. Handles the following syntax:
	 * 
	 * find classes ?<pattern>?
	 * 
	 * Will raise a TclException to indicate failure.
	 * ------------------------------------------------------------------------
	 */
	public static class FindClassesCmd implements CommandWithDispose {
		ItclObjectInfo info;

		FindClassesCmd(ItclObjectInfo info) {
			this.info = info;
		}

		public void disposeCmd() {
			Util.ReleaseData(info);
		}

		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			Namespace activeNs = Namespace.getCurrentNamespace(interp);
			Namespace globalNs = Namespace.getGlobalNamespace(interp);
			boolean forceFullNames = false;

			String pattern;
			String cmdName;
			boolean newEntry, handledActiveNs;
			// Maps WrappedCommand to the empty string
			HashMap unique;
			Itcl_Stack search;
			WrappedCommand cmd, originalCmd;
			Namespace ns;
			TclObject obj, result;

			if (objv.length > 2) {
				throw new TclNumArgsException(interp, 1, objv, "?pattern?");
			}

			if (objv.length == 2) {
				pattern = objv[1].toString();
				forceFullNames = (pattern.indexOf("::") != -1);
			} else {
				pattern = null;
			}

			// Search through all commands in the current namespace first,
			// in the global namespace next, then in all child namespaces
			// in this interpreter. If we find any commands that
			// represent classes, report them.

			search = new Itcl_Stack();
			Util.InitStack(search);
			Util.PushStack(globalNs, search);
			Util.PushStack(activeNs, search); // last in, first out!

			unique = new HashMap();
			result = TclList.newInstance();

			handledActiveNs = false;
			while (Util.GetStackSize(search) > 0) {
				ns = (Namespace) Util.PopStack(search);
				if (ns == activeNs && handledActiveNs) {
					continue;
				}

				for (Iterator iter = ns.cmdTable.entrySet().iterator(); iter
						.hasNext();) {
					Map.Entry entry = (Map.Entry) iter.next();
					String key = (String) entry.getKey();
					cmd = (WrappedCommand) entry.getValue();

					if (Class.IsClass(cmd)) {
						originalCmd = Namespace.getOriginalCommand(cmd);

						// Report full names if:
						// - the pattern has namespace qualifiers
						// - the class namespace is not in the current namespace
						// - the class's object creation command is imported
						// from
						// another namespace.
						//
						// Otherwise, report short names.

						if (forceFullNames || ns != activeNs
								|| originalCmd != null) {
							cmdName = interp.getCommandFullName(cmd);
							obj = TclString.newInstance(cmdName);
						} else {
							cmdName = interp.getCommandName(cmd);
							obj = TclString.newInstance(cmdName);
						}

						if (originalCmd != null) {
							cmd = originalCmd;
						}
						newEntry = (unique.put(cmd, "") == null);
						if (newEntry
								&& (pattern == null || tcl.lang.Util
										.stringMatch(cmdName, pattern))) {
							TclList.append(interp, result, obj);
						} else {
							// if not appended to the result, free obj
							// Tcl_DecrRefCount(objPtr);
						}

					}
				}
				handledActiveNs = true; // don't process the active namespace
				// twice

				// Push any child namespaces onto the stack and continue
				// the search in those namespaces.

				for (Iterator iter = ns.childTable.entrySet().iterator(); iter
						.hasNext();) {
					Map.Entry entry = (Map.Entry) iter.next();
					String key = (String) entry.getKey();
					Namespace child = (Namespace) entry.getValue();
					Util.PushStack(child, search);
				}
			}
			unique.clear();
			Util.DeleteStack(search);

			interp.setResult(result);
		}
	} // end class FindClassesCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_FindObjectsCmd -> Cmds.FindObjectsCmd.cmdProc
	 * 
	 * Invoked by Tcl whenever the user issues an "itcl::find objects" command
	 * to query the list of known objects. Handles the following syntax:
	 * 
	 * find objects ?-class <className>? ?-isa <className>? ?<pattern>?
	 * 
	 * Will raise a TclException to indicate failure.
	 * ------------------------------------------------------------------------
	 */
	public static class FindObjectsCmd implements CommandWithDispose {
		ItclObjectInfo info;

		FindObjectsCmd(ItclObjectInfo info) {
			this.info = info;
		}

		public void disposeCmd() {
			Util.ReleaseData(info);
		}

		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			Namespace activeNs = Namespace.getCurrentNamespace(interp);
			Namespace globalNs = Namespace.getGlobalNamespace(interp);
			boolean forceFullNames = false;

			String pattern = null;
			ItclClass classDefn = null;
			ItclClass isaDefn = null;

			String name = null, token = null;
			String cmdName = null;
			boolean newEntry, match, handledActiveNs;
			int pos;
			ItclObject contextObj;
			HashMap unique;
			Itcl_Stack search;
			WrappedCommand wcmd, originalCmd;
			Namespace ns;
			TclObject obj;
			TclObject result = TclList.newInstance();

			// Parse arguments:
			// objects ?-class <className>? ?-isa <className>? ?<pattern>?

			pos = 0;
			while (++pos < objv.length) {
				token = objv[pos].toString();
				if (token.length() == 0 || token.charAt(0) != '-') {
					if (pattern == null) {
						pattern = token;
						forceFullNames = (pattern.indexOf("::") != -1);
					} else {
						break;
					}
				} else if ((pos + 1 < objv.length) && (token.equals("-class"))) {
					name = objv[pos + 1].toString();
					classDefn = Class.FindClass(interp, name, true);
					if (classDefn == null) {
						throw new TclException(interp, interp.getResult()
								.toString());
					}
					pos++;
				} else if ((pos + 1 < objv.length) && (token.equals("-isa"))) {
					name = objv[pos + 1].toString();
					isaDefn = Class.FindClass(interp, name, true);
					if (isaDefn == null) {
						throw new TclException(interp, interp.getResult()
								.toString());
					}
					pos++;
				}

				// Last token? Take it as the pattern, even if it starts
				// with a "-". This allows us to match object names that
				// start with "-".

				else if (pos == objv.length - 1 && pattern == null) {
					pattern = token;
					forceFullNames = (pattern.indexOf("::") != -1);
				} else {
					break;
				}
			}

			if (pos < objv.length) {
				throw new TclNumArgsException(interp, 1, objv,
						"?-class className? ?-isa className? ?pattern?");
			}

			// Search through all commands in the current namespace first,
			// in the global namespace next, then in all child namespaces
			// in this interpreter. If we find any commands that
			// represent objects, report them.

			search = new Itcl_Stack();
			Util.InitStack(search);
			Util.PushStack(globalNs, search);
			Util.PushStack(activeNs, search); // last in, first out!

			unique = new HashMap();

			handledActiveNs = false;
			while (Util.GetStackSize(search) > 0) {
				ns = (Namespace) Util.PopStack(search);
				if (ns == activeNs && handledActiveNs) {
					continue;
				}

				for (Iterator iter = ns.cmdTable.entrySet().iterator(); iter
						.hasNext();) {
					Map.Entry entry = (Map.Entry) iter.next();
					String key = (String) entry.getKey();
					wcmd = (WrappedCommand) entry.getValue();

					if (Objects.IsObject(wcmd)) {
						originalCmd = Namespace.getOriginalCommand(wcmd);
						if (originalCmd != null) {
							wcmd = originalCmd;
						}
						contextObj = Objects.GetContextFromObject(wcmd);

						// Report full names if:
						// - the pattern has namespace qualifiers
						// - the class namespace is not in the current namespace
						// - the class's object creation command is imported
						// from
						// another namespace.
						//
						// Otherwise, report short names.

						if (forceFullNames || ns != activeNs
								|| originalCmd != null) {
							cmdName = interp.getCommandFullName(wcmd);
							obj = TclString.newInstance(cmdName);
						} else {
							cmdName = interp.getCommandName(wcmd);
							obj = TclString.newInstance(cmdName);
						}

						newEntry = (unique.put(wcmd, "") == null);

						match = false;
						if (newEntry
								&& (pattern == null || tcl.lang.Util
										.stringMatch(cmdName, pattern))) {
							if (classDefn == null
									|| (contextObj.classDefn == classDefn)) {
								if (isaDefn == null) {
									match = true;
								} else {
									if (contextObj.classDefn.heritage
											.get(isaDefn) != null) {
										match = true;
									}
								}
							}
						}

						if (match) {
							TclList.append(interp, result, obj);
						} else {
							// Tcl_DecrRefCount(objPtr); // throw away the name
						}
					}
				}
				handledActiveNs = true; // don't process the active namespace
				// twice

				// Push any child namespaces onto the stack and continue
				// the search in those namespaces.

				for (Iterator iter = ns.childTable.entrySet().iterator(); iter
						.hasNext();) {
					Map.Entry entry = (Map.Entry) iter.next();
					// String key = (String) entry.getKey();
					Namespace child = (Namespace) entry.getValue();

					Util.PushStack(child, search);
				}
			}
			unique.clear();
			Util.DeleteStack(search);

			interp.setResult(result);
		}
	} // end class FindObjectsCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ProtectionCmd -> Cmds.ProtectionCmd.cmdProc
	 * 
	 * Invoked by Tcl whenever the user issues a protection setting command like
	 * "public" or "private". Creates commands and variables, and assigns a
	 * protection level to them. Protection levels are defined as follows:
	 * 
	 * public => accessible from any namespace protected => accessible from
	 * selected namespaces private => accessible only in the namespace where it
	 * was defined
	 * 
	 * Handles the following syntax:
	 * 
	 * public <command> ?<arg> <arg>...?
	 * 
	 * Will raise a TclException to indicate failure.
	 * ------------------------------------------------------------------------
	 */
	public static class ProtectionCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			// As far as I can tell, this function is not used and
			// Itcl_ClassProtectionCmd used instead.
			throw new TclRuntimeError("unused function");
		}
	} // end class ProtectionCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_DelClassCmd -> Cmds.DelClassCmd.cmdProc
	 * 
	 * Part of the "delete" ensemble. Invoked by Tcl whenever the user issues a
	 * "delete class" command to delete classes. Handles the following syntax:
	 * 
	 * delete class <name> ?<name>...?
	 * 
	 * Will raise a TclException to indicate failure.
	 * ------------------------------------------------------------------------
	 */

	static class DelClassCmd implements CommandWithDispose {
		ItclObjectInfo info;

		DelClassCmd(ItclObjectInfo info) {
			this.info = info;
		}

		public void disposeCmd() {
			Util.ReleaseData(info);
		}

		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			int i;
			String name;
			ItclClass cdefn;

			// Since destroying a base class will destroy all derived
			// classes, calls like "destroy class Base Derived" could
			// fail. Break this into two passes: first check to make
			// sure that all classes on the command line are valid,
			// then delete them.

			for (i = 1; i < objv.length; i++) {
				name = objv[i].toString();
				cdefn = Class.FindClass(interp, name, true);
				if (cdefn == null) {
					throw new TclException(interp, interp.getResult()
							.toString());
				}
			}

			for (i = 1; i < objv.length; i++) {
				name = objv[i].toString();
				cdefn = Class.FindClass(interp, name, false);
				if (cdefn != null) {
					interp.resetResult();
					Class.DeleteClass(interp, cdefn);
				}
			}
			interp.resetResult();
		}
	} // end class DelClassCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_DelObjectCmd -> Cmds.DelObjectCmd.cmdProc
	 * 
	 * Part of the "delete" ensemble. Invoked by Tcl whenever the user issues a
	 * "delete object" command to delete [incr Tcl] objects. Handles the
	 * following syntax:
	 * 
	 * delete object <name> ?<name>...?
	 * 
	 * Will raise a TclException to indicate failure.
	 * ------------------------------------------------------------------------
	 */

	static class DelObjectCmd implements CommandWithDispose {
		ItclObjectInfo info;

		DelObjectCmd(ItclObjectInfo info) {
			this.info = info;
		}

		public void disposeCmd() {
			Util.ReleaseData(info);
		}

		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			int i;
			String name;
			ItclObject contextObj;

			// Scan through the list of objects and attempt to delete them.
			// If anything goes wrong (i.e., destructors fail), then
			// abort with an error.

			for (i = 1; i < objv.length; i++) {
				name = objv[i].toString();
				contextObj = Objects.FindObject(interp, name);

				if (contextObj == null) {
					throw new TclException(interp, "object \"" + name
							+ "\" not found");
				}

				Objects.DeleteObject(interp, contextObj);
			}
		}
	} // end class DelObjectCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ScopeCmd -> Cmds.ScopeCmd.cmdProc
	 * 
	 * Invoked by Tcl whenever the user issues a "scope" command to create a
	 * fully qualified variable name. Handles the following syntax:
	 * 
	 * scope <variable>
	 * 
	 * If the input string is already fully qualified (starts with "::"), then
	 * this procedure does nothing. Otherwise, it looks for a data member called
	 * <variable> and returns its fully qualified name. If the <variable> is a
	 * common data member, this procedure returns a name of the form:
	 * 
	 * ::namesp::namesp::class::variable
	 * 
	 * If the <variable> is an instance variable, this procedure returns a name
	 * of the form:
	 * 
	 * @itcl ::namesp::namesp::object variable
	 * 
	 * This kind of scoped value is recognized by the Itcl_ScopedVarResolver
	 * proc, which handles variable resolution for the entire interpreter.
	 * 
	 * Will raise a TclException to indicate failure.
	 * ------------------------------------------------------------------------
	 */
	public static class ScopeCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			Namespace contextNs = Namespace.getCurrentNamespace(interp);
			String openParen = null;
			int openParenStart, openParenEnd;

			int p;
			String token;
			ItclClass contextClass;
			ItclObject contextObj;
			ItclObjectInfo info;
			CallFrame frame;
			ItclVarLookup vlookup;
			TclObject obj, list;
			Var var;

			if (objv.length != 2) {
				throw new TclNumArgsException(interp, 1, objv, "varname");
			}

			// If this looks like a fully qualified name already,
			// then return it as is.

			token = objv[1].toString();
			if (token.startsWith("::")) {
				interp.setResult(objv[1]);
				return;
			}

			// If the variable name is an array reference, pick out
			// the array name and use that for the lookup operations
			// below.

			openParenStart = openParenEnd = -1;
			for (p = 0; p < token.length(); p++) {
				if (token.charAt(p) == '(') {
					openParenStart = p;
				} else if (token.charAt(p) == ')' && openParenStart != -1) {
					openParenEnd = p;
					break;
				}
			}
			if (openParenStart != -1 && openParenEnd != -1) {
				openParen = token.substring(openParenStart, openParenEnd + 1);
				token = token.substring(0, openParenStart);
			}

			// Figure out what context we're in. If this is a class,
			// then look up the variable in the class definition.
			// If this is a namespace, then look up the variable in its
			// varTable. Note that the normal Itcl_GetContext function
			// returns an error if we're not in a class context, so we
			// perform a similar function here, the hard way.
			//
			// TRICKY NOTE: If this is an array reference, we'll get
			// the array variable as the variable name. We must be
			// careful to add the index (everything from openParen
			// onward) as well.

			if (Class.IsClassNamespace(contextNs)) {
				contextClass = Class.GetClassFromNamespace(contextNs);

				vlookup = (ItclVarLookup) contextClass.resolveVars.get(token);
				if (vlookup == null) {
					throw new TclException(interp, "variable \"" + token
							+ "\" not found in class \""
							+ contextClass.fullname + "\"");
				}

				if ((vlookup.vdefn.member.flags & ItclInt.COMMON) != 0) {
					StringBuffer buffer = new StringBuffer(64);
					buffer.append(vlookup.vdefn.member.fullname);
					if (openParen != null) {
						buffer.append(openParen);
						openParen = null;
					}
					interp.setResult(buffer.toString());
					return;
				}

				// If this is not a common variable, then we better have
				// an object context. Return the name "@itcl object variable".

				frame = Migrate.GetCallFrame(interp, 0);
				info = contextClass.info;

				contextObj = (ItclObject) info.contextFrames.get(frame);
				if (contextObj == null) {
					throw new TclException(interp, "can't scope variable \""
							+ token + "\": missing object context\"");
				}

				list = TclList.newInstance();
				TclList.append(interp, list, TclString.newInstance("@itcl"));

				TclList.append(interp, list, TclString.newInstance(interp
						.getCommandFullName(contextObj.w_accessCmd)));

				StringBuffer buffer = new StringBuffer(64);
				buffer.append(vlookup.vdefn.member.fullname);

				if (openParen != null) {
					buffer.append(openParen);
					openParen = null;
				}

				TclList.append(interp, list, TclString.newInstance(buffer
						.toString()));

				interp.setResult(list);
			}

			// We must be in an ordinary namespace context. Resolve
			// the variable using Tcl_FindNamespaceVar.
			//
			// TRICKY NOTE: If this is an array reference, we'll get
			// the array variable as the variable name. We must be
			// careful to add the index (everything from openParen
			// onward) as well.

			else {
				StringBuffer buffer = new StringBuffer(64);

				var = Namespace.findNamespaceVar(interp, token, contextNs,
						TCL.NAMESPACE_ONLY);

				if (var == null) {
					throw new TclException(interp, "variable \"" + token
							+ "\" not found in namespace \""
							+ contextNs.fullName + "\"");
				}

				String fname = Var.getVariableFullName(interp, var);
				buffer.append(fname);

				if (openParen != null) {
					buffer.append(openParen);
					openParen = null;
				}

				interp.setResult(buffer.toString());
			}

			return;
		}
	} // end class ScopeCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_CodeCmd -> Cmds.CodeCmd.cmdProc
	 * 
	 * Invoked by Tcl whenever the user issues a "code" command to create a
	 * scoped command string. Handles the following syntax:
	 * 
	 * code ?-namespace foo? arg ?arg arg ...?
	 * 
	 * Unlike the scope command, the code command DOES NOT look for scoping
	 * information at the beginning of the command. So scopes will nest in the
	 * code command.
	 * 
	 * The code command is similar to the "namespace code" command in Tcl, but
	 * it preserves the list structure of the input arguments, so it is a lot
	 * more useful.
	 * 
	 * Will raise a TclException to indicate failure.
	 * ------------------------------------------------------------------------
	 */

	static class CodeCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			Namespace contextNs = Namespace.getCurrentNamespace(interp);

			int pos;
			String token;
			TclObject list, obj;

			// Handle flags like "-namespace"...

			for (pos = 1; pos < objv.length; pos++) {
				token = objv[pos].toString();
				if (token.length() < 2 || token.charAt(0) != '-') {
					break;
				}

				if (token.equals("-namespace")) {
					if (objv.length == 2) {
						throw new TclNumArgsException(interp, 1, objv,
								"?-namespace name? command ?arg arg...?");
					} else {
						token = objv[pos + 1].toString();
						contextNs = Namespace.findNamespace(interp, token,
								null, TCL.LEAVE_ERR_MSG);

						if (contextNs == null) {
							throw new TclException(interp, interp.getResult()
									.toString());
						}
						pos++;
					}
				} else if (token.equals("--")) {
					pos++;
					break;
				} else {
					throw new TclException(interp, "bad option \"" + token
							+ "\": should be -namespace or --");
				}
			}

			if (objv.length < 2) {
				throw new TclNumArgsException(interp, 1, objv,
						"?-namespace name? command ?arg arg...?");
			}

			// Now construct a scoped command by integrating the
			// current namespace context, and appending the remaining
			// arguments AS A LIST...

			list = TclList.newInstance();

			TclList.append(interp, list, TclString.newInstance("namespace"));
			TclList.append(interp, list, TclString.newInstance("inscope"));

			if (contextNs == Namespace.getGlobalNamespace(interp)) {
				obj = TclString.newInstance("::");
			} else {
				obj = TclString.newInstance(contextNs.fullName);
			}
			TclList.append(interp, list, obj);

			if (objv.length - pos == 1) {
				obj = objv[pos];
			} else {
				obj = TclList.newInstance();
				for (int i = pos; i < objv.length; i++) {
					TclList.append(interp, obj, objv[i]);
				}
			}
			TclList.append(interp, list, obj);

			interp.setResult(list);
		}
	} // end class CodeCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_StubCreateCmd -> Cmds.StubCreateCmd.cmdProc
	 * 
	 * Invoked by Tcl whenever the user issues a "stub create" command to create
	 * an autoloading stub for imported commands. Handles the following syntax:
	 * 
	 * stub create <name>
	 * 
	 * Creates a command called <name>. Executing this command will cause the
	 * real command <name> to be autoloaded.
	 * ------------------------------------------------------------------------
	 */
	public static class StubCreateCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			String cmdName;
			WrappedCommand wcmd;

			if (objv.length != 2) {
				throw new TclNumArgsException(interp, 1, objv, "name");
			}
			cmdName = objv[1].toString();

			// Create a stub command with the characteristic ItclDeleteStub
			// procedure. That way, we can recognize this command later
			// on as a stub. Save the cmd token in the created command,
			// instance so we can get the full name of this command later on.

			interp.createCommand(cmdName, new HandleStubCmd());

			wcmd = Namespace.findCommand(interp, cmdName, null,
					TCL.NAMESPACE_ONLY);
			((HandleStubCmd) wcmd.cmd).wcmd = wcmd;
		}
	} // end class StubCreateCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_StubExistsCmd -> Cmds.StubExistsCmd.cmdProc
	 * 
	 * Invoked by Tcl whenever the user issues a "stub exists" command to see if
	 * an existing command is an autoloading stub. Handles the following syntax:
	 * 
	 * stub exists <name>
	 * 
	 * Looks for a command called <name> and checks to see if it is an
	 * autoloading stub. Will set a boolean result as the interp result.
	 * ------------------------------------------------------------------------
	 */
	public static class StubExistsCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			String cmdName;
			WrappedCommand wcmd;

			if (objv.length != 2) {
				throw new TclNumArgsException(interp, 1, objv, "name");
			}
			cmdName = objv[1].toString();

			wcmd = Namespace.findCommand(interp, cmdName, null, 0);

			if (wcmd != null && Cmds.IsStub(wcmd)) {
				interp.setResult(true);
			} else {
				interp.setResult(false);
			}
		}
	} // end class StubExistsCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_IsStub -> Cmds.IsStub
	 * 
	 * Checks the given Tcl command to see if it represents an autoloading stub
	 * created by the "stub create" command. Returns true if the command is
	 * indeed a stub.
	 * ------------------------------------------------------------------------
	 */

	static boolean IsStub(WrappedCommand wcmd) // command being tested
	{
		// This may be an imported command, but don't try to get the
		// original. Just check to see if this particular command
		// is a stub. If we really want the original command, we'll
		// find it at a higher level.

		if (wcmd.cmd instanceof HandleStubCmd) {
			return true;
		}
		return false;
	}

	/*
	 * ------------------------------------------------------------------------
	 * ItclHandleStubCmd -> Cmds.HandleStubCmd.cmdProc
	 * 
	 * Invoked by Tcl to handle commands created by "stub create". Calls
	 * "auto_load" with the full name of the current command to trigger
	 * autoloading of the real implementation. Then, calls the command to handle
	 * its function. If successful, this command will set the interpreter result
	 * with the result from the real implementation. Will raise a TclException
	 * to indicate failure.
	 * ------------------------------------------------------------------------
	 */
	public static class HandleStubCmd implements CommandWithDispose {
		WrappedCommand wcmd;

		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			int loaded;
			String cmdName;
			TclObject obj;
			TclObject cmdline;
			TclObject[] cmdlinev;

			cmdName = interp.getCommandFullName(wcmd);

			// Try to autoload the real command for this stub.

			interp.eval("::auto_load \"" + cmdName + "\"");

			obj = interp.getResult();

			boolean err = false;
			loaded = 0;
			try {
				loaded = TclInteger.getInt(interp, obj);
			} catch (TclException ex) {
				err = true;
			}
			if (err || loaded != 1) {
				interp.resetResult();
				throw new TclException(interp, "can't autoload \"" + cmdName
						+ "\"");
			}

			// At this point, the real implementation has been loaded.
			// Invoke the command again with the arguments passed in.

			cmdline = Util.CreateArgs(interp, cmdName, objv, 1);
			cmdlinev = TclList.getElements(interp, cmdline);
			interp.resetResult();
			Util.EvalArgs(interp, cmdlinev);
		}

		public void disposeCmd() {
			Cmds.ItclDeleteStub(null);
		}

	} // end class HandleStubCmd

	/*
	 * ------------------------------------------------------------------------
	 * ItclDeleteStub -> Cmds.DeleteStub
	 * 
	 * Invoked by Tcl whenever a stub command is deleted. This procedure does
	 * nothing, but its presence identifies a command as a stub.
	 * ------------------------------------------------------------------------
	 */

	static void ItclDeleteStub(Object cdata) // not used
	{
		// do nothing
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_IsObjectCmd -> Cmds.IsObjectCmd.cmdProc
	 * 
	 * Invoked by Tcl whenever the user issues an "itcl::is object" command to
	 * test whether the argument is an object or not. syntax:
	 * 
	 * itcl::is object ?-class classname? commandname
	 * 
	 * Sets interpreter result to 1 if it is an object, 0 otherwise
	 * ------------------------------------------------------------------------
	 */
	public static class IsObjectCmd implements CommandWithDispose {
		ItclObjectInfo info;

		IsObjectCmd(ItclObjectInfo info) {
			this.info = info;
		}

		public void disposeCmd() {
			Util.ReleaseData(info);
		}

		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			boolean classFlag = false;
			int idx = 0;
			String name = null;
			String cname;
			String cmdName;
			String token;
			WrappedCommand wcmd;
			Namespace contextNs = null;
			ItclClass classDefn = null;
			ItclObject contextObj;

			// Handle the arguments.
			// objc needs to be either:
			// 2 itcl::is object commandname
			// 4 itcl::is object -class classname commandname

			if (objv.length != 2 && objv.length != 4) {
				throw new TclNumArgsException(interp, 1, objv,
						"?-class classname? commandname");
			}

			// Parse the command args. Look for the -class
			// keyword.

			for (idx = 1; idx < objv.length; idx++) {
				token = objv[idx].toString();

				if (token.equals("-class")) {
					cname = objv[idx + 1].toString();
					classDefn = Class.FindClass(interp, cname, false);

					if (classDefn == null) {
						throw new TclException(interp, interp.getResult()
								.toString());
					}

					idx++;
					classFlag = true;
				} else {
					name = objv[idx].toString();
				}
			} // end for objc loop

			if (name == null) {
				throw new TclRuntimeError("name not assigned in objc loop");
			}

			// The object name may be a scoped value of the form
			// "namespace inscope <namesp> <command>". If it is,
			// decode it.

			Util.DecodeScopedCommandResult res = Util.DecodeScopedCommand(
					interp, name);
			contextNs = res.rNS;
			cmdName = res.rCmd;

			wcmd = Namespace.findCommand(interp, cmdName, contextNs, 0);

			// Need the null test, or the test will fail if cmd is null

			if (wcmd == null || !Objects.IsObject(wcmd)) {
				interp.setResult(false);
				return;
			}

			// Handle the case when the -class flag is given

			if (classFlag) {
				contextObj = Objects.GetContextFromObject(wcmd);
				if (!Objects.ObjectIsa(contextObj, classDefn)) {
					interp.setResult(false);
					return;
				}
			}

			// Got this far, so assume that it is a valid object

			interp.setResult(true);
			return;
		}
	} // end class IsObjectCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_IsClassCmd -> Cmds.IsClassCmd.cmdProc
	 * 
	 * Invoked by Tcl whenever the user issues an "itcl::is class" command to
	 * test whether the argument is an itcl class or not syntax:
	 * 
	 * itcl::is class commandname
	 * 
	 * Sets interpreter result to 1 if it is a class, 0 otherwise
	 * ------------------------------------------------------------------------
	 */
	public static class IsClassCmd implements CommandWithDispose {
		ItclObjectInfo info;

		IsClassCmd(ItclObjectInfo info) {
			this.info = info;
		}

		public void disposeCmd() {
			Util.ReleaseData(info);
		}

		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			String cname;
			String name;
			ItclClass classDefn = null;
			Namespace contextNs = null;

			// Need itcl::is class classname

			if (objv.length != 2) {
				throw new TclNumArgsException(interp, 1, objv, "commandname");
			}

			name = objv[1].toString();

			// The object name may be a scoped value of the form
			// "namespace inscope <namesp> <command>". If it is,
			// decode it.

			Util.DecodeScopedCommandResult res = Util.DecodeScopedCommand(
					interp, name);
			contextNs = res.rNS;
			cname = res.rCmd;

			classDefn = Class.FindClass(interp, cname, false);

			// If classDefn is null, then it wasn't found, hence it
			// isn't a class

			if (classDefn != null) {
				interp.setResult(true);
			} else {
				interp.setResult(false);
			}
		}
	} // end class IsClassCmd

} // end class Cmds

