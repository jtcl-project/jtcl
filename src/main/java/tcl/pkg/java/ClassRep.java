/*
 * ClassRep.java --
 *
 *	This class implements the internal representation of a Java
 *	class name.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 * RCS: @(#) $Id: ClassRep.java,v 1.3 2000/10/29 06:00:42 mdejong Exp $
 *
 */

package tcl.pkg.java;

import tcl.lang.InternalRep;
import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclObject;

/**
 * This class implements the internal representation of a Java class name.
 */

class ClassRep implements InternalRep {

	// The class referred to by this ClassRep.

	Class cls;

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * ClassRep --
	 * 
	 * Creates a new ClassRep instance.
	 * 
	 * Side effects: Member fields are initialized.
	 * 
	 * ----------------------------------------------------------------------
	 */

	ClassRep(Class c) // Initial value for cls.
	{
		cls = c;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * duplicate --
	 * 
	 * Make a copy of an object's internal representation.
	 * 
	 * Results: Returns a newly allocated instance of the appropriate type.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public InternalRep duplicate() {
		return new ClassRep(cls);
	}

	/**
	 * Implement this no-op for the InternalRep interface.
	 */

	public void dispose() {
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * get --
	 * 
	 * Returns the Class object referred to by the TclObject.
	 * 
	 * Results: The Class object referred to by the TclObject.
	 * 
	 * Side effects: When successful, the internalRep of the signature object is
	 * converted to ClassRep.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static Class get(Interp interp, // Current interpreter.
			TclObject tclObj) // TclObject that contains a valid Java
			// class name.
			throws TclException // If the TclObject doesn't contain a valid
	// class name.
	{
		InternalRep rep = tclObj.getInternalRep();

		if (rep instanceof ClassRep) {
			// If a ClassRep is already cached, return it right away.
			return ((ClassRep) rep).cls;
		} else {
			Class c = JavaInvoke.getClassByName(interp, tclObj.toString());
			tclObj.setInternalRep(new ClassRep(c));
			return c;
		}
	}

} // end ClassRep

