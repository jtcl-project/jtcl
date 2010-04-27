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
 * ========================================================================
 *  AUTHOR:  Michael J. McLennan
 *           Bell Labs Innovations for Lucent Technologies
 *           mmclennan@lucent.com
 *           http://www.tcltk.com/itcl
 *
 *     RCS:  $Id: ItclInt.java,v 1.3 2006/01/26 19:49:18 mdejong Exp $
 * ========================================================================
 *           Copyright (c) 1993-1998  Lucent Technologies, Inc.
 * ------------------------------------------------------------------------
 * See the file "license.itcl" for information on usage and redistribution
 * of this file, and for a DISCLAIMER OF ALL WARRANTIES.
 */

package tcl.pkg.itcl;

import java.util.HashMap;

import tcl.lang.AssocData;
import tcl.lang.CallFrame;
import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.Namespace;
import tcl.lang.Procedure;
import tcl.lang.TclException;
import tcl.lang.TclObject;
import tcl.lang.Var;
import tcl.lang.VarTrace;
import tcl.lang.WrappedCommand;

class ItclObjectInfo implements AssocData, ItclEventuallyFreed {

	Interp interp; // interpreter that manages this info

	HashMap objects; // Maps Command to ItclObject
	// for all known objects

	Itcl_Stack transparentFrames; // stack of call frames that should be
	// treated transparently. When
	// Itcl_EvalMemberCode is invoked in
	// one of these contexts, it does an
	// "uplevel" to get past the transparent
	// frame and back to the calling context.
	HashMap contextFrames; // object contexts for active call frames

	int protection; // protection level currently in effect

	Itcl_Stack cdefnStack; // stack of class definitions currently

	// being parsed

	// No implementation for the assoc data delete callback.
	// Itcl ref counting is used to track use of a ItclObjectInfo.

	public void disposeAssocData(Interp interp) {
		// No-op
	}

	// Invoke via Util.ReleaseData() when refCount drops to 0.

	public void eventuallyFreed() {
		Cmds.DelObjectInfo(this);
	}
}

// Representation for each [incr Tcl] class.

class ItclClass implements ItclEventuallyFreed {
	String name; // class name
	String fullname; // fully qualified class name
	Interp interp; // interpreter that manages this info
	Namespace namesp; // namespace representing class scope
	Command accessCmd; // access command for creating instances
	WrappedCommand w_accessCmd; // WrappedCommand for accessCmd

	ItclObjectInfo info; // info about all known objects
	Itcl_List bases; // list of base classes
	Itcl_List derived; // list of all derived classes
	HashMap heritage; // table of all base classes that provides
	// provides fast lookup for inheritance tests.
	// Maps ItclClass to the empty string.
	TclObject initCode; // initialization code for new objs
	HashMap variables; // definitions for all data members
	// in this class. Look up simple string
	// names and get back ItclVarDefn refs
	HashMap functions; // definitions for all member functions
	// in this class. Look up simple string
	// names and get back ItclMemberFunc refs
	int numInstanceVars; // number of instance vars in variables
	// table
	HashMap resolveVars; // all possible names for variables in
	// this class (e.g., x, foo::x, etc.)
	// Maps String to ItclVarLookup.
	HashMap resolveCmds; // all possible names for functions in
	// this class (e.g., x, foo::x, etc.)
	// Maps String to ItclMemberFunc.
	int unique; // unique number for #auto generation
	int flags; // maintains class status

	// Invoke via ItclEventuallyFreed interface when
	// refCount for this class drops to 0

	public void eventuallyFreed() {
		Class.FreeClass(this);
	}
}

class ItclHierIter {
	ItclClass current; // current position in hierarchy
	Itcl_Stack stack; // stack used for traversal
}

// Representation for each [incr Tcl] object.

class ItclObject implements ItclEventuallyFreed, VarTrace {
	ItclClass classDefn; // most-specific class
	Command accessCmd; // object access command
	WrappedCommand w_accessCmd; // WrappedCommand for accessCmd

	int dataSize; // number of elements in data array
	Var[] data; // all object-specific data members
	HashMap constructed; // temp storage used during construction
	// Maps class name String to the empty string.
	HashMap destructed; // temp storage used during destruction

	// Maps class name String to the empty string.

	// Invoke via ItclEventuallyFreed interface when
	// refCount for this instance drops to 0

	public void eventuallyFreed() {
		Objects.FreeObject(this);
	}

	// traceProc is invoked to handle variable traces on
	// the "this" instance variable.

	public void traceProc(Interp interp, String part1, String part2, int flags)
			throws TclException {
		Objects.TraceThisVar(this, interp, part1, part2, flags);
	}
}

// Implementation for any code body in an [incr Tcl] class.

class ItclMemberCode implements ItclEventuallyFreed {
	int flags; // flags describing implementation
	CompiledLocal arglist; // list of arg names and initial values
	int argcount; // number of args in arglist
	Procedure proc; // Tcl proc representation
	String body; // String based representation of proc "body".
	// Used for both a Tcl proc and one
	// implemented via a Java Command.

	Command objCmd; // Java style objv Command

	// Object clientData; // client data for Java implementations

	// Invoke via ItclEventuallyFreed interface when
	// refCount for this instance drops to 0

	public void eventuallyFreed() {
		Methods.DeleteMemberCode(this);
	}
}

// Basic representation for class members (commands/variables)

class ItclMember {
	Interp interp; // interpreter containing the class
	ItclClass classDefn; // class containing this member
	String name; // member name
	String fullname; // member name with "class::" qualifier
	int protection; // protection level
	int flags; // flags describing member (see below)
	ItclMemberCode code; // code associated with member
}

// Constants: ITCL_IGNORE_ERRS -> ItclInt.IGNORE_ERRS

class ItclInt {
	static int IMPLEMENT_NONE = 0x001; // no implementation
	static int IMPLEMENT_TCL = 0x002; // Tcl implementation
	static int IMPLEMENT_ARGCMD = 0x004; // (argc,argv) implementation (unused)
	static int IMPLEMENT_OBJCMD = 0x008; // (objc,objv) implementation (unused)
	static int IMPLEMENT_C = 0x00c; // either of previous two (unused)
	static int CONSTRUCTOR = 0x010; // non-zero => is a constructor
	static int DESTRUCTOR = 0x020; // non-zero => is a destructor
	static int COMMON = 0x040; // non-zero => is a "proc"
	static int ARG_SPEC = 0x080; // non-zero => has an argument spec
	static int OLD_STYLE = 0x100; // non-zero => old-style method
	// (process "config" argument)
	// (unused)
	static int THIS_VAR = 0x200; // non-zero => built-in "this" variable

	static int IGNORE_ERRS = 0x002; // useful for construction/destruction
	static String INTERP_DATA = "itcl_data";
}

// Representation of member functions in an [incr Tcl] class.

class ItclMemberFunc implements ItclEventuallyFreed {
	ItclMember member; // basic member info
	Command accessCmd; // Tcl command installed for this function
	WrappedCommand w_accessCmd; // WrappedCommand for accessCmd

	CompiledLocal arglist; // list of arg names and initial values
	int argcount; // number of args in arglist

	// Invoke via ItclEventuallyFreed interface when
	// refCount for this instance drops to 0

	public void eventuallyFreed() {
		Methods.DeleteMemberFunc(this);
	}
}

// Instance variables.

class ItclVarDefn {
	ItclMember member; // basic member info
	String init; // initial value
}

// Instance variable lookup entry.

class ItclVarLookup {
	ItclVarDefn vdefn; // variable definition
	int usage; // number of uses for this record
	boolean accessible; // true => accessible from class with
	// this lookup record in its resolveVars
	String leastQualName; // simplist name for this variable, with
	// the fewest qualifiers. This string is
	// taken from the resolveVars table, so
	// it shouldn't be freed.

	int index; // index into virtual table (instance data)
	Var common; // variable (common data)
}

// Representation for the context in which a body of [incr Tcl]
// code executes. In ordinary Tcl, this is a CallFrame. But for
// [incr Tcl] code bodies, we must be careful to set up the
// CallFrame properly, to plug in instance variables before
// executing the code body.

class ItclContext {
	ItclClass classDefn; // class definition
	CallFrame frame; // call frame for object context

	// Var[] compiledLocals; // array that holds references to compiled locals

	ItclContext(Interp interp) {
		frame = ItclAccess.newCallFrame(interp);
	}
}
