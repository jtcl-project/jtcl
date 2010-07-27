/*
 * JavaForCmd.java --
 *
 *	Implements the built-in "java::for" command.
 *
 * Copyright (c) 2006 by Moses DeJong
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 * RCS: @(#) $Id: JavaForCmd.java,v 1.1 2006/04/13 20:07:04 mdejong Exp $
 *
 */

package tcl.pkg.java;

import java.util.Collection;
import java.util.Iterator;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.TCL;
import tcl.lang.TclDouble;
import tcl.lang.TclException;
import tcl.lang.TclInteger;
import tcl.lang.TclList;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;
import tcl.lang.TclRuntimeError;
import tcl.lang.TclString;

/**
 * This class implements the built-in "java::for" command. This command is
 * semantically equivalent to the enhanced Java for statement in JDK 1.5 and
 * newer. It is used to iterate over Collections and can also be used for
 * arrays.
 */

public class JavaForCmd implements Command {
	public void cmdProc(Interp interp, TclObject[] objv) throws TclException {
		if (objv.length != 4) {
			throw new TclNumArgsException(interp, 1, objv,
					"type_var collection script");
		}

		TclObject type_var = objv[1];
		TclObject collectionObj = objv[2];
		TclObject script = objv[3];

		// Extract {type varname} for iteration var

		Class type = null;
		String typename;
		String varname;

		if (TclList.getLength(interp, type_var) != 2) {
			throw new TclException(interp, "invalid type_var \"" + type_var
					+ "\"");
		}
		typename = TclList.index(interp, type_var, 0).toString();
		varname = TclList.index(interp, type_var, 1).toString();
		if (typename.length() == 0 || varname.length() == 0) {
			throw new TclException(interp, "invalid type_var \"" + type_var
					+ "\"");
		}

		// Make sure Var is a simple scalar name. It can't be
		// an array name or a scoped variable name.

		if (isArrayVarname(varname) || varname.indexOf("::") != -1) {
			throw new TclException(interp, "invalid type_var \"" + type_var
					+ "\", varname must be simple scalar");
		}

		// Get Class object that corresponds to typename.
		// The void type is not allowed.

		if (typename.equals("void") || typename.equals("Void")
				|| typename.equals("java.lang.Void")) {
			throw new TclException(interp, "void type is invalid");
		}

		type = JavaInvoke.getClassByName(interp, typename);

		// Get the passes in collection object and check
		// its type against the passed in type.

		Object collection_obj = ReflectObject.get(interp, collectionObj);
		Class collection_class = ReflectObject.getClass(interp, collectionObj);

		// Determine if the type name given is a primitive type
		// or an object type. A primitive array pass as the collection
		// object must match the primitive type exactly. A
		// primitive array does not implement the Collection
		// interface, but the Java construct supports the usage.

		final int ARRAY_OBJECT = 1; // Any Object in an array
		final int ARRAY_STRING = 2;
		final int ARRAY_INT = 3;
		final int ARRAY_BOOLEAN = 4;
		final int ARRAY_LONG = 5;
		final int ARRAY_FLOAT = 6;
		final int ARRAY_DOUBLE = 7;
		final int ARRAY_BYTE = 8;
		final int ARRAY_SHORT = 9;
		final int ARRAY_CHAR = 10;
		final int COLLECTION = 11; // Any Object in a Collection

		int typeId;

		int[] intArray = null;
		int intIndexValue;

		boolean[] booleanArray = null;
		boolean booleanIndexValue;

		long[] longArray = null;
		long longIndexValue;

		float[] floatArray = null;
		float floatIndexValue;

		double[] doubleArray = null;
		double doubleIndexValue;

		byte[] byteArray = null;
		byte byteIndexValue;

		short[] shortArray = null;
		short shortIndexValue;

		char[] charArray = null;
		char charIndexValue;

		String[] stringArray = null;
		String stringIndexValue;

		Object[] objArray = null;
		// Object objIndexValue;

		int arrayLength = 0;

		if (!collection_class.isArray()) {
			// If argument is not an array, then it has to be a Collection
			typeId = COLLECTION;
		} else if (type == Integer.TYPE) {
			typeId = ARRAY_INT;

			if (!(collection_obj instanceof int[])) {
				throw new TclException(interp,
						"expected collection object to be array of int");
			} else {
				intArray = (int[]) collection_obj;
				arrayLength = intArray.length;
			}
		} else if (type == Boolean.TYPE) {
			typeId = ARRAY_BOOLEAN;

			if (!(collection_obj instanceof boolean[])) {
				throw new TclException(interp,
						"expected collection object to be array of boolean");
			} else {
				booleanArray = (boolean[]) collection_obj;
				arrayLength = booleanArray.length;
			}
		} else if (type == Long.TYPE) {
			typeId = ARRAY_LONG;

			if (!(collection_obj instanceof long[])) {
				throw new TclException(interp,
						"expected collection object to be array of long");
			} else {
				longArray = (long[]) collection_obj;
				arrayLength = longArray.length;
			}
		} else if (type == Float.TYPE) {
			typeId = ARRAY_FLOAT;

			if (!(collection_obj instanceof float[])) {
				throw new TclException(interp,
						"expected collection object to be array of float");
			} else {
				floatArray = (float[]) collection_obj;
				arrayLength = floatArray.length;
			}
		} else if (type == Double.TYPE) {
			typeId = ARRAY_DOUBLE;

			if (!(collection_obj instanceof double[])) {
				throw new TclException(interp,
						"expected collection object to be array of double");
			} else {
				doubleArray = (double[]) collection_obj;
				arrayLength = doubleArray.length;
			}
		} else if (type == Byte.TYPE) {
			typeId = ARRAY_BYTE;

			if (!(collection_obj instanceof byte[])) {
				throw new TclException(interp,
						"expected collection object to be array of byte");
			} else {
				byteArray = (byte[]) collection_obj;
				arrayLength = byteArray.length;
			}
		} else if (type == Short.TYPE) {
			typeId = ARRAY_SHORT;

			if (!(collection_obj instanceof short[])) {
				throw new TclException(interp,
						"expected collection object to be array of short");
			} else {
				shortArray = (short[]) collection_obj;
				arrayLength = shortArray.length;
			}
		} else if (type == Character.TYPE) {
			typeId = ARRAY_CHAR;

			if (!(collection_obj instanceof char[])) {
				throw new TclException(interp,
						"expected collection object to be array of char");
			} else {
				charArray = (char[]) collection_obj;
				arrayLength = charArray.length;
			}
		} else if (type == String.class) {
			typeId = ARRAY_STRING;

			if (!(collection_obj instanceof String[])) {
				throw new TclException(interp,
						"expected collection object to be array of String");
			} else {
				stringArray = (String[]) collection_obj;
				arrayLength = stringArray.length;
			}
		} else {
			typeId = ARRAY_OBJECT;

			if (!(collection_obj instanceof Object[])) {
				throw new TclException(interp,
						"expected collection object to be array of Object");
			} else {
				objArray = (Object[]) collection_obj;
				arrayLength = objArray.length;
			}
		}

		// For a collection object that is not an
		// array of a primitive type, check that
		// the object implements the Collection
		// interface.

		Collection collection = null;
		if (typeId == COLLECTION) {
			if (!(collection_obj instanceof Collection)) {
				throw new TclException(interp, "passed collection argument "
						+ "of type "
						+ JavaInfoCmd.getNameFromClass(collection_class)
						+ " which does not implement Collection interface");
			}
			collection = (Collection) collection_obj;
		}

		// Loop over elements in the collection and eval script

		boolean done;
		Iterator iterator = null;
		Object elem;
		Class elem_class;
		int i = 0;

		// loop init

		switch (typeId) {
		case COLLECTION: {
			iterator = collection.iterator();
			break;
		}
		case ARRAY_OBJECT:
		case ARRAY_STRING:
		case ARRAY_INT:
		case ARRAY_BOOLEAN:
		case ARRAY_LONG:
		case ARRAY_FLOAT:
		case ARRAY_DOUBLE:
		case ARRAY_BYTE:
		case ARRAY_SHORT:
		case ARRAY_CHAR: {
			// No-op
			break;
		}
		default: {
			throw new TclRuntimeError("unmatched typeId " + typeId);
		}
		}

		while (true) {

			// loop done condition

			switch (typeId) {
			case COLLECTION: {
				done = !iterator.hasNext();
				break;
			}
			case ARRAY_OBJECT:
			case ARRAY_STRING:
			case ARRAY_INT:
			case ARRAY_BOOLEAN:
			case ARRAY_LONG:
			case ARRAY_FLOAT:
			case ARRAY_DOUBLE:
			case ARRAY_BYTE:
			case ARRAY_SHORT:
			case ARRAY_CHAR: {
				done = !(i < arrayLength);
				break;
			}
			default: {
				throw new TclRuntimeError("unmatched typeId " + typeId);
			}
			}
			if (done) {
				break; // out of while loop
			}

			// More elements to process in collection or array,
			// get the next element, set the interp var, and
			// evaluate the loop body.

			TclObject wrapper;

			switch (typeId) {
			case COLLECTION:
			case ARRAY_OBJECT: {
				// Get Object from array or collection and make sure
				// it is assignable to type.

				if (typeId == COLLECTION) {
					elem = iterator.next();
				} else {
					elem = objArray[i];
				}
				elem_class = elem.getClass();

				// Check that the element Object can be cast
				// up to the type of the iteration var.
				// In Java code, a failed upcast would
				// generate a ClassCastException. Here we
				// check at runtime. This logic also
				// supports assigning a wrapper type
				// like Integer to an int iteration var.

				if (type.isPrimitive()
						&& (((type == Integer.TYPE) && (elem_class == Integer.class))
								|| ((type == Boolean.TYPE) && (elem_class == Boolean.class))
								|| ((type == Long.TYPE) && (elem_class == Long.class))
								|| ((type == Float.TYPE) && (elem_class == Float.class))
								|| ((type == Double.TYPE) && (elem_class == Double.class))
								|| ((type == Byte.TYPE) && (elem_class == Byte.class))
								|| ((type == Short.TYPE) && (elem_class == Short.class)) || ((type == Character.TYPE) && (elem_class == Character.class)))) {
					// No-op
				} else if (!JavaInvoke.isAssignable(type, elem_class)) {
					throw new TclException(
							interp,
							((typeId == COLLECTION) ? "collection" : "array")
									+ " element of type "
									+ JavaInfoCmd.getNameFromClass(elem_class)
									+ " could not be assigned to iteration variable of type "
									+ JavaInfoCmd.getNameFromClass(type));
				}

				if (type.isPrimitive() || type == String.class) {
					wrapper = JavaInvoke.convertJavaObject(interp, type, elem);
				} else {
					// Don't convert to Tcl primitive type if the iteration
					// var is an object type like Integer.
					wrapper = ReflectObject.newInstance(interp, type, elem);
				}
				break;
			}
			case ARRAY_STRING: {
				stringIndexValue = stringArray[i];
				wrapper = TclString.newInstance(stringIndexValue);
				break;
			}
			case ARRAY_INT: {
				intIndexValue = intArray[i];
				wrapper = TclInteger.newInstance(intIndexValue);
				break;
			}
			case ARRAY_BOOLEAN: {
				booleanIndexValue = booleanArray[i];
				wrapper = TclInteger.newInstance((booleanIndexValue ? 1 : 0));
				break;
			}
			case ARRAY_LONG: {
				longIndexValue = longArray[i];
				wrapper = TclInteger.newInstance(longIndexValue);
				break;
			}
			case ARRAY_FLOAT: {
				floatIndexValue = floatArray[i];
				wrapper = TclDouble.newInstance((double) floatIndexValue);
				break;
			}
			case ARRAY_DOUBLE: {
				doubleIndexValue = doubleArray[i];
				wrapper = TclDouble.newInstance(doubleIndexValue);
				break;
			}
			case ARRAY_BYTE: {
				byteIndexValue = byteArray[i];
				wrapper = TclInteger.newInstance((long) byteIndexValue);
				break;
			}
			case ARRAY_SHORT: {
				shortIndexValue = shortArray[i];
				wrapper = TclInteger.newInstance((long) shortIndexValue);
				break;
			}
			case ARRAY_CHAR: {
				charIndexValue = charArray[i];
				wrapper = TclString.newInstance(charIndexValue);
				break;
			}
			default: {
				throw new TclRuntimeError("unmatched typeId " + typeId);
			}
			}

			// Update array index

			i++;

			// Assign iteration variable

			interp.setVar(varname, wrapper, 0);

			// Eval the script argument, handle break and continue

			try {
				interp.eval(script, 0);
			} catch (TclException e) {
				int ccode = e.getCompletionCode();
				if (ccode == TCL.BREAK) {
					break; // Out of while loop
				} else if (ccode == TCL.CONTINUE) {
					continue; // To next while loop
				} else {
					throw e;
				}
			}

		} // end while (true) loop

		// Reset interp results after loop, this matches the
		// implementation of Tcl while and for loop.

		interp.resetResult();
		return;
	}

	static final boolean isArrayVarname(String varName) {
		final int lastInd = varName.length() - 1;
		if (varName.charAt(lastInd) == ')') {
			if (varName.indexOf('(') != -1) {
				return true;
			}
		}
		return false;
	}

} // end of JavaForCmd class

