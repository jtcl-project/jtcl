/*
 * Copyright (c) 2005 Advanced Micro Devices, Inc.
 *
 * See the file "license.amd" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: TJC.java,v 1.36 2009/07/10 13:17:43 rszulgo Exp $ *
 */

// Runtime support for TJC compiler implementation.

package tcl.pkg.tjc;

import java.util.HashMap;

import tcl.lang.CallFrame;
import tcl.lang.Command;
import tcl.lang.ExprValue;
import tcl.lang.Expression;
import tcl.lang.Interp;
import tcl.lang.Namespace;
import tcl.lang.Parser;
import tcl.lang.TCL;
import tcl.lang.TclDouble;
import tcl.lang.TclException;
import tcl.lang.TclInteger;
import tcl.lang.TclList;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;
import tcl.lang.TclRuntimeError;
import tcl.lang.TclString;
import tcl.lang.Util;
import tcl.lang.Var;
import tcl.lang.WrappedCommand;
import tcl.lang.cmd.LappendCmd;
import tcl.lang.cmd.LindexCmd;
import tcl.lang.cmd.ProcCmd;
import tcl.lang.cmd.SwitchCmd;

public class TJC {

	// Constants

	public static final int SWITCH_MODE_EXACT = 0;
	public static final int SWITCH_MODE_GLOB = 1;
	public static final int SWITCH_MODE_REGEXP = 2;

	public static final int EXPR_OP_MULT = Expression.MULT;
	public static final int EXPR_OP_DIVIDE = Expression.DIVIDE;
	public static final int EXPR_OP_MOD = Expression.MOD;
	public static final int EXPR_OP_PLUS = Expression.PLUS;
	public static final int EXPR_OP_MINUS = Expression.MINUS;
	public static final int EXPR_OP_LEFT_SHIFT = Expression.LEFT_SHIFT;
	public static final int EXPR_OP_RIGHT_SHIFT = Expression.RIGHT_SHIFT;
	public static final int EXPR_OP_LESS = Expression.LESS;
	public static final int EXPR_OP_GREATER = Expression.GREATER;
	public static final int EXPR_OP_LEQ = Expression.LEQ;
	public static final int EXPR_OP_GEQ = Expression.GEQ;
	public static final int EXPR_OP_EQUAL = Expression.EQUAL;
	public static final int EXPR_OP_NEQ = Expression.NEQ;
	public static final int EXPR_OP_BIT_AND = Expression.BIT_AND;
	public static final int EXPR_OP_BIT_XOR = Expression.BIT_XOR;
	public static final int EXPR_OP_BIT_OR = Expression.BIT_OR;
	public static final int EXPR_OP_STREQ = Expression.STREQ;
	public static final int EXPR_OP_STRNEQ = Expression.STRNEQ;

	public static final int EXPR_OP_UNARY_MINUS = Expression.UNARY_MINUS;
	public static final int EXPR_OP_UNARY_PLUS = Expression.UNARY_PLUS;
	public static final int EXPR_OP_UNARY_NOT = Expression.NOT;
	public static final int EXPR_OP_UNARY_BIT_NOT = Expression.BIT_NOT;

	public static final WrappedCommand INVALID_COMMAND_CACHE;

	static {
		// Setup a fake command wrapper. The only
		// point of this is to have a wrapper with
		// and invalid cmdEpoch that will never be
		// equal to a valid cmdEpoch for a command.

		INVALID_COMMAND_CACHE = new WrappedCommand();
		INVALID_COMMAND_CACHE.deleted = true;
		INVALID_COMMAND_CACHE.cmdEpoch = -1;
	}

	// Invoked to create and push a new CallFrame for local
	// variables when a command begins to execute. This
	// method does not assign method arguments to local
	// variables inside the CallFrame. The logic here is
	// copied from CallFrame.chain().

	public static CallFrame pushLocalCallFrame(Interp interp, Namespace ns) {
		CallFrame frame = interp.newCallFrame();

		// Namespace the command is defined in, default to global namespace

		if (ns != null) {
			frame.ns = ns;
		}

		// ignore objv

		// isProcCallFrame should be true
		if (frame.isProcCallFrame == false) {
			throw new TclRuntimeError("expected isProcCallFrame to be true");
		}

		frame.level = (interp.varFrame == null) ? 1
				: (interp.varFrame.level + 1);
		frame.caller = interp.frame;
		frame.callerVar = interp.varFrame;
		interp.frame = frame;
		interp.varFrame = frame;

		return frame;
	}

	// Invoked to pop a local CallFrame when a command is finished.

	public static void popLocalCallFrame(Interp interp, CallFrame frame) {
		// Cleanup code copied from Procedure.java. See the cmdProc
		// implementation in that class for more info.

		if (interp.errInProgress) {
			frame.dispose();
			interp.errInProgress = true;
		} else {
			frame.dispose();
		}
	}

	// Invoked to add a compiledLocals array to a
	// CallFrame that was just pushed. This
	// compiledLocals array is disposed of
	// automatically when the CallFrame is popped.

	public static Var[] initCompiledLocals(final CallFrame frame,
			final int size, final String[] names) {
		frame.compiledLocalsNames = names;
		return frame.compiledLocals = new Var[size];
	}

	// Evaluate a Tcl string that is the body of a Tcl procedure.

	public static void evalProcBody(Interp interp, String body)
			throws TclException {
		interp.eval(body);
	}

	// Check a TclException raised while a compiled Tcl procedure
	// is executing. This method should be invoked from a catch
	// block around the body code for a proc. This method could
	// handle the exception and stop if from propagating in the
	// case of a return, or it could allow it to propagate for
	// a normal error case. The logic in this method is copied
	// from Procedure.cmdProc().

	public static void checkTclException(Interp interp, TclException e,
			String procName) throws TclException {
		// FIXME: Ugh this is nasty. How can TCL.OK be package private if this
		// kind of mess could appear in the result from any eval()? All of this
		// should be in an interp method to handle these cases.

		int code = e.getCompletionCode();
		if (code == TCL.RETURN) {
			int realCode = interp.updateReturnInfo();
			if (realCode != TCL.OK) {
				e.setCompletionCode(realCode);
				throw e;
			}
		} else if (code == TCL.ERROR) {
			interp
					.addErrorInfo("\n    (procedure \"" + procName
							+ "\" line 1)");
			throw e;
		} else if (code == TCL.BREAK) {
			throw new TclException(interp,
					"invoked \"break\" outside of a loop");
		} else if (code == TCL.CONTINUE) {
			throw new TclException(interp,
					"invoked \"continue\" outside of a loop");
		} else {
			throw e;
		}
	}

	// Base class for TJC compiled commands

	public static abstract class CompiledCommand implements Command {
		public WrappedCommand wcmd = null;

		// A CompiledCommand implementation must define cmdProc

		// This flag is used to indicate that the command was
		// compiled with inlined Tcl commands.

		protected boolean inlineCmds = false;

		// A CompiledCommand implementation may want to init
		// instance data members when first invoked. This
		// flag and method are used to implement this init
		// check.

		protected boolean initCmd = false;

		protected void initCmd(Interp interp) throws TclException {
			if (initCmd) {
				throw new TclRuntimeError("initCmd already invoked");
			}
			builtinCommandsCheck(interp);
			initConstants(interp);
			initCmd = true;
		}

		// The following method should be defined in a specific
		// command implementations to init constant instance data.

		protected void initConstants(Interp interp) throws TclException {
		}

		// Verify that this compiled command is not being used
		// in a namespace that contains commands with the same
		// name Tcl commands that could have been inlined.
		// Since this check is only done when the command is
		// first invoked, it should not be a performance concern.

		protected void builtinCommandsCheck(Interp interp) throws TclException {
			if (wcmd.ns.fullName.equals("::")) {
				return; // loaded into global namespace
			}
			String[] containers = { "break", "catch", "continue", "expr",
					"for", "foreach", "if", "return", "switch", "while" };
			String[] containers_and_inlines = { "break", "catch", "continue",
					"expr", "for", "foreach", "global", "if", "list",
					"llength", "return", "set", "switch", "while" };
			String[] builtin = (inlineCmds ? containers_and_inlines
					: containers);
			WrappedCommand cmd;
			String cmdName;
			for (int i = 0; i < builtin.length; i++) {
				cmdName = builtin[i];
				cmd = Namespace.findCommand(interp, cmdName, wcmd.ns,
						TCL.NAMESPACE_ONLY);
				if (cmd != null) {
					throw new TclException(interp, "TJC compiled command"
							+ " can't be loaded into the namespace "
							+ wcmd.ns.fullName
							+ " as it defines the builtin Tcl command \""
							+ cmdName + "\" (" + cmd.toString() + ")");
				}
			}
		}

		// The following methods are used in compiled commands
		// that make use of cached variable access.

		// initVarScoped() is invoked for a scoped variable
		// like "::myglobal". This method will create a local
		// var linked to a variable defined in another scope
		// if the compiled local has not been initialized yet.

		protected final void initVarScoped(final Interp interp,
				final String varname, // Fully qualified varname
				// including namespace scope.
				// Can be array or scalar.
				final Var[] compiledLocals, final int localIndex)
				throws TclException {
			if (compiledLocals[localIndex] == null) {
				// Passing EXPLICIT_LOCAL_NAME tells makeUpvar
				// to do nothing when varname lookup fails.
				Var.makeUpvar(interp, null, varname, null, TCL.GLOBAL_ONLY,
						varname, Var.EXPLICIT_LOCAL_NAME, localIndex);
			}
		}

		// getVarScalar() will get a variable value, if a
		// cached variable is available then it will be used.
		// Otherwise the runtime getVar() will be invoked to get
		// the value. This method will raise a TclException
		// on error, it will never return null.

		protected final TclObject getVarScalar(final Interp interp,
				final String varname, // Scalar variable name
				final Var[] compiledLocals, final int localIndex)
				throws TclException {
			Var var = compiledLocals[localIndex];

			if ((var == null) || ((var = Var.resolveScalar(var)) == null)) {
				return Var.getVarCompiledLocalScalarInvalid(interp, varname);
			} else {
				return var.getValue();
			}
		}

		// getVarArray() will get an array element value,
		// if a cached variable is available, then it will be used.
		// Otherwise the runtime getVar() will be invoked to get
		// the value. This method will raise a TclException
		// on error, it will never return null.

		protected final TclObject getVarArray(final Interp interp,
				final String varname, // Array variable name
				final String key, // Array key
				final Var[] compiledLocals, final int localIndex)
				throws TclException {
			Var var = compiledLocals[localIndex];

			if (var == null || ((var = Var.resolveArray(var)) == null)) {
				return Var
						.getVarCompiledLocalArrayInvalid(interp, varname, key);
			} else {
				return Var.getVarCompiledLocalArray(interp, varname, key, var,
						true);
			}
		}

		// setVarScalar() will set a scalar variable value,
		// if a cached variable is available then it will be used,
		// otherwise the runtime setVar() will be invoked to set
		// the value. This method will raise a TclException
		// on error, it will never return null.

		protected final TclObject setVarScalar(final Interp interp,
				final String varname, // Scalar variable name
				final TclObject value, // New variable value
				final Var[] compiledLocals, final int localIndex)
				throws TclException {
			Var var = compiledLocals[localIndex];

			if (var == null) {
				return Var.initVarCompiledLocalScalar(interp, varname, value,
						compiledLocals, localIndex);
			} else if ((var = Var.resolveScalar(var)) == null) {
				return Var.setVarCompiledLocalScalarInvalid(interp, varname,
						value);
			} else {
				TJC.setVarScalar(var, value);
				return value;
			}
		}

		protected final TclObject setVarScalar(final Interp interp,
				final String varname, final String value,
				final Var[] compiledLocals, final int localIndex)
				throws TclException {
			TclObject tobj = interp.checkCommonString(value);
			return setVarScalar(interp, varname, tobj, compiledLocals,
					localIndex);
		}

		// setVarArray() will set an array element to a value,
		// if a cached variable is available then it will be used,
		// otherwise the runtime setVar() will be invoked to set
		// the value. This method will raise a TclException
		// on error, it will never return null.

		protected final TclObject setVarArray(final Interp interp,
				final String varname, final String key, final TclObject value,
				final Var[] compiledLocals, final int localIndex)
				throws TclException {
			Var var = compiledLocals[localIndex];

			if (var == null) {
				return Var.initVarCompiledLocalArray(interp, varname, key,
						value, compiledLocals, localIndex);
			} else if ((var = Var.resolveArray(var)) == null) {
				return Var.setVarCompiledLocalArrayInvalid(interp, varname,
						key, value);
			} else {
				return Var.setVarCompiledLocalArray(interp, varname, key,
						value, var);
			}
		}

		protected final TclObject setVarArray(final Interp interp,
				final String varname, final String key, final String value,
				final Var[] compiledLocals, final int localIndex)
				throws TclException {
			TclObject tobj = interp.checkCommonString(value);
			return setVarArray(interp, varname, key, tobj, compiledLocals,
					localIndex);
		}

		// incrVarScalar() will increment a scalar compiled local
		// by the given incrAmount. If the variable is not a
		// scalar local or has traces set then the runtime version
		// of the incr command will be used.

		protected final TclObject incrVarScalar(final Interp interp,
				final String varname, final long incrAmount,
				final Var[] compiledLocals, final int localIndex)
				throws TclException {
			Var var = compiledLocals[localIndex];

			if (var == null || ((var = Var.resolveScalar(var)) == null)) {
				return TJC.incrVar(interp, varname, null, incrAmount);
			} else {
				TclObject varValue = var.getValue();

				boolean createdNewObj = false;
				if (varValue.isShared()) {
					varValue = varValue.duplicate();
					createdNewObj = true;
				}
				try {
					TclInteger.incr(interp, varValue, incrAmount);
				} catch (TclException ex) {
					if (createdNewObj) {
						varValue.release(); // free unneeded copy
					}
					throw ex;
				}

				// If we create a new TclObject, then save it
				// as the variable value.

				if (createdNewObj) {
					TJC.setVarScalar(var, varValue);
				}
				return varValue;
			}
		}

		// incrVarArray() will increment an array element.
		// If the array is not a compiled local scalar then
		// the runtime implementation will be used.

		protected final TclObject incrVarArray(final Interp interp,
				final String varname, final String key, final long incrAmount,
				final Var[] compiledLocals, final int localIndex)
				throws TclException {
			Var var = compiledLocals[localIndex];

			if (var == null || ((var = Var.resolveArray(var)) == null)) {
				return TJC.incrVar(interp, varname, key, incrAmount);
			} else {
				TclObject varValue = Var.getVarCompiledLocalArray(interp,
						varname, key, var, true);

				boolean createdNewObj = false;
				if (varValue.isShared()) {
					varValue = varValue.duplicate();
					createdNewObj = true;
				}
				try {
					TclInteger.incr(interp, varValue, incrAmount);
				} catch (TclException ex) {
					if (createdNewObj) {
						varValue.release(); // free unneeded copy
					}
					throw ex;
				}

				// Set the array element once again since the
				// variable could have traces.

				return Var.setVarCompiledLocalArray(interp, varname, key,
						varValue, var);
			}
		}

		// lappendVarScalar() will append list elements to
		// a scalar local variable. If the variable is not a
		// scalar local or has traces set then the runtime version
		// of the lappend command will be used.

		protected final TclObject lappendVarScalar(final Interp interp,
				final String varname, final TclObject[] values,
				final Var[] compiledLocals, final int localIndex)
				throws TclException {
			Var var = compiledLocals[localIndex];

			// Use runtime impl of lappend if resolved var
			// is not available. The lappend command
			// accepts an undefined variable name, but we
			// don't optimize that case.

			if (var == null || ((var = Var.resolveScalar(var)) == null)) {
				return TJC.lappendVar(interp, varname, null, values);
			}

			// The cache var is valid, but it might indicate
			// a shared value. If the value is shared then
			// we need to duplicate it and invoke setVar()
			// to implement "copy on write".

			TclObject varValue = var.getValue();
			boolean createdNewObj = false;

			if (varValue.isShared()) {
				varValue = varValue.duplicate();
				createdNewObj = true;
			}

			// Insert the new elements at the end of the list.

			final int len = values.length;
			if (len == 1) {
				TclList.append(interp, varValue, values[0]);
			} else {
				TclList.append(interp, varValue, values, 0, len);
			}

			if (createdNewObj) {
				TJC.setVarScalar(var, varValue);
			}
			return varValue;
		}

		// lappendVarArray() will append list elements to
		// an array element in a compiled local array variable.
		// If the variable is not an array variable then the runtime
		// implementation of the lappend command will be used.

		protected final TclObject lappendVarArray(final Interp interp,
				final String varname, final String key,
				final TclObject[] values, final Var[] compiledLocals,
				final int localIndex) throws TclException {
			Var var = compiledLocals[localIndex];

			// Use runtime impl of lappend if resolved array
			// var is null or is not valid. The lappend command
			// accepts an undefined variable name, but we
			// don't optimize that case.

			if (var == null || ((var = Var.resolveArray(var)) == null)) {
				return TJC.lappendVar(interp, varname, key, values);
			}

			// The cache var is valid but need to lookup the
			// array element to see if it exists. If the
			// array element does not exist then it is
			// assumed to be an empty list. If the element
			// does exist, then check to see if it is
			// shared and if so then make a copy to
			// implement "copy on write".

			TclObject varValue = Var.getVarCompiledLocalArray(interp, varname,
					key, var, false);

			if (varValue == null) {
				// Array element does not exist, use {}
				varValue = TclList.newInstance();
			} else if (varValue.isShared()) {
				varValue = varValue.duplicate();
			}

			// Insert the new elements at the end of the list.

			final int len = values.length;
			if (len == 1) {
				TclList.append(interp, varValue, values[0]);
			} else {
				TclList.append(interp, varValue, values, 0, len);
			}

			return Var.setVarCompiledLocalArray(interp, varname, key, varValue,
					var);
		}

		// appendVarScalar() will append string elements to
		// a scalar local variable. If the variable is not a
		// scalar local or has traces set then the runtime version
		// of the append command will be used.

		protected final TclObject appendVarScalar(final Interp interp,
				final String varname, final TclObject[] values,
				final Var[] compiledLocals, final int localIndex)
				throws TclException {
			Var var = compiledLocals[localIndex];

			// Use runtime impl of append if resolved var
			// is not available. The append command
			// accepts an undefined variable name, but we
			// don't optimize that case.

			if (var == null || (var = Var.resolveScalar(var)) == null) {
				return TJC.appendVar(interp, varname, null, values);
			}

			// The cache var is valid, but it might indicate
			// a shared value. If the value is shared then
			// we need to create a new TclString object
			// and drop refs to the previous TclObject value.

			TclObject varValue = var.getValue();
			boolean createdNewObj = false;

			if (varValue.isShared()) {
				varValue = TclString.newInstance(varValue.toString());
				createdNewObj = true;
			}

			// Insert the new elements at the end of the string.

			final int len = values.length;
			if (len == 1) {
				TclString.append(varValue, values[0].toString());
			} else {
				TclString.append(varValue, values, 0, len);
			}

			if (createdNewObj) {
				TJC.setVarScalar(var, varValue);
			}
			return varValue;
		}

		// appendVarArray() will append string elements to an
		// element inside an array variable.

		protected final TclObject appendVarArray(final Interp interp,
				final String varname, final String key,
				final TclObject[] values, final Var[] compiledLocals,
				final int localIndex) throws TclException {
			// This is way lame, but the append command
			// semantics for arrays with traces are
			// such that we can't use an optimized
			// implementation. Use the runtime append
			// command implementation for now until
			// the Tcl core can be fixed to correct this.
			// The implementation should work the same
			// way as the lappend command.

			return TJC.appendVar(interp, varname, key, values);
		}

	} // end class CompiledCommand

	// Used to create a TJC compiled command. This method will
	// use the fully qualified command name passed in to
	// create the command in the correct namespace.

	public static void createCommand(Interp interp, // Interp to create command
			// in
			String cmdName, // Fully qualified or short name of command
			TJC.CompiledCommand cmd) // Instance for new compiled command
			throws TclException {
		ProcCmd.FindCommandNamespaceResult result = ProcCmd
				.FindCommandNamespace(interp, cmdName);

		interp.createCommand(result.cmdFullName, cmd);

		cmd.wcmd = Namespace.findCommand(interp, result.cmdName, result.ns,
				TCL.NAMESPACE_ONLY);
	}

	// Used to load an init file from the package JAR file.
	// This command should be invoked from a TJCExtension
	// to source the init Tcl script in the JAR. It is
	// possible that the TJC package is not yet loaded
	// if java::load was used to load the Extension. Just
	// require TJC package to take care of this case.

	public static void sourceInitFile(Interp interp, String init_file,
			String[] files, String prefix) throws TclException {
		// Install temp source command
		interp.eval("package require TJC");
		interp.eval("rename ::source ::TJC::source");
		interp.createCommand("::source", new InitSourceCmd(init_file, files,
				prefix));

		// System.out.println("sourceInitFile: will source \"" + (prefix +
		// init_file) + "\"");
		TclException tex = null;
		try {
			interp.evalResource(prefix + init_file);
		} catch (TclException ex) {
			tex = ex;
		} finally {
			// Remove temp source command
			interp.eval("rename ::source {}");
			interp.eval("rename ::TJC::source ::source");

			if (tex != null) {
				interp.setResult(tex.getMessage());
				throw tex;
			}
		}
	}

	// This class implements a fake "source" command that is
	// only active while a package init file is being sourced
	// inside sourceInitFile().

	static class InitSourceCmd implements Command {
		String init_file;
		String[] files;
		String prefix;
		HashMap filesTable;

		InitSourceCmd(String init_file, String[] files, String prefix) {
			this.init_file = init_file;
			this.files = files;
			this.prefix = prefix;

			filesTable = new HashMap();
			for (int i = 0; i < files.length; i++) {
				filesTable.put(files[i], "");
			}
		}

		public void cmdProc(Interp interp, TclObject[] objv)
				throws TclException {
			boolean handled = false;

			// Expected syntax "source filename"

			if (objv.length == 2) {
				String filepath = objv[1].toString();

				interp.eval("file tail {" + filepath + "}");

				String filename = interp.getResult().toString();

				if (filesTable.containsKey(filename)) {
					// Sourced a file in files array, this
					// file should be read from a resource.
					handled = true;

					// System.out.println("sourceInitFile.files: will source \""
					// + (prefix + filename) + "\"");
					interp.evalResource(prefix + filename);
				}
			}

			if (!handled) {
				// Invoke TJC::source, this is the original source command.
				// The original command will deal with error reporting
				// as well as invocations that source a file not in "files".
				WrappedCommand cmd = interp.getWrappedCommand("TJC::source");
				if (cmd.mustCallInvoke(interp)) cmd.invoke(interp, objv);
				else cmd.cmd.cmdProc(interp, objv);
			}
		}
	}

	// Resolve a command name into a WrappedCommand reference
	// that is cached. Note that this method is only ever
	// invoked after a command has been successfully invoked,
	// so there should be no need to worry about loading the
	// command or dealing with stub commands. If the command
	// can't be located, null is returned. This command is
	// always invoked after the CallFrame for a compiled
	// command has been pushed, so it is safe to assume
	// that the current namespace is the namespace the
	// command is defined in.

	public static WrappedCommand resolveCmd(Interp interp, String cmdName)
			throws TclException {
		return Namespace.findCommand(interp, cmdName, null, 0);
	}

	// This method is used to set the TclObject value
	// contained inside a cached scalar Var reference.
	// This method works like interp.setVar(), it should
	// be used when a valid cached var reference is
	// held.

	static final void setVarScalar(final Var var, final TclObject newValue) // New
	// value
	// to
	// set
	// varible
	// to,
	// can't
	// be
	// null
	{
		TclObject oldValue = var.getValue();
		if (oldValue != newValue) {
			var.setValue(newValue);
			newValue.preserve();
			if (oldValue != null) {
				oldValue.release();
			}
		}
		// Unlike Var.setVar(), this method does not invoke
		// setVarScalar() or clearVarUndefined() since a
		// cached array variable will never be passed to
		// this method and an undefined variable will never
		// be cached. This method does not return a value
		// since the return value would always be the passed
		// in newValue.
		return;
	}

	/**
	 * This method is invoked when a compiled incr command
	 * is inlined. This methods works for both scalar
	 * variables and array scalars. This method is not
	 * used for cached variables.
	 * 
	 * @param interp current interpreter
	 * @param part1 array name, or scalar name
	 * @param part2 array index
	 * @param incrAmount amount to increment
	 * @return tclobj
	 * @throws TclException
	 */
	public static final TclObject incrVar(Interp interp, String part1,
			String part2, long incrAmount) throws TclException {
		return Var.incrVar(interp, part1, part2, incrAmount, TCL.LEAVE_ERR_MSG);
	}

	// Get a TclObject[] of the given size. This array will
	// be allocated quickly from a cache if it is a common size.
	// The array must be released by releaseObjv(). Each element
	// in the returned array will be null.

	private static final boolean USE_OBJV_CACHE = true;

	public static TclObject[] grabObjv(final Interp interp, final int size) {
		if (USE_OBJV_CACHE) {
			return Parser.grabObjv(interp, size);
		} else {
			return new TclObject[size];
		}
	}

	// Release the array back into the common array
	// cache. This array must have been allocated
	// with grabObjv(). This method will not release
	// TclObject values in the array, use the
	// releaseObjvElems() method for that.

	public static void releaseObjv(final Interp interp, final TclObject[] objv,
			final int size) {
		if (USE_OBJV_CACHE) {
			Parser.releaseObjv(interp, objv, size);
		}
	}

	// For each non-null TclObject element in the array,
	// invoke TclObject.release() and then return the
	// array to the common cache of array values.
	// The array must have been allocated with grabObjv().

	public static void releaseObjvElems(final Interp interp,
			final TclObject[] objv, final int size) {
		for (int i = 0; i < size; i++) {
			TclObject tobj = objv[i];
			if (tobj != null) {
				tobj.release();
			}
		}
		if (USE_OBJV_CACHE) {
			Parser.releaseObjv(interp, objv, size);
		}
	}

	// Invoke a Command with TclObject arguments. This method
	// will invoke an already resolved Command passed in "cmd" or
	// it will resolve objv[0] into a command. This method
	// assumes that the ref count of each element in the
	// objv was already incremented by the caller. This method
	// does not modify or cleanup the passed in objv array.

	public static void invoke(Interp interp, // Interp to invoke command in
			Command cmd, // Resolved command ref, null to lookup objv[0]
			TclObject[] objv, // TclObject arguments to command
			int flags) // Either 0 or TCL.EVAL_GLOBAL. If 0 is passed
			// then objv[0] is resolved in the current
			// namespace and then the global one. If
			// TCL.EVAL_GLOBAL is passed then the command
			// is resolved and evaluated in the global namespace.
			// If the cmd argument is non-null, then this
			// flag only controls evaluation scope.
			throws TclException {
		boolean grabbed_objv = false;

		// Save copy of interp.varFrame in case TCL.EVAL_GLOBAL is set.
		CallFrame savedVarFrame = interp.varFrame;

		// If cmd is null, then resolve objv[0] into a Command.

		if (cmd == null) {
			WrappedCommand wcmd;
			int fflags = 0;
			if ((flags & TCL.EVAL_GLOBAL) != 0) {
				fflags |= TCL.GLOBAL_ONLY;
			}
			// Find the procedure to execute this command. If there isn't one,
			// then see if there is a command "unknown". If so, create a new
			// word array with "unknown" as the first word and the original
			// command words as arguments.
			String cmdName = objv[0].toString();
			wcmd = Namespace.findCommand(interp, cmdName, null, fflags);
			if (wcmd != null) {
				cmd = wcmd.cmd;
			}
			if (cmd == null) {
				wcmd = Namespace.findCommand(interp, "unknown", null,
						TCL.GLOBAL_ONLY);
				if (wcmd != null) {
					cmd = wcmd.cmd;
				}
				if (cmd == null) {
					throw new TclException(interp, "invalid command name \""
							+ cmdName + "\"");
				}
				int len = objv.length;
				if (len == 0) {
					throw new TclRuntimeError("zero length objv array");
				}
				TclObject[] newObjv = TJC.grabObjv(interp, len + 1);
				newObjv[0] = TclString.newInstance("unknown");
				newObjv[0].preserve();
				for (int i = (len - 1); i >= 0; i--) {
					newObjv[i + 1] = objv[i];
				}
				objv = newObjv;
				grabbed_objv = true;
			}
		}

		try {
			interp.preserve();
			interp.allowExceptions();

			interp.ready();

			interp.nestLevel++;
			interp.cmdCount++;

			// Invoke cmdProc for the command.

			if ((flags & TCL.EVAL_GLOBAL) != 0) {
				interp.varFrame = null;
			}

			cmd.cmdProc(interp, objv);
		} catch (TclException ex) {
			// Generate error info that includes the arguments
			// to the command and add these to the errorInfo var.

			if (ex.getCompletionCode() == TCL.ERROR
					&& !(interp.errAlreadyLogged)) {
				StringBuffer cmd_strbuf = new StringBuffer(64);

				int len = objv.length;
				if (len == 0) {
					throw new TclRuntimeError("zero length objv array");
				}
				for (int i = 0; i < len; i++) {
					Util.appendElement(interp, cmd_strbuf, objv[i].toString());
				}
				String cmd_str = cmd_strbuf.toString();
				char[] script_array = cmd_str.toCharArray();
				int script_index = 0;
				int command_start = 0;
				int command_length = cmd_str.length();
				Parser.logCommandInfo(interp, script_array, script_index,
						command_start, command_length, ex);
			}

			throw ex;
		} finally {
			if (grabbed_objv) {
				objv[0].release();
				TJC.releaseObjv(interp, objv, objv.length);
			}
			interp.nestLevel--;
			interp.varFrame = savedVarFrame;
			interp.release();

			interp.checkInterrupted();
		}
	}

	// Most efficient way to query a TclObject
	// to determine its boolean value. If a
	// TclString is passed into this method,
	// it will be parsed and the object's internal
	// rep will be updated to a numeric type.

	public static boolean getBoolean(final Interp interp, final TclObject obj)
			throws TclException {
		if (obj.isIntType()) {
			return (obj.ivalue != 0); // Inline TclInteger.get()
		} else if (obj.isDoubleType()) {
			return (TJC.exprGetKnownDouble(obj) != 0.0);
		}

		ExprValue value;
		if (USE_EXPR_CACHE) {
			value = interp.expr.grabExprValue();
		} else {
			value = new ExprValue(0, null);
		}
		// The logic above already checked for an int or
		// double type so just reparse from the string
		// instead of calling ExprParseObject().
		Expression.ExprParseString(interp, obj, value);
		boolean b = value.getBooleanValue(interp);
		if (USE_EXPR_CACHE) {
			interp.expr.releaseExprValue(value);
		}
		return b;
	}

	// This method will invoke logic for the switch
	// command at runtime. If no body is matched,
	// then -1 will be returned. Otherwise, the
	// offset from the first pattern is returned.
	// The caller must take care to release the
	// pbObjv array after invoking this method.

	public static int invokeSwitch(Interp interp, TclObject[] pbObjv,
			int pbStart, // Index > 0 of first pattern
			String string, // String to be matched against patterns
			int mode) // Either TJC.SWITCH_MODE_EXACT
			// or TJC.SWITCH_MODE_GLOB
			// or TJC.SWITCH_MODE_REGEXP
			throws TclException {
		int offset = SwitchCmd.getBodyOffset(interp, pbObjv, pbStart, string,
				mode);
		return offset;
	}

	// Check a switch string and raise an error
	// if it starts with a '-' character. This
	// runtime check is needed for a compiled
	// switch command like [switch $str {...}]
	// which has no option terminator "--".

	public static void switchStringIsNotOption(Interp interp, String str)
			throws TclException {
		if (str.startsWith("-")) {
			TclObject[] objv = new TclObject[1];
			objv[0] = TclString.newInstance("switch");
			throw new TclNumArgsException(interp, 1, objv,
					"?switches? string pattern body ... ?default body?");
		}
	}

	// Raise exception when a variable could not
	// be set during a catch command. This
	// exception is defined here so that the
	// constant string need not appear in
	// every class file.

	public static void catchVarErr(Interp interp) throws TclException {
		throw new TclException(interp,
				"couldn't save command result in variable");
	}

	// Raise exception when a loop variable could
	// not be set in a foreach command.

	public static void foreachVarErr(Interp interp, String varname)
			throws TclException {
		throw new TclException(interp, "couldn't set loop variable: \""
				+ varname + "\"");
	}

	private static final boolean USE_EXPR_CACHE = true;

	// Release an ExprValue that was returned by
	// one of the exprGetValue methods.

	public static void exprReleaseValue(Interp interp, ExprValue value) {
		if (USE_EXPR_CACHE) {
			interp.expr.releaseExprValue(value);
		}
	}

	// Return the ExprValue for the given int value.
	// If the value was parsed from a String that
	// differs from the parsed value of the integer,
	// then it should be passed as the srep.

	public static ExprValue exprGetValue(Interp interp, long ival, String srep)
			throws TclException {
		if (USE_EXPR_CACHE) {
			ExprValue value = interp.expr.grabExprValue();
			value.setIntValue(ival, srep);
			return value;
		} else {
			return new ExprValue(ival, srep);
		}
	}

	// Return the expr value contained in the double

	public static ExprValue exprGetValue(Interp interp, double dval, String srep)
			throws TclException {
		if (USE_EXPR_CACHE) {
			ExprValue value = interp.expr.grabExprValue();
			value.setDoubleValue(dval, srep);
			return value;
		} else {
			return new ExprValue(dval, srep);
		}
	}

	// Return the expr value for the String

	public static ExprValue exprGetValue(Interp interp, String srep)
			throws TclException {
		if (USE_EXPR_CACHE) {
			ExprValue value = interp.expr.grabExprValue();
			value.setStringValue(srep);
			return value;
		} else {
			return new ExprValue(srep);
		}
	}

	// Return the expr value for the boolean, this
	// boolean has no string rep and is represented
	// by an integer type.

	public static ExprValue exprGetValue(Interp interp, boolean bval)
			throws TclException {
		if (USE_EXPR_CACHE) {
			ExprValue value = interp.expr.grabExprValue();
			value.setIntValue(bval);
			return value;
		} else {
			return new ExprValue(bval);
		}
	}

	// Return the expr value contained in the TclObject

	public static ExprValue exprGetValue(Interp interp, TclObject tobj)
			throws TclException {
		if (USE_EXPR_CACHE) {
			ExprValue value = interp.expr.grabExprValue();
			Expression.ExprParseObject(interp, tobj, value);
			return value;
		} else {
			ExprValue value = new ExprValue(0, null);
			Expression.ExprParseObject(interp, tobj, value);
			return value;
		}
	}

	// Init expr value with the value in the TclObject

	public static void exprInitValue(final Interp interp,
			final ExprValue value, final TclObject tobj) throws TclException {
		Expression.ExprParseObject(interp, tobj, value);
	}

	// Return an uninitialized ExprValue.

	public static ExprValue exprGetValue(Interp interp) {
		if (USE_EXPR_CACHE) {
			return interp.expr.grabExprValue();
		} else {
			return new ExprValue(0, null);
		}
	}

	// Return the int value inside a TclObject known
	// to be of type integer. This method should
	// only be used inside the expr layer. Testing
	// indicates that this method executes 25%
	// faster than TclInteger.get().

	public static long exprGetKnownInt(final TclObject tobj) {
		return tobj.ivalue;
	}

	// Return the double value inside a TclObject known
	// to be of type double. This method should
	// only be used inside the expr layer. Testing
	// indicates that this method executes about
	// 4x faster than TclDouble.get().

	public static double exprGetKnownDouble(final TclObject tobj) {
		return ((TclDouble) tobj.getInternalRep()).value;
	}

	// Evaluate a unary expr operator.

	public static void exprUnaryOperator(final Interp interp, // current interp,
			// can't be
			// null.
			final int op, // One of the EXPR_OP_* values
			final ExprValue value) throws TclException {
		Expression.evalUnaryOperator(interp, op, value);
	}

	// Evaluate a binary expr operator.

	public static void exprBinaryOperator(final Interp interp, // current
			// interp, can't
			// be null.
			final int op, // One of the EXPR_OP_* values
			final ExprValue value, final ExprValue value2) throws TclException {
		Expression.evalBinaryOperator(interp, op, value, value2);
	}

	// Evaluate a math function. This method will release
	// the values ExprValue objects when finished. The
	// values argument should be null where the math
	// function takes no arguments.

	public static void exprMathFunction(Interp interp, // current interp, can't
			// be null.
			String funcName, // Name of math function
			ExprValue[] values, // Array of arguments, can be null
			ExprValue result) // Location to store result
			throws TclException {
		interp.expr.evalMathFunction(interp, funcName, values, false, result);
	}

	// Set the interp result to the given expr value. This
	// method is used only for a compiled version of the expr
	// command.

	public static void exprSetResult(Interp interp, ExprValue value)
			throws TclException {
		switch (value.getType()) {
		case ExprValue.INT:
			interp.setResult(value.getIntValue());
			break;
		case ExprValue.DOUBLE:
			interp.setResult(value.getDoubleValue());
			break;
		case ExprValue.STRING:
			interp.setResult(value.getStringValue());
			break;
		default:
			throw new TclRuntimeError("internal error: expression, unknown");
		}
		return;
	}

	// Determine if the given TclObject is equal to
	// the empty string. This method implements an
	// optimized version of an expr comparison
	// like expr {$obj == ""} or expr {$obj != {}}.
	// This method sets the value argument to
	// an integer, either 0 or 1.

	public static void exprEqualsEmptyString(ExprValue value, // Location for
			// result value
			TclObject obj, // TclObject to compare to empty string
			final boolean negate) // Negate result if true
			throws TclException {
		boolean isEmptyString;

		if (obj.hasNoStringRep() && obj.isListType()) {
			// A pure Tcl list is equal to the empty string
			// when the list length is zero. This check
			// avoids the possibly slow generation of
			// a string rep from a pure TclList object.
			// Note that passing null as the Interp
			// argument is fine since we know this object
			// has the TclList internal rep already.
			isEmptyString = (TclList.getLength(null, obj) == 0);
		} else {
			// TclObject already has a string rep, check if it is a
			// ref to the interned empty string or if the len is 0.
			String s = obj.toString();
			isEmptyString = ("".equals(s) || s.length() == 0);
		}
		if (negate) {
			isEmptyString = !isEmptyString;
		}
		value.setIntValue(isEmptyString);
	}

	// Implement an optimized version of the
	// int() math function used in an expr.
	// This method will raise an error if
	// the value is non-numeric, otherwise
	// it will case a double type to an int.

	public static void exprIntMathFunction(Interp interp, ExprValue value)
			throws TclException {
		switch (value.getType()) {
		case ExprValue.INT: {
			// No-op
			break;
		}
		case ExprValue.DOUBLE: {
			double d = value.getDoubleValue();
			if (((d < 0) && (d < ((double) TCL.INT_MIN)))
					|| ((d > 0) && (d > ((double) TCL.INT_MAX)))) {
				Expression.IntegerTooLarge(interp);
			}
			value.setIntValue((long) d);
			break;
		}
		case ExprValue.STRING: {
			throw new TclException(interp,
					"argument to math function didn't have numeric value");
		}
		}
	}

	// Implement an optimized version of the
	// double() math function used in an expr.
	// This method will raise an error if
	// the value is non-numeric, otherwise
	// it will case an int type to a double.

	public static void exprDoubleMathFunction(Interp interp, ExprValue value)
			throws TclException {
		if (value.isStringType()) {
			throw new TclException(interp,
					"argument to math function didn't have numeric value");
		} else if (value.isIntType()) {
			value.setDoubleValue((double) value.getIntValue());
		}
	}

	// Evaluate a unary not operator. This method accepts an
	// ExprValue value and returns the result in the ExprValue.
	// This method is invoked when an ExprValue is known
	// at compile time to be a double or a string.
	// This method should not be invoked when the ExprValue
	// is known to be of type int (or boolean).

	public static void exprUnaryNotOperator(final Interp interp, // current
			// interp,
			// can't be
			// null.
			final ExprValue value) // operand value
			throws TclException {
		// Special case of int type should have been handled
		// in inline code before this method is invoked.

		if (value.isDoubleType()) {
			value.setIntValue(value.getDoubleValue() == 0.0);
		} else {
			Expression.evalUnaryOperator(interp, TJC.EXPR_OP_UNARY_NOT, value);
		}
	}

	// Evaluate a unary not operator. This method accepts a
	// TclObject value and returns the result in an ExprValue.

	public static void exprUnaryNotOperator(final Interp interp, // current
			// interp,
			// can't be
			// null.
			final ExprValue value, // where to store result of operator
			final TclObject tobj) // contains the value as a TclObject
			throws TclException {
		// Special case of int type should have been handled
		// in inline code before this method is invoked.

		if (tobj.isDoubleType()) {
			value.setIntValue(TJC.exprGetKnownDouble(tobj) == 0.0);
			return;
		}

		Expression.ExprParseString(interp, tobj, value);
		if (value.isIntType()) {
			// ExprValue is known to be an int, so
			// invoke specific method that does not
			// change the type.

			value.optIntUnaryNot();
		} else if (value.isDoubleType()) {
			value.setIntValue(value.getDoubleValue() == 0.0);
		} else {
			Expression.evalUnaryOperator(interp, EXPR_OP_UNARY_NOT, value);
		}
	}

	// These methods are used when inlining a
	// unary not operator. In the case where
	// the operand is known to be of type int,
	// these methods are used to inline the op.
	// This impl executes about 4x faster than
	// a exprUnaryNotOperator() that checks
	// for a TclObject that contains an int.

	public static void exprUnaryNotOperatorKnownInt(final ExprValue value, // where
			// to
			// store
			// result
			// of
			// operator
			final TclObject tobj) // contains the value as a TclObject
	{
		// Invoking ExprValue.setInt(boolean) here is the most
		// efficient implementation. Invoking TJC.exprGetKnownInt()
		// and inlining the branching logic in calling code is slower.

		value.setIntValue(tobj.ivalue == 0);
	}

	public static int exprUnaryNotOperatorKnownInt(final TclObject tobj) // contains
	// the
	// value
	// as
	// a
	// TclObject
	{
		return (tobj.ivalue == 0) ? 1 : 0;
	}

	public static boolean exprUnaryNotOperatorKnownIntAsBoolean(
			final TclObject tobj) // contains the value as a TclObject
	{
		return (tobj.ivalue == 0);
	}

	// Implements inlined global command, this method
	// will create a local var linked to a global var.
	// A compiled global command accepts a varTail
	// that is either a scalar or an array name.
	// If the localIndex argument is not -1, it
	// indicates the compiledLocal slot to use.

	public static final void makeGlobalLinkVar(Interp interp, String varName, // Fully
			// qualified
			// name
			// of
			// global
			// variable.
			String varTail, // Variable name without namespace qualifiers.
			int localIndex) // Index into compiledLocals array
			throws TclException {
		// Link to the variable "varName" in the global :: namespace.
		// A local link var named varTail is defined.

		Var.makeUpvar(interp, null, varName, null, TCL.GLOBAL_ONLY, varTail, 0,
				localIndex);
	}

	// Implements inlined lindex command for non-constant integer
	// index values. This implementation is used only with lindex
	// commands that have 3 arguments. If the index argument
	// is already a TclInteger type then an optimized lindex
	// impl is used. The interp result is always set by this
	// method.

	public static final void lindexNonconst(final Interp interp,
			final TclObject listObj, // List value
			final TclObject indexValue) // List index to be resolved.
			throws TclException {
		// Optimized check for integer indexValue argument.
		// This is the most common use case:
		// set obj [lindex $list $i]

		if (indexValue.isIntType()) {
			TclObject result = TclList
					.index(interp, listObj, (int)indexValue.ivalue);
			if (result == null) {
				interp.resetResult();
			} else {
				interp.setResult(result);
			}
			return;
		} else {
			// Invoke the static lindex command impl.

			TclObject[] objv = TJC.grabObjv(interp, 3);
			try {
				// objv[0] = null;

				objv[1] = listObj;

				objv[2] = indexValue;
				indexValue.preserve();

				TclObject elem = LindexCmd.TclLindexList(interp, listObj, objv,
						2);
				interp.setResult(elem);
				elem.release();
			} finally {
				// Caller should preserve() and release() listObj
				objv[2].release();
				TJC.releaseObjv(interp, objv, 3);
			}
			return;
		}
	}

	// Implements inlined lappend command that appends 1 or more
	// TclObject values to a variable. This implementation
	// makes use of runtime support found in the LappendCmd class.
	// The new variable value after the lappend operation is returned.

	public static final TclObject lappendVar(Interp interp, String varName, // Name
			// of
			// variable
			String key, // Array element key (can be null)
			TclObject[] values) // Array of TclObject values to append
			throws TclException {
		if (key == null) {
			return LappendCmd.lappendVar(interp, varName, values, 0);
		} else {
			// LappendCmd expects a single var name in a String,
			// so create one for this uncommon case.
			String avName = varName + "(" + key + ")";
			return LappendCmd.lappendVar(interp, avName, values, 0);
		}
	}

	// Implements inlined append command that appends 1 or more
	// TclObject values to a variable. This implementation
	// duplicates the logic found in AppendCmd. The new
	// variable value after the lappend operation is returned.

	public static final TclObject appendVar(Interp interp, String varName, // Name
			// of
			// variable
			String key, // Array element key (can be null)
			TclObject[] values) // Array of TclObject values to append
			throws TclException {
		TclObject varValue = null;
		final int len = values.length;
		if (key != null) {
			varName = varName + "(" + key + ")";
		}

		for (int i = 0; i < len; i++) {
			varValue = interp.setVar(varName, values[i], TCL.APPEND_VALUE);
		}

		if (varValue == null) {
			// Return empty result object if null
			varValue = interp.checkCommonString(null);
		}

		return varValue;
	}

	// Implements inlined string index command. This
	// implementation duplicates the logic found
	// in StringCmd.java. The new value is returned.
	// If the index is out of range the null result
	// will be returned.

	public static final TclObject stringIndex(final Interp interp,
			final String str, // string
			final TclObject indObj) // index into string
			throws TclException {
		final int len = str.length();
		int i;

		if (indObj.isIntType()) {
			i = (int)indObj.ivalue; // Inline TclInteger.get()
		} else {
			i = Util.getIntForIndex(interp, indObj, len - 1);
		}

		if ((i >= 0) && (i < len)) {
			TclObject obj = interp.checkCommonCharacter(str.charAt(i));
			if (obj == null) {
				obj = TclString.newInstance(str.substring(i, i + 1));
			}
			return obj;
		} else {
			return interp.checkCommonString(null);
		}
	}

	// Implements inlined string range command. This
	// implementation duplicates the logic found
	// in StringCmd.java. The new value is returned.
	// This method assumes that the firstObj TclObject
	// was preserved() before this method is invoked.
	// This method will always release() the firstObj.

	public static final TclObject stringRange(final Interp interp,
			final String str, // string
			final TclObject firstObj, // first index (ref count incremented)
			final TclObject lastObj) // last index
			throws TclException {
		final int len = str.length();
		int first, last;

		try {
			if (firstObj.isIntType()) {
				first = (int)firstObj.ivalue; // Inline TclInteger.get()
			} else {
				first = Util.getIntForIndex(interp, firstObj, len - 1);
			}
			if (first < 0) {
				first = 0;
			}

			if (lastObj.isIntType()) {
				last = (int)lastObj.ivalue; // Inline TclInteger.get()
			} else {
				last = Util.getIntForIndex(interp, lastObj, len - 1);
			}
			if (last >= len) {
				last = len - 1;
			}
		} finally {
			// Release firstObj after lastObj has been queried.
			// There could only be a ref count problem if firstObj
			// was released before lastObj and lastObj was a list
			// element of firstObj and lastObj had a ref count of 1.

			firstObj.release();
		}

		if (first > last) {
			return interp.checkCommonString(null);
		} else {
			String substr = str.substring(first, last + 1);
			return TclString.newInstance(substr);
		}
	}

	// Implements inlined "string first" command, this
	// duplicates the logic found in StringCmd.java.
	// A TclObject that holds the new value is
	// returned.

	public static final TclObject stringFirst(Interp interp, String substr, // substring
			// to
			// search
			// for
			String str, // string to search in
			TclObject startObj) // start index (null if start is 0)
			throws TclException {
		int substrLen = substr.length();
		int strLen = str.length();
		int index;

		int start;

		if (startObj == null) {
			start = 0;
		} else {
			// If a startIndex is specified, we will need to fast
			// forward to that point in the string before we think
			// about a match.

			start = Util.getIntForIndex(interp, startObj, strLen - 1);
			if (start >= strLen) {
				return interp.checkCommonInteger(-1);
			}
		}

		if (substrLen == 0) {
			index = -1;
		} else if (substrLen == 1) {
			char c = substr.charAt(0);
			index = str.indexOf(c, start);
		} else {
			index = str.indexOf(substr, start);
		}
		return interp.checkCommonInteger(index);
	}

	// Implements inlined "string last" command, this
	// duplicates the logic found in StringCmd.java.
	// A TclObject that holds the new value is
	// returned.

	public static final TclObject stringLast(Interp interp, String substr, // substring
			// to
			// search
			// for
			String str, // string to search in
			TclObject lastObj) // last index (null if last is 0)
			throws TclException {
		int substrLen = substr.length();
		int strLen = str.length();
		int index;
		int last;

		if (lastObj == null) {
			last = 0;
		} else {
			last = Util.getIntForIndex(interp, lastObj, strLen - 1);
			if (last < 0) {
				return interp.checkCommonInteger(-1);
			} else if (last < strLen) {
				str = str.substring(0, last + 1);
			}
		}

		if (substrLen == 0) {
			index = -1;
		} else if (substrLen == 1) {
			char c = substr.charAt(0);
			index = str.lastIndexOf(c);
		} else {
			index = str.lastIndexOf(substr);
		}
		return interp.checkCommonInteger(index);
	}

}
