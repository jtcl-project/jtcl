/*
 * BeanEventParam.java --
 *
 *	This class stores the parameters that are passed to the event
 *	method when an event is fired.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 * RCS: @(#) $Id: BeanEventParamSet.java,v 1.1.1.1 1998/10/14 21:09:13 cvsadmin Exp $
 */

package tcl.pkg.java;


/*
 * This class stores the parameters that are passed to the event
 * method when an event is fired.
 */

class BeanEventParamSet {

	/*
	 * The types of the parameters.
	 */

	Class paramTypes[];

	/*
	 * The parameters to the event method that has been fired.
	 */

	Object params[];

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * BeanEventParamSet --
	 * 
	 * Creates a new BeanEventParamSet instance.
	 * 
	 * Side effects: Member fields are initialized.
	 * 
	 * ----------------------------------------------------------------------
	 */

	BeanEventParamSet(Class t[], // Initial value for paramTypes.
			Object p[]) // Initial value for params.
	{
		paramTypes = t;
		params = p;
	}

} // end BeanEventParam

