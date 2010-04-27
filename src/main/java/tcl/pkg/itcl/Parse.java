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
 *  Procedures in this file support the new syntax for [incr Tcl]
 *  class definitions:
 *
 *    itcl::class <className> {
 *        inherit <base-class>...
 *
 *        constructor {<arglist>} ?{<init>}? {<body>}
 *        destructor {<body>}
 *
 *        method <name> {<arglist>} {<body>}
 *        proc <name> {<arglist>} {<body>}
 *        variable <name> ?<init>? ?<config>?
 *        common <name> ?<init>?
 *
 *        public <thing> ?<args>...?
 *        protected <thing> ?<args>...?
 *        private <thing> ?<args>...?
 *    }
 *
 * ========================================================================
 *  AUTHOR:  Michael J. McLennan
 *           Bell Labs Innovations for Lucent Technologies
 *           mmclennan@lucent.com
 *           http://www.tcltk.com/itcl
 *
 *     RCS:  $Id: Parse.java,v 1.2 2005/09/12 00:00:50 mdejong Exp $
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
import tcl.lang.Resolver;
import tcl.lang.TCL;
import tcl.lang.TclException;
import tcl.lang.TclList;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;
import tcl.lang.TclString;
import tcl.lang.Var;
import tcl.lang.WrappedCommand;

//
//  Info needed for public/protected/private commands:
//
class ProtectionCmdInfo {
	int pLevel; // protection level
	ItclObjectInfo info; // info regarding all known objects
}

class Parse {

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ParseInit -> Parse.ParseInit
	 * 
	 * Invoked by Itcl_Init() whenever a new interpeter is created to add [incr
	 * Tcl] facilities. Adds the commands needed to parse class definitions.
	 * Will raise a TclException if anything goes wrong.
	 * ------------------------------------------------------------------------
	 */
	public static void ParseInit(Interp interp, // interpreter to be updated
			ItclObjectInfo info) // info regarding all known objects
			throws TclException {
		Namespace parserNs;
		ProtectionCmdInfo pInfo;

		// Create the "itcl::parser" namespace used to parse class
		// definitions.

		parserNs = Namespace.createNamespace(interp, "::itcl::parser", null);

		if (parserNs == null) {
			throw new TclException(interp, "  (cannot initialize itcl parser)");
		}
		// We don't preserve the info argument here because it is not associated
		// with the namespace created above. The ::itcl::class command created
		// below holds a ref to the info object anyway.
		// Util.PreserveData(info);

		// Add commands for parsing class definitions.

		interp.createCommand("::itcl::parser::inherit", new ClassInheritCmd());

		interp.createCommand("::itcl::parser::constructor",
				new ClassConstructorCmd());

		interp.createCommand("::itcl::parser::destructor",
				new ClassDestructorCmd());

		interp.createCommand("::itcl::parser::method", new ClassMethodCmd());

		interp.createCommand("::itcl::parser::proc", new ClassProcCmd());

		interp.createCommand("::itcl::parser::common", new ClassCommonCmd());

		interp
				.createCommand("::itcl::parser::variable",
						new ClassVariableCmd());

		pInfo = new ProtectionCmdInfo();
		pInfo.pLevel = Itcl.PUBLIC;
		pInfo.info = info;

		interp.createCommand("::itcl::parser::public", new ClassProtectionCmd(
				pInfo));

		pInfo = new ProtectionCmdInfo();
		pInfo.pLevel = Itcl.PROTECTED;
		pInfo.info = info;

		interp.createCommand("::itcl::parser::protected",
				new ClassProtectionCmd(pInfo));

		pInfo = new ProtectionCmdInfo();
		pInfo.pLevel = Itcl.PRIVATE;
		pInfo.info = info;

		interp.createCommand("::itcl::parser::private", new ClassProtectionCmd(
				pInfo));

		// Set the runtime variable resolver for the parser namespace,
		// to control access to "common" data members while parsing
		// the class definition.

		Resolver resolver = new ParseVarResolverImpl();
		Namespace.setNamespaceResolver(parserNs, resolver);

		// Install the "class" command for defining new classes.

		interp.createCommand("::itcl::class", new Parse.ClassCmd(info));
		Util.PreserveData(info);
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ClassCmd -> Parse.ClassCmd.cmdProc
	 * 
	 * Invoked by Tcl whenever the user issues an "itcl::class" command to
	 * specify a class definition. Handles the following syntax:
	 * 
	 * itcl::class <className> { inherit <base-class>...
	 * 
	 * constructor {<arglist>} ?{<init>}? {<body>} destructor {<body>}
	 * 
	 * method <name> {<arglist>} {<body>} proc <name> {<arglist>} {<body>}
	 * variable <varname> ?<init>? ?<config>? common <varname> ?<init>?
	 * 
	 * public <args>... protected <args>... private <args>... }
	 * 
	 * ------------------------------------------------------------------------
	 */
	public static class ClassCmd implements CommandWithDispose {
		ItclObjectInfo info;

		ClassCmd(ItclObjectInfo info) {
			this.info = info;
		}

		public void disposeCmd() {
			Util.ReleaseData(info);
		}

		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			String className;
			Namespace parserNs;
			ItclClass cdefn;
			CallFrame frame;

			if (objv.length != 3) {
				throw new TclNumArgsException(interp, 1, objv,
						"name { definition }");
			}
			className = objv[1].toString();
			if (className.length() == 0) {
				throw new TclException(interp, "invalid class name \"\"");
			}

			// Find the namespace to use as a parser for the class definition.
			// If for some reason it is destroyed, bail out here.

			parserNs = Namespace.findNamespace(interp, "::itcl::parser", null,
					TCL.LEAVE_ERR_MSG);

			if (parserNs == null) {
				interp
						.addErrorInfo("\n    (while parsing class definition for \""
								+ className + "\")");
				throw new TclException(interp, interp.getResult().toString());
			}

			// Try to create the specified class and its namespace.

			cdefn = Class.CreateClass(interp, className, info);

			// Import the built-in commands from the itcl::builtin namespace.
			// Do this before parsing the class definition, so methods/procs
			// can override the built-in commands.

			try {
				Namespace.importList(interp, cdefn.namesp,
						"::itcl::builtin::*", true);
			} catch (TclException ex) {
				interp
						.addErrorInfo("\n    (while installing built-in commands for class \""
								+ className + "\")");

				Namespace.deleteNamespace(cdefn.namesp);
				throw ex;
			}

			// Push this class onto the class definition stack so that it
			// becomes the current context for all commands in the parser.
			// Activate the parser and evaluate the class definition.

			Util.PushStack(cdefn, info.cdefnStack);

			TclException pex = null;
			boolean pushed = false;

			try {
				frame = ItclAccess.newCallFrame(interp);
				Namespace.pushCallFrame(interp, frame, parserNs, false);
				pushed = true;
				interp.eval(objv[2].toString());
			} catch (TclException ex) {
				pex = ex;
			} finally {
				if (pushed) {
					Namespace.popCallFrame(interp);
				}
			}

			Util.PopStack(info.cdefnStack);

			if (pex != null) {
				interp.addErrorInfo("\n    (class \"" + className
						+ "\" body line " + interp.getErrorLine() + ")");

				Namespace.deleteNamespace(cdefn.namesp);
				throw pex;
			}

			// At this point, parsing of the class definition has succeeded.
			// Add built-in methods such as "configure" and "cget"--as long
			// as they don't conflict with those defined in the class.

			try {
				BiCmds.InstallBiMethods(interp, cdefn);
			} catch (TclException ex) {
				Namespace.deleteNamespace(cdefn.namesp);
				throw ex;
			}

			// Build the name resolution tables for all data members.

			Class.BuildVirtualTables(cdefn);

			interp.resetResult();
		}
	} // end class ClassCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ClassInheritCmd -> Parse.ClassInheritCmd.cmdProc
	 * 
	 * Invoked by Tcl during the parsing of a class definition whenever the
	 * "inherit" command is invoked to define one or more base classes. Handles
	 * the following syntax:
	 * 
	 * inherit <baseclass> ?<baseclass>...?
	 * 
	 * ------------------------------------------------------------------------
	 */
	public static class ClassInheritCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			ItclObjectInfo info = (ItclObjectInfo) interp
					.getAssocData(ItclInt.INTERP_DATA);
			ItclClass cdefn = (ItclClass) Util.PeekStack(info.cdefnStack);

			boolean newEntry = true;
			String token;
			Itcl_ListElem elem, elem2;
			ItclClass cd, baseCdefn, badCd;
			ItclHierIter hier;
			Itcl_Stack stack;
			CallFrame frame;

			if (objv.length < 2) {
				throw new TclNumArgsException(interp, 1, objv,
						"class ?class...?");
			}

			// In "inherit" statement can only be included once in a
			// class definition.

			elem = Util.FirstListElem(cdefn.bases);
			if (elem != null) {
				StringBuffer msg = new StringBuffer(64);
				msg.append("inheritance \"");

				while (elem != null) {
					cd = (ItclClass) Util.GetListValue(elem);
					msg.append(cd.name);
					msg.append(" ");

					elem = Util.NextListElem(elem);
				}

				msg.append("\" already defined for class \"");
				msg.append(cdefn.fullname);
				msg.append("\"");

				throw new TclException(interp, msg.toString());
			}

			// Validate each base class and add it to the "bases" list.

			frame = ItclAccess.newCallFrame(interp);
			Namespace.pushCallFrame(interp, frame, cdefn.namesp.parent, false);

			for (int i = 1; i < objv.length; i++) {

				// Make sure that the base class name is known in the
				// parent namespace (currently active). If not, try
				// to autoload its definition.

				token = objv[i].toString();
				baseCdefn = Class.FindClass(interp, token, true);
				if (baseCdefn == null) {
					String errmsg = interp.getResult().toString();
					interp.resetResult();

					StringBuffer msg = new StringBuffer(64);
					msg.append("cannot inherit from \"");
					msg.append(token);
					msg.append("\"");

					if (errmsg.length() > 0) {
						msg.append(" (");
						msg.append(errmsg);
						msg.append(")");
					}

					// goto inheritError;
					ClassInheritCmdInheritError(interp, cdefn, msg.toString());
				}

				// Make sure that the base class is not the same as the
				// class that is being built.

				if (baseCdefn == cdefn) {
					// goto inheritError;
					ClassInheritCmdInheritError(interp, cdefn, "class \""
							+ cdefn.name + "\" cannot inherit from itself");
				}

				Util.AppendList(cdefn.bases, baseCdefn);
				Util.PreserveData(baseCdefn);
			}

			// Scan through the inheritance list to make sure that no
			// class appears twice.

			elem = Util.FirstListElem(cdefn.bases);
			while (elem != null) {
				elem2 = Util.NextListElem(elem);
				while (elem2 != null) {
					if (Util.GetListValue(elem) == Util.GetListValue(elem2)) {
						cd = (ItclClass) Util.GetListValue(elem);
						String msg = "class \"" + cdefn.fullname
								+ "\" cannot inherit base class \""
								+ cd.fullname + "\" more than once";
						// goto inheritError;
						ClassInheritCmdInheritError(interp, cdefn, msg);
					}
					elem2 = Util.NextListElem(elem2);
				}
				elem = Util.NextListElem(elem);
			}

			// Add each base class and all of its base classes into
			// the heritage for the current class. Along the way, make
			// sure that no class appears twice in the heritage.

			hier = new ItclHierIter();
			Class.InitHierIter(hier, cdefn);
			cd = Class.AdvanceHierIter(hier); // skip the class itself
			cd = Class.AdvanceHierIter(hier);
			while (cd != null) {
				// Map class def to the empty string in heritage table
				Object prev = cdefn.heritage.put(cd, "");
				newEntry = (prev == null);

				if (!newEntry) {
					break;
				}

				cd = Class.AdvanceHierIter(hier);
			}
			Class.DeleteHierIter(hier);

			// Same base class found twice in the hierarchy?
			// Then flag error. Show the list of multiple paths
			// leading to the same base class.

			if (!newEntry) {
				StringBuffer msg = new StringBuffer(64);

				badCd = cd;
				msg.append("class \"");
				msg.append(cdefn.fullname);
				msg.append("\" inherits base class \"");
				msg.append(badCd.fullname);
				msg.append("\" more than once:");

				cd = cdefn;
				stack = new Itcl_Stack();
				Util.InitStack(stack);
				Util.PushStack(cd, stack);

				// Show paths leading to bad base class

				while (Util.GetStackSize(stack) > 0) {
					cd = (ItclClass) Util.PopStack(stack);

					if (cd == badCd) {
						msg.append("\n  ");
						for (int i = 0; i < Util.GetStackSize(stack); i++) {
							if (Util.GetStackValue(stack, i) == null) {
								cd = (ItclClass) Util.GetStackValue(stack,
										i - 1);
								msg.append(cd.name);
								msg.append("->");
							}
						}
						msg.append(badCd.name);
					} else if (cd == null) {
						Util.PopStack(stack);
					} else {
						elem = Util.LastListElem(cd.bases);
						if (elem != null) {
							Util.PushStack(cd, stack);
							Util.PushStack(null, stack);
							while (elem != null) {
								Util.PushStack(Util.GetListValue(elem), stack);
								elem = Util.PrevListElem(elem);
							}
						}
					}
				}
				Util.DeleteStack(stack);
				// goto inheritError;
				ClassInheritCmdInheritError(interp, cdefn, msg.toString());
			}

			// At this point, everything looks good.
			// Finish the installation of the base classes. Update
			// each base class to recognize the current class as a
			// derived class.

			elem = Util.FirstListElem(cdefn.bases);
			while (elem != null) {
				baseCdefn = (ItclClass) Util.GetListValue(elem);

				Util.AppendList(baseCdefn.derived, cdefn);
				Util.PreserveData(cdefn);

				elem = Util.NextListElem(elem);
			}

			Namespace.popCallFrame(interp);
		}
	} // end class ClassInheritCmd

	// Helper function to simulate inheritError label as goto target.
	// This is invoked to tear down the inherit data structures
	// and leave the calling function via an Exception.

	static void ClassInheritCmdInheritError(Interp interp, ItclClass cdefn,
			String exmsg) throws TclException {
		Itcl_ListElem elem;

		Namespace.popCallFrame(interp);

		elem = Util.FirstListElem(cdefn.bases);
		while (elem != null) {
			ItclClass baseDefn = (ItclClass) Util.GetListValue(elem);
			Util.ReleaseData(baseDefn);
			elem = Util.DeleteListElem(elem);
		}

		throw new TclException(interp, exmsg);
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ClassProtectionCmd -> Parse.ClassProtectionCmd.cmdProc
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
	 * Will raise a TclException if anything goes wrong.
	 * ------------------------------------------------------------------------
	 */
	public static class ClassProtectionCmd implements CommandWithDispose {
		ProtectionCmdInfo pInfo;

		public ClassProtectionCmd(ProtectionCmdInfo pInfo) {
			this.pInfo = pInfo;
		}

		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			int result;
			int oldLevel;

			if (objv.length < 2) {
				throw new TclNumArgsException(interp, 1, objv,
						"command ?arg arg...?");
			}

			oldLevel = Util.Protection(interp, pInfo.pLevel);

			try {

				if (objv.length == 2) {
					interp.eval(objv[1].toString());
				} else {
					// Eval rest of args without the first arg
					TclObject cmdline = Util.CreateArgs(interp, null, objv, 1);
					TclObject[] cmdlinev = TclList.getElements(interp, cmdline);
					Util.EvalArgs(interp, cmdlinev);
				}

				// Removed TCL_BREAK, TCL_CONTINUE error since eval() raises
				// them

			} catch (TclException ex) {
				interp.addErrorInfo("\n    (" + objv[0].toString()
						+ " body line " + interp.getErrorLine() + ")");
			} finally {
				Util.Protection(interp, oldLevel);
			}
		}

		// This dispose does not actually do anything since
		// FreeParserCommandData would only deallocate memory

		public void disposeCmd() {
			Parse.FreeParserCommandData(pInfo);
		}

	} // end class ClassProtectionCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ClassConstructorCmd -> Parse.ClassConstructorCmd.cmdProc
	 * 
	 * Invoked by Tcl during the parsing of a class definition whenever the
	 * "constructor" command is invoked to define the constructor for an object.
	 * Handles the following syntax:
	 * 
	 * constructor <arglist> ?<init>? <body>
	 * 
	 * ------------------------------------------------------------------------
	 */
	public static class ClassConstructorCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			ItclObjectInfo info = (ItclObjectInfo) interp
					.getAssocData(ItclInt.INTERP_DATA);
			ItclClass cdefn = (ItclClass) Util.PeekStack(info.cdefnStack);

			String name, arglist, body;

			if (objv.length < 3 || objv.length > 4) {
				throw new TclNumArgsException(interp, 1, objv,
						"args ?init? body");
			}

			name = objv[0].toString();
			if (cdefn.functions.get(name) != null) {
				throw new TclException(interp, "\"" + name
						+ "\" already defined in class \"" + cdefn.fullname
						+ "\"");
			}

			// If there is an object initialization statement, pick this
			// out and take the last argument as the constructor body.

			arglist = objv[1].toString();
			if (objv.length == 3) {
				body = objv[2].toString();
			} else {
				cdefn.initCode = objv[2];
				cdefn.initCode.preserve();
				body = objv[3].toString();
			}

			Methods.CreateMethod(interp, cdefn, name, arglist, body);
		}
	} // end class ClassConstructorCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ClassDestructorCmd -> Parse.ClassDestructorCmd.cmdProc
	 * 
	 * Invoked by Tcl during the parsing of a class definition whenever the
	 * "destructor" command is invoked to define the destructor for an object.
	 * Handles the following syntax:
	 * 
	 * destructor <body>
	 * 
	 * ------------------------------------------------------------------------
	 */
	public static class ClassDestructorCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			ItclObjectInfo info = (ItclObjectInfo) interp
					.getAssocData(ItclInt.INTERP_DATA);
			ItclClass cdefn = (ItclClass) Util.PeekStack(info.cdefnStack);

			String name, body;

			if (objv.length != 2) {
				throw new TclNumArgsException(interp, 1, objv, "body");
			}

			name = objv[0].toString();
			body = objv[1].toString();

			if (cdefn.functions.get(name) != null) {
				throw new TclException(interp, "\"" + name
						+ "\" already defined in class \"" + cdefn.fullname
						+ "\"");
			}

			Methods.CreateMethod(interp, cdefn, name, null, body);
		}
	} // end class ClassDestructorCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ClassMethodCmd -> Parse.ClassMethodCmd.cmdProc
	 * 
	 * Invoked by Tcl during the parsing of a class definition whenever the
	 * "method" command is invoked to define an object method. Handles the
	 * following syntax:
	 * 
	 * method <name> ?<arglist>? ?<body>?
	 * 
	 * ------------------------------------------------------------------------
	 */
	public static class ClassMethodCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			ItclObjectInfo info = (ItclObjectInfo) interp
					.getAssocData(ItclInt.INTERP_DATA);
			ItclClass cdefn = (ItclClass) Util.PeekStack(info.cdefnStack);

			String name, arglist, body;

			if (objv.length < 2 || objv.length > 4) {
				throw new TclNumArgsException(interp, 1, objv,
						"name ?args? ?body?");
			}

			name = objv[1].toString();

			arglist = null;
			body = null;
			if (objv.length >= 3) {
				arglist = objv[2].toString();
			}
			if (objv.length == 4) {
				body = objv[3].toString();
			}

			Methods.CreateMethod(interp, cdefn, name, arglist, body);
		}
	} // end class ClassMethodCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ClassProcCmd -> Parse.ClassProcCmd.cmdProc
	 * 
	 * Invoked by Tcl during the parsing of a class definition whenever the
	 * "proc" command is invoked to define a common class proc. A "proc" is like
	 * a "method", but only has access to "common" class variables. Handles the
	 * following syntax:
	 * 
	 * proc <name> ?<arglist>? ?<body>?
	 * 
	 * ------------------------------------------------------------------------
	 */
	public static class ClassProcCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			ItclObjectInfo info = (ItclObjectInfo) interp
					.getAssocData(ItclInt.INTERP_DATA);
			ItclClass cdefn = (ItclClass) Util.PeekStack(info.cdefnStack);

			String name, arglist, body;

			if (objv.length < 2 || objv.length > 4) {
				throw new TclNumArgsException(interp, 1, objv,
						"name ?args? ?body?");
			}

			name = objv[1].toString();

			arglist = null;
			body = null;
			if (objv.length >= 3) {
				arglist = objv[2].toString();
			}
			if (objv.length >= 4) {
				body = objv[3].toString();
			}

			Methods.CreateProc(interp, cdefn, name, arglist, body);
		}
	} // end class ClassProcCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ClassVariableCmd -> Parse.ClassVariableCmd.cmdProc
	 * 
	 * Invoked by Tcl during the parsing of a class definition whenever the
	 * "variable" command is invoked to define an instance variable. Handles the
	 * following syntax:
	 * 
	 * variable <varname> ?<init>? ?<config>?
	 * 
	 * ------------------------------------------------------------------------
	 */
	public static class ClassVariableCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			ItclObjectInfo info = (ItclObjectInfo) interp
					.getAssocData(ItclInt.INTERP_DATA);
			ItclClass cdefn = (ItclClass) Util.PeekStack(info.cdefnStack);

			int pLevel;
			ItclVarDefn vdefn;
			String name, init, config;

			pLevel = Util.Protection(interp, 0);

			if (pLevel == Itcl.PUBLIC) {
				if (objv.length < 2 || objv.length > 4) {
					throw new TclNumArgsException(interp, 1, objv,
							"name ?init? ?config?");
				}
			} else if ((objv.length < 2) || (objv.length > 3)) {
				throw new TclNumArgsException(interp, 1, objv, "name ?init?");
			}

			// Make sure that the variable name does not contain anything
			// goofy like a "::" scope qualifier.

			name = objv[1].toString();
			if (name.indexOf("::") != -1) {
				throw new TclException(interp, "bad variable name \"" + name
						+ "\"");
			}

			init = null;
			config = null;
			if (objv.length >= 3) {
				init = objv[2].toString();
			}
			if (objv.length >= 4) {
				config = objv[3].toString();
			}

			vdefn = Class.CreateVarDefn(interp, cdefn, name, init, config);
		}
	} // end class ClassVariableCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ClassCommonCmd -> Parse.ClassCommonCmd.cmdProc
	 * 
	 * Invoked by Tcl during the parsing of a class definition whenever the
	 * "common" command is invoked to define a variable that is common to all
	 * objects in the class. Handles the following syntax:
	 * 
	 * common <varname> ?<init>?
	 * 
	 * ------------------------------------------------------------------------
	 */
	public static class ClassCommonCmd implements Command {
		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			ItclObjectInfo info = (ItclObjectInfo) interp
					.getAssocData(ItclInt.INTERP_DATA);
			ItclClass cdefn = (ItclClass) Util.PeekStack(info.cdefnStack);

			String name, init;
			ItclVarDefn vdefn;
			Namespace ns;
			Var var;

			if ((objv.length < 2) || (objv.length > 3)) {
				throw new TclNumArgsException(interp, 1, objv, "varname ?init?");
			}

			// Make sure that the variable name does not contain anything
			// goofy like a "::" scope qualifier.

			name = objv[1].toString();
			if (name.indexOf("::") != -1) {
				throw new TclException(interp, "bad variable name \"" + name
						+ "\"");
			}

			init = null;
			if (objv.length >= 3) {
				init = objv[2].toString();
			}

			vdefn = Class.CreateVarDefn(interp, cdefn, name, init, null);
			vdefn.member.flags |= ItclInt.COMMON;

			// Create the variable in the namespace associated with the
			// class. Do this the hard way, to avoid the variable resolver
			// procedures. These procedures won't work until we rebuild
			// the virtual tables below.

			ns = cdefn.namesp;

			var = Migrate.NewVar();
			ItclAccess.createCommonVar(var, vdefn.member.name, ns, ns.varTable);

			ns.varTable.put(vdefn.member.name, var);

			// TRICKY NOTE: Make sure to rebuild the virtual tables for this
			// class so that this variable is ready to access. The variable
			// resolver for the parser namespace needs this info to find the
			// variable if the developer tries to set it within the class
			// definition.
			//
			// If an initialization value was specified, then initialize
			// the variable now.

			Class.BuildVirtualTables(cdefn);

			if (init != null) {
				TclObject val = interp.setVar(vdefn.member.name.toString(),
						TclString.newInstance(init), TCL.NAMESPACE_ONLY);
				if (val == null) {
					throw new TclException(interp,
							"cannot initialize common variable \""
									+ vdefn.member.name + "\"");
				}
			}
		}
	} // end class ClassCommonCmd

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ParseVarResolver -> Parse.ParseVarResolver
	 * 
	 * Used by the "parser" namespace to resolve variable accesses to common
	 * variables. The runtime resolver procedure is consulted whenever a
	 * variable is accessed within the namespace. It can deny access to certain
	 * variables, or perform special lookups itself.
	 * 
	 * This procedure allows access only to "common" class variables that have
	 * been declared within the class or inherited from another class. A "set"
	 * command can be used to initialized common data members within the body of
	 * the class definition itself:
	 * 
	 * itcl::class Foo { common colors set colors(red) #ff0000 set colors(green)
	 * #00ff00 set colors(blue) #0000ff ... }
	 * 
	 * itcl::class Bar { inherit Foo set colors(gray) #a0a0a0 set colors(white)
	 * #ffffff
	 * 
	 * common numbers set numbers(0) zero set numbers(1) one }
	 * 
	 * ------------------------------------------------------------------------
	 */

	static Var ParseVarResolver(Interp interp, // current interpreter
			String name, // name of the variable being accessed
			Namespace contextNs, // namespace context
			int flags) // TCL.GLOBAL_ONLY => global variable
			// TCL.NAMESPACE_ONLY => namespace variable
			throws TclException {
		ItclObjectInfo info = (ItclObjectInfo) interp
				.getAssocData(ItclInt.INTERP_DATA);
		ItclClass cdefn = (ItclClass) Util.PeekStack(info.cdefnStack);

		ItclVarLookup vlookup;

		// See if the requested variable is a recognized "common" member.
		// If it is, make sure that access is allowed.

		vlookup = (ItclVarLookup) cdefn.resolveVars.get(name);

		if (vlookup != null) {
			if ((vlookup.vdefn.member.flags & ItclInt.COMMON) != 0) {
				if (!vlookup.accessible) {
					throw new TclException(
							interp,
							"can't access \""
									+ name
									+ "\": "
									+ Util
											.ProtectionStr(vlookup.vdefn.member.protection)
									+ " variable");
				}
				return vlookup.common;
			}
		}

		// If the variable is not recognized, return null and
		// let lookup continue via the normal name resolution rules.
		// This is important for variables like "errorInfo"
		// that might get set while the parser namespace is active.

		return null;
	}

	public static class ParseVarResolverImpl implements Resolver {
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
			return Parse.ParseVarResolver(interp, name, context, flags);
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * ItclFreeParserCommandData -> Parse.FreeParserCommandData
	 * 
	 * This callback will free() up memory dynamically allocated and passed as
	 * the ClientData argument to Tcl_CreateObjCommand. This callback is
	 * required because one can not simply pass a pointer to the free() or
	 * ckfree() to Tcl_CreateObjCommand.
	 * ------------------------------------------------------------------------
	 */

	static void FreeParserCommandData(Object cdata) // client data to be
	// destroyed
	{
		// ckfree(cdata);
	}

} // end class Parse

