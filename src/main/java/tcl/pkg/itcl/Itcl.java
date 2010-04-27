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
 *     RCS:  $Id: Itcl.java,v 1.1 2005/09/11 20:56:57 mdejong Exp $
 * ========================================================================
 *           Copyright (c) 1993-1998  Lucent Technologies, Inc.
 * ------------------------------------------------------------------------
 * See the file "license.itcl" for information on usage and redistribution
 * of this file, and for a DISCLAIMER OF ALL WARRANTIES.
 */

package tcl.pkg.itcl;

import java.util.Stack;

class Itcl {

	// Constants: ITCL_MAJOR_VERSION -> Itcl.MAJOR_VERSION

	static int MAJOR_VERSION = 3;
	static int MINOR_VERSION = 3;
	static int RELEASE_LEVEL = 2;
	static int RELEASE_SERIAL = 0;

	static String VERSION = "3.3";
	static String PATCH_LEVEL = "3.3.0";

	// Protection levels:
	//
	// PUBLIC - accessible from any namespace
	// PROTECTED - accessible from namespace that imports in "protected" mode
	// PRIVATE - accessible only within the namespace that contains it
	//

	final static int PUBLIC = 1;
	final static int PROTECTED = 2;
	final static int PRIVATE = 3;
	final static int DEFAULT_PROTECT = 4;

} // end class Itcl

class Itcl_Stack {
	Stack s = null;
}

// Generic linked list.

class Itcl_ListElem {
	Itcl_List owner; // list containing this element
	Object value; // value associated with this element
	Itcl_ListElem prev; // previous element in linked list
	Itcl_ListElem next; // next element in linked list
}

class Itcl_List {
	int validate; // validation stamp
	int num; // number of elements
	Itcl_ListElem head; // previous element in linked list
	Itcl_ListElem tail; // next element in linked list
}
