/*
 * ArrayObject.java --
 *
 *	This class implements the Array Object Command, which is a special case
 *	of the Object Command.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 * RCS: @(#) $Id: ArrayObject.java,v 1.4 2002/12/30 02:30:54 mdejong Exp $
 *
 */

package tcl.pkg.java;

import java.lang.reflect.Array;

import tcl.lang.Interp;
import tcl.lang.TCL;
import tcl.lang.TclException;
import tcl.lang.TclIndex;
import tcl.lang.TclInteger;
import tcl.lang.TclList;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;

/*
 * An ArrayObject is used to create and access Java Array objects
 * using the Java Reflection API. It wraps around a Java Array object (i.e.,
 * an instance of any Java Array class) and expose it to Tcl scripts.
 */

class ArrayObject extends ReflectObject {

	static final private String validCmds[] = { "length", "get", "getrange",
			"set", "setrange" };
	static final private int OPT_LENGTH = 0;
	static final private int OPT_GET = 1;
	static final private int OPT_GETRANGE = 2;
	static final private int OPT_SET = 3;
	static final private int OPT_SETRANGE = 4;

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * cmdProc --
	 * 
	 * This cmdProc implements the Tcl command used to invoke methods of the
	 * java.lang.Object stored in this ArrayObject internal rep. For example,
	 * this method is called to process the "$v" command at the second line of
	 * this script:
	 * 
	 * set v [java::new java.util.Vector] $v addElement "foo"
	 * 
	 * Results: None.
	 * 
	 * Side effects: May set the value of array elements or default to the super
	 * class implementation of cmdProc.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	public void cmdProc(Interp interp, // Current interpreter.
			TclObject argv[]) // Argument list.
			throws TclException // If wrong number of args, a
	// standard Tcl exception;
	// If an exception is encountered
	// during the invokation of the method,
	// the Exception object will be stored
	// in the errorCode of the interp.
	{
		boolean convert;
		int optionIdx, numArgs;
		Object subArrayObj;
		Class subArrayClass;
		int option, index, numDims, count;
		TclObject indexListObj;

		if (argv.length < 2) {
			throw new TclNumArgsException(interp, 1, argv,
					"?-noconvert? option ?arg arg ...?");
		}

		String arg1 = argv[1].toString();
		if ((arg1.length() >= 2) && (NOCONVERT.startsWith(arg1))) {
			// numArgs is the number of optional arguments after the
			// sub-command.

			convert = false;
			optionIdx = 2;
			numArgs = argv.length - 3;
		} else {
			convert = true;
			optionIdx = 1;
			numArgs = argv.length - 2;
		}

		if (numArgs < 0) {
			throw new TclNumArgsException(interp, 1, argv,
					"?-noconvert? option ?arg arg ...?");
		}

		// If the <option> argument to the array object command is one of the
		// array sub-commands, then proceed to the switch statement below.
		// Otherwise, the array object behaves as a reflect object by calling
		// ReflectObject.cmdProc, and <option> will be treated as a method
		// of the array object.
		//
		// We can be sure that there is no conflect between the array
		// sub-commands and the method of the array object. This is
		// because the array object is an instance of java.lang.Object,
		// which has only the following methods (as of JDK 1.1): getClass,
		// hashCode, equals, wait, toString, notify, notifyAll

		try {
			option = TclIndex.get(interp, argv[optionIdx], validCmds, "option",
					TCL.EXACT);
		} catch (TclException e) {
			try {
				int startIdx = optionIdx + 1;

				FuncSig.get(interp, javaClass, argv[optionIdx], argv, startIdx,
						argv.length - startIdx, false);
			} catch (TclException e1) {
				throw new TclException(interp, "bad option \""
						+ argv[optionIdx]
						+ "\": must be length, get, getrange, "
						+ "set, setrange, or a valid method signature");
			}

			super.cmdProc(interp, argv);
			return;
		}

		switch (option) {
		case OPT_LENGTH:
			if (numArgs != 0) {
				throw new TclNumArgsException(interp, optionIdx + 1, argv, "");
			}
			if (convert == false) {
				throw new TclException(interp,
						"-noconvert flag not allowed for the \"length\" sub-command");
			}
			interp.setResult(Array.getLength(javaObj));
			return;

		case OPT_GET:
			if (numArgs != 1) {
				throw new TclNumArgsException(interp, optionIdx + 1, argv,
						"indexList");
			}
			indexListObj = argv[optionIdx + 1];
			numDims = TclList.getLength(interp, indexListObj);
			if (numDims == 0) {
				subArrayObj = javaObj;
				subArrayClass = dereferenceClassDims(interp, javaClass, 1);
				index = 0;
			} else {
				// Dereference all but the last dimension specified. Set
				// the interpreter result to the index'th element of the
				// "subArrayObj".

				subArrayObj = dereferenceArrayDims(interp, javaObj, numDims,
						indexListObj);
				subArrayClass = dereferenceClassDims(interp, javaClass, numDims);
				index = TclInteger.getInt(interp, TclList.index(interp,
						indexListObj, numDims - 1));
			}

			// Set the interpreter result to a TclObject containing the index'th
			// element of "subArrayObj".

			interp.setResult(getArrayElt(interp, subArrayObj, subArrayClass,
					index, convert));
			return;

		case OPT_SET:
			if (numArgs != 2) {
				throw new TclNumArgsException(interp, optionIdx + 1, argv,
						"indexList value");
			}
			if (convert == false) {
				throw new TclException(interp,
						"-noconvert flag not allowed for the \"set\" sub-command");
			}

			indexListObj = argv[optionIdx + 1];
			numDims = TclList.getLength(interp, indexListObj);
			if (numDims == 0) {
				subArrayObj = javaObj;
				subArrayClass = dereferenceClassDims(interp, javaClass, 1);
				index = 0;
			} else {
				// Dereference all but the last dimension specified. Set
				// the value of index'th element of the "subArrayObj" to
				// the value in argv[optionIdx + 2].

				subArrayObj = dereferenceArrayDims(interp, javaObj, numDims,
						indexListObj);
				subArrayClass = dereferenceClassDims(interp, javaClass, numDims);
				index = TclInteger.getInt(interp, TclList.index(interp,
						indexListObj, numDims - 1));
			}

			// Set the value of the index'th element of "subArrayObj" to the
			// value
			// in the TclObject argv[optionIdx + 2].

			setArrayElt(interp, subArrayObj, subArrayClass, index,
					argv[optionIdx + 2]);
			interp.resetResult();
			return;

		case OPT_GETRANGE:
			if (numArgs > 2) {
				throw new TclNumArgsException(interp, optionIdx + 1, argv,
						"?indexList ?count??");
			}

			// If an index list is specified, dereference all but the last
			// dimension specified. If the index list is empty, getrange
			// behaves as an identity function and returns argv[0].

			subArrayObj = javaObj;
			subArrayClass = dereferenceClassDims(interp, javaClass, 1);
			index = 0;

			if (numArgs > 0) {
				indexListObj = argv[optionIdx + 1];
				numDims = TclList.getLength(interp, indexListObj);
				if (numDims > 0) {
					subArrayObj = dereferenceArrayDims(interp, javaObj,
							numDims, indexListObj);
					subArrayClass = dereferenceClassDims(interp, javaClass,
							numDims);
					index = TclInteger.getInt(interp, TclList.index(interp,
							indexListObj, numDims - 1));
				}
			}

			// The variable "count" represents the number of elements to
			// return. The default is the array size less the first index
			// (the remaining elements of the array). If a count is
			// specified and is smaller than the default, the default is
			// overridden.

			count = Array.getLength(subArrayObj) - index;
			if (numArgs > 1) {
				count = Math.min(count, TclInteger.getInt(interp,
						argv[optionIdx + 2]));
			}

			// Set the interpreter result to a TclList containing "count"
			// elements
			// of the "subArrayObj", starting with the index'th element.

			interp.setResult(getArrayElts(interp, subArrayObj, subArrayClass,
					index, count, convert));
			return;

		case OPT_SETRANGE:
			if ((numArgs < 1) || (numArgs > 3)) {
				throw new TclNumArgsException(interp, optionIdx + 1, argv,
						"?indexList ?count?? valueList");
			}
			if (convert == false) {
				throw new TclException(interp,
						"-noconvert flag not allowed for the \"setrange\" sub-command");
			}

			TclObject tclValueListObj = argv[argv.length - 1];

			// If an index list is specified, dereference all but the last
			// dimension specified. If the index list is empty, setrange
			// initialized the array object as it would in the java::new
			// command.

			subArrayObj = javaObj;
			subArrayClass = dereferenceClassDims(interp, javaClass, 1);
			index = 0;

			if (numArgs > 1) {
				indexListObj = argv[optionIdx + 1];
				numDims = TclList.getLength(interp, indexListObj);
				if (numDims > 0) {
					subArrayObj = dereferenceArrayDims(interp, javaObj,
							numDims, indexListObj);
					subArrayClass = dereferenceClassDims(interp, javaClass,
							numDims);
					index = TclInteger.getInt(interp, TclList.index(interp,
							indexListObj, numDims - 1));
				}
			}

			// "count" represents the number of elements to set. The
			// default is the minimum of the valueList size and array size
			// less the first index (the remaining elements of the array).
			// If a count is specified and is smaller than the default,
			// the default is overridden.

			count = Math.min(TclList.getLength(interp, tclValueListObj), Array
					.getLength(subArrayObj)
					- index);

			if (numArgs > 2) {
				count = Math.min(count, TclInteger.getInt(interp,
						argv[optionIdx + 2]));
			}

			// Set the value of "count" elements of the "subArrayObj", starting
			// with the index'th element, to the values in tclValueListObj.

			setArrayElts(interp, subArrayObj, subArrayClass, index, count,
					tclValueListObj);
			interp.resetResult();
			return;
		}
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * initArray --
	 * 
	 * Call Array.newInstance and populate the array.
	 * 
	 * Results: When successful, the object created by the constructor.
	 * 
	 * Side effects: The constructor can cause arbitrary side effects.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static Object initArray(Interp interp, // Current interpreter.
			TclObject sizeListObj, // List of dimension sizes.
			int sizeListLen, // Size of sizeListObj.
			int dim, // Current dimension.
			int numDims, // Number of dimensions to allocate.
			Class cls, // Type of Array.
			TclObject valueListObj) // List to populate the Array.
			throws TclException // May encounter TclList elt of wrong
	// type in sizeListObj or valueListObj.
	{
		Class compCls = cls.getComponentType();
		int valueListLen = 0;
		if (valueListObj != null) {
			valueListLen = TclList.getLength(interp, valueListObj);
		}

		// Set arrayLength to be the dim'th dimension size in sizeListObj. If
		// sizeListObj doesn't contain dim elts, the array length is the length
		// of valueListObj.
		//
		// Initialize the "arrayObj" to size "arrayLength".

		int arrayLength;
		if (dim < sizeListLen) {
			arrayLength = TclInteger.getInt(interp, TclList.index(interp,
					sizeListObj, dim));
		} else {
			arrayLength = valueListLen;
		}
		if ((arrayLength == 0) && (dim < (numDims - 1))) {
			throw new TclException(interp, "cannot initialize a " + numDims
					+ " dimensional array with zero size in dimension " + dim);
		}

		Object arrayObj;
		try {
			arrayObj = Array.newInstance(compCls, arrayLength);
		} catch (NegativeArraySizeException ex) {
			throw new TclException(interp, "negative array size " + arrayLength);
		}

		if (compCls.isArray()) {
			// Initialize each subArray "i" according to the dim+1'st elt in
			// sizeListObj and i'th elt in valueListObj.

			int nextDim = dim + 1;
			for (int i = 0; i < arrayLength; i++) {

				TclObject subValueListObj = null;
				if (i < valueListLen) {
					subValueListObj = TclList.index(interp, valueListObj, i);
				}

				Object subArrayObj = initArray(interp, sizeListObj,
						sizeListLen, nextDim, numDims, compCls, subValueListObj);
				Array.set(arrayObj, i, subArrayObj);
			}
		} else if (valueListLen > 0) {
			// Set the value of "count" elements of the "subArrayObj", starting
			// with the 0'th element, to the values in valueListObj.

			int count = Math.min(arrayLength, valueListLen);
			setArrayElts(interp, arrayObj, compCls, 0, count, valueListObj);
		}
		return arrayObj;
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * dereferenceArrayDims --
	 * 
	 * Dereference "numDims - 1" dimensions of the array. Return a non-null
	 * pointer to the remaining array.
	 * 
	 * Results: Returns an array cell object.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	private static Object dereferenceArrayDims(Interp interp, // Current
			// interpreter.
			Object arrayObj, // Array to dereference. Must be an array.

			int numDerefDims, // Number of dimensions to dereference.
			TclObject indexListObj) // Index to dereference in each dim.
			throws TclException // May encounter bad array index or
	// dereference a null array value.
	{
		// Before derefencing any dimensions, check that the indexList isn't too
		// large--we want to return an array.

		int numDims = JavaInfoCmd.getNumDimsFromClass(arrayObj.getClass());
		if (numDims < numDerefDims) {
			throw new TclException(interp, "bad indexList \""
					+ indexListObj.toString() + "\": javaObj only has "
					+ numDims + " dimension(s)");
		}

		Object subArrayObj = arrayObj;
		for (int dim = 0; dim < numDerefDims - 1; dim++) {

			int index = TclInteger.getInt(interp, TclList.index(interp,
					indexListObj, dim));
			try {
				subArrayObj = Array.get(subArrayObj, index);
			} catch (ArrayIndexOutOfBoundsException e) {
				int max = Array.getLength(subArrayObj) - 1;
				throw new TclException(interp, "array index \"" + index
						+ "\" is out of bounds: must be between 0 and " + max);
			}
			if (subArrayObj == null) {
				throw new TclException(interp, "null value in dimension " + dim
						+ ": can't dereference " + numDims + " dimensions");
			}
		}
		return subArrayObj;
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * dereferenceClassDims --
	 * 
	 * Dereference class numDims dimensions of the array class type
	 * 
	 * Results: Returns an sub array. ex: class Object[][] -> Object[] -> Object
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	private static Class dereferenceClassDims(Interp interp, // Current
			// interpreter.
			Class arrayClass, // Array class type.

			int numDerefDims) // Number of dimensions to dereference.
			throws TclException // May encounter bad array index or
	// dereference a null array value.
	{
		// Before derefencing class, check that the numDerefDims isn't too large

		int numDims = JavaInfoCmd.getNumDimsFromClass(arrayClass);
		if (numDims < numDerefDims) {
			throw new TclException(interp,
					"bad class dereference class only has " + numDims
							+ " dimension(s)");
		}

		Class subArrayClass = arrayClass;
		for (int dim = 0; dim < numDerefDims; dim++) {
			subArrayClass = subArrayClass.getComponentType();
		}
		return subArrayClass;
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * getArrayElts --
	 * 
	 * Return a TclList containing "count" elements of "arrayObj", starting with
	 * the index'th element.
	 * 
	 * Results: Returns a TclListObject.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static TclObject getArrayElts(Interp interp, // Current interpreter.
			Object arrayObj, // Array to dereference. Must be an array.
			Class arrayClass, // Class object of array to deref
			int index, // First elt to dereference.
			int count, // Number of elts to dereference.
			boolean convert) // Whether the values should be converted
			// into Tcl objects of the closest types.
			throws TclException // May encounter bad index.
	{
		TclObject resultListObj = TclList.newInstance();
		try {
			for (int i = 0; i < count; i++, index++) {
				TclList.append(interp, resultListObj, getArrayElt(interp,
						arrayObj, arrayClass, index, convert));
			}
		} catch (TclException e) {
			resultListObj.release();
			throw e;
		}
		return resultListObj;
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * getArrayElt --
	 * 
	 * Return a TclObject containing an element elements of "arrayObj", at the
	 * index'th element.
	 * 
	 * Results: Returns a TclObject.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static TclObject getArrayElt(Interp interp, // Current interpreter.
			Object arrayObj, // Array to dereference. Must be an array.
			Class arrayClass, // Class object of array to deref
			int index, // First elt to dereference.
			boolean convert) throws TclException // May encounter bad index.
	{
		// Set "obj" to the index'th element of the arrayObj. If Array.get()
		// fails, reset the interp result to cover error message, and set
		// "obj" to null. Wrap "obj" in a TclObject and append it to the
		// result list.

		Object obj;
		try {
			obj = Array.get(arrayObj, index);
		} catch (ArrayIndexOutOfBoundsException e) {
			int max = Array.getLength(arrayObj) - 1;
			throw new TclException(interp, "array index \"" + index
					+ "\" is out of bounds: must be between 0 and " + max);
		}

		/*
		 * 
		 * System.out.println("object class is " + arrayObj.getClass());
		 * System.out.println("object component type is " +
		 * arrayObj.getClass().getComponentType());
		 * 
		 * System.out.println("array class is " + arrayClass);
		 * System.out.println("array component type is " +
		 * arrayClass.getComponentType());
		 * System.out.println("obj derived type is " + obj.getClass());
		 */

		if (convert) {
			return JavaInvoke.convertJavaObject(interp, arrayClass, obj);
		} else {
			return ReflectObject.newInstance(interp, arrayClass, obj);
		}

	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * setArrayElts --
	 * 
	 * Set the value of "count" elements of the "arrayObj", starting with the
	 * index'th element, to the first "count" elements of "valueList". Throw a
	 * TclException if arrayObj has fewer than "index" elements. Throw a
	 * TclException if an element of "valueList" cannot be converted to the
	 * component type of "arrayObj".
	 * 
	 * Results: None.
	 * 
	 * Side effects: Sets "count" elements of "arrayObj".
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static void setArrayElts(Interp interp, // Current interpreter.
			Object arrayObj, // Array whose elts to set. Must be an array.
			Class arrayClass, // Class object of array to deref
			int index, // First elt to set.
			int count, // Number of elts to set.
			TclObject tclValueListObj) // List of values to assign.
			throws TclException // May encounter bad index,
	// or wrong type in tclValueListObj.
	{
		for (int i = 0; i < count; i++, index++) {
			setArrayElt(interp, arrayObj, arrayClass, index, TclList.index(
					interp, tclValueListObj, i));
		}
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * setArrayElt --
	 * 
	 * Set the value of "count" elements of the "arrayObj", starting with the
	 * index'th element, to the first "count" elements of "valueList". Throw a
	 * TclException if arrayObj has fewer than "index" elements. Throw a
	 * TclException if an element of "valueList" cannot be converted to the
	 * component type of "arrayObj".
	 * 
	 * Results: None.
	 * 
	 * Side effects: Sets "count" elements of "arrayObj".
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static void setArrayElt(Interp interp, // Current interpreter.
			Object arrayObj, // Array whose elts to set. Must be an array.
			Class arrayClass, // Class object of array to deref
			int index, // First elt to set.
			TclObject value) // Value to assign.
			throws TclException // May encounter bad index,
	// or wrong type in tclValueListObj.
	{
		// Class componentType = arrayClass.getComponentType();
		Class componentType = arrayClass;

		/*
		 * 
		 * System.out.println("object class is " + arrayObj.getClass());
		 * System.out.println("object component type is " +
		 * arrayObj.getClass().getComponentType());
		 * 
		 * 
		 * System.out.println("array class is " + arrayClass);
		 * System.out.println("array component type is " +
		 * arrayClass.getComponentType());
		 */

		Object javaValue = JavaInvoke.convertTclObject(interp, componentType,
				value);

		// Set the arrayObj[index] to valueObj. If the array has a primitive
		// component type, the new value is automatically unwrapped by
		// Array.set().

		try {
			Array.set(arrayObj, index, javaValue);
		} catch (ArrayIndexOutOfBoundsException e) {
			int max = Array.getLength(arrayObj) - 1;
			throw new TclException(interp, "array index \"" + index
					+ "\" is out of bounds: must be between 0 and " + max);
		}
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * getBaseName --
	 * 
	 * Return the name of the base class of the class with internal-rep
	 * "clsName".
	 * 
	 * Results: Returns a base class name.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static String getBaseName(String clsName) // String name of the class.
	{
		// If the string is of the form className[][]..., strip out the trailing
		// []s.

		if (clsName.endsWith("[]")) {
			int end = clsName.indexOf('[');
			return clsName.substring(0, end);
		}

		// If the string begins with '[', strip off the leading '['s and convert
		// of
		// base code to the string it represents.

		if (clsName.charAt(0) == '[') {
			if (clsName.endsWith("[")) {
				return clsName;
			}
			String baseName = clsName.substring(1);
			while (baseName.charAt(0) == '[') {
				baseName = baseName.substring(1);
			}
			if ((baseName.charAt(0) == 'L') && (baseName.endsWith(";"))) {
				return baseName.substring(1, baseName.length() - 1);
			} else if (baseName.charAt(0) == 'I') {
				return "int";
			} else if (baseName.charAt(0) == 'Z') {
				return "boolean";
			} else if (baseName.charAt(0) == 'J') {
				return "long";
			} else if (baseName.charAt(0) == 'F') {
				return "float";
			} else if (baseName.charAt(0) == 'D') {
				return "double";
			} else if (baseName.charAt(0) == 'B') {
				return "byte";
			} else if (baseName.charAt(0) == 'S') {
				return "short";
			} else if (baseName.charAt(0) == 'C') {
				return "char";
			}
		}

		// "clsName" is not array class name, so it must be a base class.

		return clsName;
	}

} // end ArrayObject

