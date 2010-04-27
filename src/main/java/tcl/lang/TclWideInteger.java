/*
 * TclWideInteger.java --
 *
 *	Implements the TclWideInteger internal object representation
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: TclWideInteger.java,v 1.1 2009/07/23 10:42:15 rszulgo Exp $
 *
 */

package tcl.lang;

/*
 * This class implements the long object type in Tcl.
 */

public class TclWideInteger implements InternalRep {

	/*
	 * Internal representation of a wide integer (long) value. This field is
	 * package scoped so that the expr module can quickly read the value.
	 */

	long value;

	// Extra debug checking

	private final static boolean validate = false;

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * TclWideInteger --
	 * 
	 * Construct a TclWideInteger representation with the given long value.
	 * 
	 * Results: None.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private TclWideInteger(long l) // Initial value.
	{
		value = l;

		if (TclObject.saveObjRecords) {
			String key = "TclWideInteger";
			Integer num = (Integer) TclObject.objRecordMap.get(key);
			if (num == null) {
				num = new Integer(1);
			} else {
				num = new Integer(num.intValue() + 1);
			}
			TclObject.objRecordMap.put(key, num);
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * TclWideInteger --
	 * 
	 * Construct a TclWideInteger representation with the initial value taken
	 * from the given string.
	 * 
	 * Results: None.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private TclWideInteger(Interp interp, // Current interpreter.
			String str) // String that contains the initial value.
			throws TclException // If error occurs in string conversion.
	{
		value = Util.getWideInt(interp, str);
		if (TclObject.saveObjRecords) {
			String key = "TclWideInteger";
			Integer num = (Integer) TclObject.objRecordMap.get(key);
			if (num == null) {
				num = new Integer(1);
			} else {
				num = new Integer(num.intValue() + 1);
			}
			TclObject.objRecordMap.put(key, num);
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * duplicate --
	 * 
	 * Duplicate the current object.
	 * 
	 * Results: A dupilcate of the current object.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public InternalRep duplicate() {
		if (TclObject.saveObjRecords) {
			String key = "TclWideInteger.duplicate()";
			Integer num = (Integer) TclObject.objRecordMap.get(key);
			if (num == null) {
				num = new Integer(1);
			} else {
				num = new Integer(num.intValue() + 1);
			}
			TclObject.objRecordMap.put(key, num);
		}

		return new TclWideInteger(value);
	}

	/**
	 * Implement this no-op for the InternalRep interface.
	 */

	public void dispose() {
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * newInstance --
	 * 
	 * Creates a new instance of a TclObject with a TclWideInteger internal
	 * representation.
	 * 
	 * Results: The newly created TclObject.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public static TclObject newInstance(long l) // Initial value.
	{
		return new TclObject(new TclWideInteger(l));
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * setWideIntFromAny --
	 * 
	 * Called to convert a TclObject's internal rep to TclWideInteger.
	 * 
	 * Results: None.
	 * 
	 * Side effects: When successful, the internal representation of tobj is
	 * changed to TclWideInt, if it is not already so.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private static void setWideIntFromAny(Interp interp, // Current interpreter.
															// May be null.
			TclObject tobj) // The object to convert.
			throws TclException // If error occurs in type conversion.
	// Error message will be left inside
	// the interp if it's not null.

	{
		// This method is only ever invoked from TclWideInteger.get().
		// This method will never be invoked when the internal
		// rep is already a TclWideInteger. This method will always
		// reparse a double from the string rep so that tricky
		// special cases like "040" are handled correctly.

		if (validate) {
			if (tobj.getInternalRep() instanceof TclWideInteger) {
				throw new TclRuntimeError(
						"should not be TclWideInteger, was a "
								+ tobj.getInternalRep().getClass().getName());
			}
		}

		tobj.setInternalRep(new TclWideInteger(interp, tobj.toString()));

		if (TclObject.saveObjRecords) {
			String key = "TclString -> TclWideInteger";
			Integer num = (Integer) TclObject.objRecordMap.get(key);
			if (num == null) {
				num = new Integer(1);
			} else {
				num = new Integer(num.intValue() + 1);
			}
			TclObject.objRecordMap.put(key, num);
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * get --
	 * 
	 * Returns the long value of the object.
	 * 
	 * Results: The long value of the object.
	 * 
	 * Side effects: When successful, the internal representation of tobj is
	 * changed to TclWideInteger, if it is not already so.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public static long get(Interp interp, // Current interpreter. May be null.
			TclObject tobj) // The object to query.
			throws TclException // If the object does not have a TclWideInteger
	// representation and a conversion fails.
	// Error message will be left inside
	// the interp if it's not null.
	{
		TclWideInteger tWideInt;

		if (!tobj.isDoubleType()) {
			if (Util.isJacl()) {
				// Try to convert to TclWideInteger. If the string can't be
				// parsed as a long, then raise a TclException here.

				setWideIntFromAny(interp, tobj);
				long lval;
				tWideInt = (TclWideInteger) tobj.getInternalRep();
				lval = tWideInt.value;

				// The string can be parsed as a long, but if it
				// can also be parsed as an integer then we need
				// to convert the internal rep back to TclInteger.
				// This logic handles the special case of an octal
				// string like "040". The most common path through
				// this code is a normal double like "1.0", so this
				// code will only attempt a conversion to TclInteger
				// when the string looks like an integer. This logic
				// is tricky, but it leads to a speedup in performance
				// critical expr code since the double value from a
				// TclWideInteger can be used without having to check to
				// see if the double looks like an integer.

				if (Util.looksLikeInt(tobj.toString())) {
					try {
						int ival = TclInteger.get(null, tobj);

						// A tricky octal like "040" can be parsed as
						// the double 40.0 or the integer 32, return
						// the value parsed as a double and leave
						// the object with a TclInteger internal rep.

						return lval;
					} catch (TclException te) {
						throw new TclRuntimeError("looksLikeInt() is true, "
								+ "but TclInteger.get() failed for \""
								+ tobj.toString() + "\"");
					}
				}

				if (validate) {
					// Double check that we did not just create a TclWideInteger
					// that looks like an integer.

					InternalRep tmp = tobj.getInternalRep();
					if (!(tmp instanceof TclWideInteger)) {
						throw new TclRuntimeError("not a TclWideInteger, is a "
								+ tmp.getClass().getName());
					}
					String stmp = tobj.toString();
					if (Util.looksLikeInt(stmp)) {
						throw new TclRuntimeError("looks like an integer");
					}
				}
			} else {
				setWideIntFromAny(interp, tobj);
				tWideInt = (TclWideInteger) tobj.getInternalRep();
			}
		} else {
			tWideInt = (TclWideInteger) tobj.getInternalRep();
		}

		return tWideInt.value;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * set --
	 * 
	 * Changes the long value of the object.
	 * 
	 * Results: None.
	 * 
	 * Side effects: The internal representation of tobj is changed to
	 * TclWideInteger, if it is not already so.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public static void set(TclObject tobj, // The object to modify.
			long l) // The new value for the object.
	{
		tobj.invalidateStringRep();

		if (tobj.isWideIntType()) {
			TclWideInteger tWideInt = (TclWideInteger) tobj.getInternalRep();
			tWideInt.value = l;
		} else {
			tobj.setInternalRep(new TclWideInteger(l));
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * toString --
	 * 
	 * Called to query the string representation of the Tcl object. This method
	 * is called only by TclObject.toString() when TclObject.stringRep is null.
	 * 
	 * Results: Returns the string representation of the TclWideInteger object.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public String toString() {
		return String.valueOf(value);
	}

	/**
	 * This special helper method is used only by the Expression module. This
	 * method will change the internal rep to a TclWideInteger with the passed
	 * in long value. This method does not invalidate the string rep since the
	 * object's value is not being changed.
	 * 
	 * @param tobj
	 *            the object to operate on.
	 * @param d
	 *            the new double value.
	 */
	static void exprSetInternalRep(TclObject tobj, long l) {
		if (validate) {

			// Double check that the internal rep is not
			// already of type TclWideInteger.

			InternalRep rep = tobj.getInternalRep();

			if (rep instanceof TclWideInteger) {
				throw new TclRuntimeError(
						"exprSetInternalRep() called with object"
								+ " that is already of type TclWideInteger");
			}

			// Double check that the new int value and the
			// string rep would parse to the same integer.

			double d2;
			try {
				d2 = Util.getWideInt(null, tobj.toString());
			} catch (TclException te) {
				throw new TclRuntimeError(
						"exprSetInternalRep() called with long"
								+ " value that could not be parsed from the string");
			}
			if (l != d2) {
				throw new TclRuntimeError(
						"exprSetInternalRep() called with long value " + l
								+ " that does not match parsed long value "
								+ d2 + ", parsed from str \"" + tobj.toString()
								+ "\"");
			}

			// It should not be possible to parse the TclObject's string
			// rep as an integer since we know it is a long. An object
			// that could be parsed as either a long or an integer
			// should have been parsed as an integer.

			try {
				int ival = Util.getInt(null, tobj.toString());
				throw new TclRuntimeError(
						"should not be able to parse string rep as int: "
								+ tobj.toString());
			} catch (TclException e) {
				// No-op
			}
		}

		tobj.setInternalRep(new TclWideInteger(l));
	}

	// This method is used to set the internal rep for a recycled
	// object to TclWideInteger, in the edge case where it might have
	// been changed. This method exists only because the
	// TclWideInteger ctor can't be made package access without
	// changing signature regression tests.

	static void setRecycledInternalRep(TclObject tobj) {
		tobj.setInternalRep(new TclWideInteger(0L));
	}

} // end TclWideInteger

