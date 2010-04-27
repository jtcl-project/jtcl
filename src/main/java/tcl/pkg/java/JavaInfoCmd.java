/*
 * JavaInfoCmd.java
 *
 *	This file contains the Jacl implementation of the built-in java::info
 *	command.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 * RCS: @(#) $Id: JavaInfoCmd.java,v 1.7 2006/04/13 07:36:50 mdejong Exp $
 */

package tcl.pkg.java;

import java.beans.BeanInfo;
import java.beans.EventSetDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclIndex;
import tcl.lang.TclList;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;
import tcl.lang.TclString;
import tcl.pkg.java.reflect.PkgInvoker;

/**
 * This class implements the built-in "java::info" command in Tcl.
 */

public class JavaInfoCmd implements Command {

	static final private String validCmds[] = { "class", "baseclass",
			"dimensions", "events", "fields", "methods", "constructors",
			"properties", "superclass" };

	static final private int CLASS = 0;
	static final private int BASECLASS = 1;
	static final private int DIMENSIONS = 2;
	static final private int EVENTS = 3;
	static final private int FIELDS = 4;
	static final private int METHODS = 5;
	static final private int CONSTRUCTORS = 6;
	static final private int PROPERTIES = 7;
	static final private int SUPERCLASS = 8;

	static final private String propOpts[] = { "-type" };
	static final private String methOpts[] = { "-type", "-static" };

	static final int TYPE_OPT = 0;
	static final int STATIC_OPT = 1;

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * CmdProc --
	 * 
	 * This procedure is invoked to process the "java::info" command. See the
	 * user documentation for details on what it does.
	 * 
	 * Results: None.
	 * 
	 * Side effects: See the user documentation.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	public void cmdProc(Interp interp, // Current interpreter for info query.
			TclObject argv[]) // Argument list.
			throws TclException // Exceptions thrown as a result of bad
	// user input.
	{
		int lastArg = argv.length - 1;
		boolean statOpt = false;
		boolean typeOpt = false;
		TclObject resultListObj;
		Class c;

		if (argv.length < 2) {
			throw new TclNumArgsException(interp, 1, argv,
					"option ?arg arg ...?");
		}

		int opt = TclIndex.get(interp, argv[1], validCmds, "option", 0);
		switch (opt) {
		case BASECLASS:
			if (argv.length != 3) {
				throw new TclNumArgsException(interp, 2, argv, "objOrClass");
			}
			c = getClassFromObj(interp, argv[2]);
			if (c != null) {
				interp.setResult(getBaseNameFromClass(c));
			}
			return;
		case CLASS:
			if (argv.length != 3) {
				throw new TclNumArgsException(interp, 2, argv, "javaObj");
			}
			c = ReflectObject.getClass(interp, argv[2]);
			if (c != null) {
				interp.setResult(getNameFromClass(c));
			}
			return;
		case DIMENSIONS:
			if (argv.length != 3) {
				throw new TclNumArgsException(interp, 2, argv, "objOrClass");
			}
			c = getClassFromObj(interp, argv[2]);
			if (c == null) {
				interp.setResult(0);
			} else {
				interp.setResult(getNumDimsFromClass(c));
			}
			return;
		case EVENTS:
			if (argv.length != 3) {
				throw new TclNumArgsException(interp, 2, argv, "javaObj");
			}
			c = getClassFromObj(interp, argv[2]);
			if (c == null) {
				interp.resetResult();
				return;
			}
			if (!PkgInvoker.isAccessible(c)) {
				JavaInvoke.notAccessibleError(interp, c);
			}
			lookup: {
				BeanInfo beanInfo;

				try {
					beanInfo = Introspector.getBeanInfo(c);
				} catch (IntrospectionException e) {
					break lookup;
				}
				EventSetDescriptor events[] = beanInfo.getEventSetDescriptors();

				if (events == null) {
					break lookup;
				}

				TclObject list = TclList.newInstance();
				for (int i = 0; i < events.length; i++) {
					TclList.append(interp, list, TclString
							.newInstance(getNameFromClass(events[i]
									.getListenerType())));
				}
				interp.setResult(list);
				return;
			}

			// The objOrClass doesn't support BeanInfo or it has no events.

			interp.resetResult();
			return;
		case FIELDS:
			if ((lastArg < 2) || (lastArg > 4)) {
				throw new TclNumArgsException(interp, 2, argv,
						"?-type? ?-static? objOrClass");
			}
			for (int i = 2; i < lastArg; i++) {
				opt = TclIndex.get(interp, argv[i], methOpts, "option", 0);
				switch (opt) {
				case STATIC_OPT:
					statOpt = true;
					break;
				case TYPE_OPT:
					typeOpt = true;
					break;
				}
			}
			c = getClassFromObj(interp, argv[lastArg]);
			if (c != null) {
				if (!PkgInvoker.isAccessible(c)) {
					JavaInvoke.notAccessibleError(interp, c);
				}
				resultListObj = getFieldInfoList(interp, c, statOpt, typeOpt);
				interp.setResult(resultListObj);
			}
			return;
		case METHODS:
			if ((lastArg < 2) || (lastArg > 4)) {
				throw new TclNumArgsException(interp, 2, argv,
						"?-type? ?-static? objOrClass");
			}
			for (int i = 2; i < lastArg; i++) {
				opt = TclIndex.get(interp, argv[i], methOpts, "option", 0);
				switch (opt) {
				case STATIC_OPT:
					statOpt = true;
					break;
				case TYPE_OPT:
					typeOpt = true;
					break;
				}
			}
			c = getClassFromObj(interp, argv[lastArg]);
			if (c != null) {
				if (!PkgInvoker.isAccessible(c)) {
					JavaInvoke.notAccessibleError(interp, c);
				}
				resultListObj = getMethodInfoList(interp, c, statOpt, typeOpt);
				interp.setResult(resultListObj);
			}
			return;
		case CONSTRUCTORS:
			if (argv.length != 3) {
				throw new TclNumArgsException(interp, 2, argv, "objOrClass");
			}
			c = getClassFromObj(interp, argv[lastArg]);
			if (c != null) {
				if (!PkgInvoker.isAccessible(c)) {
					JavaInvoke.notAccessibleError(interp, c);
				}
				resultListObj = getConstructorInfoList(interp, c);
				interp.setResult(resultListObj);
			}
			return;
		case PROPERTIES:
			if ((lastArg < 2) || (lastArg > 3)) {
				throw new TclNumArgsException(interp, 2, argv,
						"?-type? objOrClass");
			}
			if (lastArg == 3) {
				opt = TclIndex.get(interp, argv[2], propOpts, "option", 0);

				// Since we just have one valid option, if the above call
				// returns without an exception, we've got "-type" (or
				// abreviations).

				typeOpt = true;
			}
			c = getClassFromObj(interp, argv[lastArg]);
			if (c != null) {
				if (!PkgInvoker.isAccessible(c)) {
					JavaInvoke.notAccessibleError(interp, c);
				}
				resultListObj = getPropInfoList(interp, c, typeOpt);
				interp.setResult(resultListObj);
			}
			return;
		case SUPERCLASS:
			if (argv.length != 3) {
				throw new TclNumArgsException(interp, 2, argv, "objOrClass");
			}
			c = getClassFromObj(interp, argv[2]);

			interp.resetResult();
			if (c != null) {
				c = c.getSuperclass();

				if (c != null) {
					interp.setResult(getNameFromClass(c));
				}

			}
			return;
		}
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * getClassFromObj --
	 * 
	 * Find the class associated with objOrClass.
	 * 
	 * Results: Returns a Class.
	 * 
	 * Side effects: Throws a Tcl exception if the objOrClass cannot be found.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	private static Class getClassFromObj(Interp interp, // Current interpreter
			// for info query.
			TclObject objOrClass) // Class or object for which the
			// associated class is returned.
			throws TclException // Exceptions thrown as a result of bad
	// user input.
	{
		Class c;
		try {
			c = ReflectObject.getClass(interp, objOrClass);
		} catch (TclException e) {
			try {
				c = ClassRep.get(interp, objOrClass);
			} catch (TclException e2) {
				throw new TclException(interp,
						"unknown java class or object \"" + objOrClass + "\"");
			}
		}
		return c;
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * gePropInfoList--
	 * 
	 * Find the list of properties.
	 * 
	 * Results: Returns a TclObject list of properties.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	private static TclObject getPropInfoList(Interp interp, // Current
			// interpreter for
			// info query.
			Class c, // The class for which we return the
			// properties.
			boolean typeOpt) // Include prop-type info in result.
			throws TclException // Exceptions thrown as a result of bad
	// user input.
	{
		BeanInfo beaninfo;
		try {
			beaninfo = Introspector.getBeanInfo(c);
		} catch (IntrospectionException e) {
			throw new TclException(interp, e.toString());
		}

		PropertyDescriptor propDesc[] = null;
		propDesc = beaninfo.getPropertyDescriptors();

		TclObject resultListObj = TclList.newInstance();
		TclObject elementObj, pairObj;

		for (int i = 0; i < propDesc.length; i++) {
			// If the -type option was specified, create a list containing
			// the field's type and name.

			pairObj = TclList.newInstance();

			if (typeOpt) {
				// The result of getPropertyType() may be "null" if this is an
				// indexed property that does not support non-indexed access.
				// For now, if the result is null, just don't add anything to
				// the
				// result. This is as yet UNTESTED because I couldn't produce a
				// case in which null was returned.

				elementObj = TclString.newInstance(getNameFromClass(propDesc[i]
						.getPropertyType()));
				if (elementObj != null) {
					TclList.append(interp, pairObj, elementObj);
				}
			}
			elementObj = TclString.newInstance(propDesc[i].getName());
			TclList.append(interp, pairObj, elementObj);

			TclList.append(interp, resultListObj, pairObj);
		}
		return resultListObj;
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * geFieldInfoList--
	 * 
	 * Find the list of fields.
	 * 
	 * Results: Returns a TclObject list of field signatures.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	private static TclObject getFieldInfoList(Interp interp, // Current
			// interpreter
			// for info
			// query.
			Class c, // The class for which we return the
			// fields.
			boolean statOpt, // Return only/no static field info.
			boolean typeOpt) // Include feild-type info in result.
			throws TclException // Exceptions thrown as a result of bad
	// user input.
	{
		// Get the array of fields associated with that class.

		Field[] fieldArray = FieldSig.getAccessibleFields(c);

		// Check whether each field is static. Based on -static option,
		// ignore the field or add it to the result list.

		TclObject resultListObj = TclList.newInstance();
		TclObject elementObj, sigObj, pairObj;
		Class declClass;

		for (int f = 0; f < fieldArray.length; ++f) {
			boolean isStatic = ((fieldArray[f].getModifiers() & Modifier.STATIC) > 0);
			if (isStatic == statOpt) {
				// If the declaring class is the same as c, and the same field
				// is also declared in c, then the signature is the name of the
				// field. Otherwise, the signature is a pair containing the
				// field
				// name and the declaring class name.

				sigObj = TclList.newInstance();

				String fieldName = fieldArray[f].getName();
				elementObj = TclString.newInstance(fieldName);
				TclList.append(interp, sigObj, elementObj);

				declClass = fieldArray[f].getDeclaringClass();
				if (!declClass.equals(c)) {
					for (int i = 0; i < fieldArray.length; ++i) {
						if (i == f) {
							continue;
						}
						if (!fieldName.equals(fieldArray[i].getName())) {
							continue;
						}
						Class tmpClass = fieldArray[i].getDeclaringClass();
						if (declClass.isAssignableFrom(tmpClass)) {
							elementObj = TclString
									.newInstance(getNameFromClass(declClass));
							TclList.append(interp, sigObj, elementObj);
							break;
						}
					}
				}
				if (typeOpt) {
					// If -type was used, create a pair with the property type
					// and
					// signature. Append the pair to the result list.

					pairObj = TclList.newInstance();

					elementObj = TclString
							.newInstance(getNameFromClass(fieldArray[f]
									.getType()));
					TclList.append(interp, pairObj, elementObj);
					TclList.append(interp, pairObj, sigObj);
					TclList.append(interp, resultListObj, pairObj);
				} else {
					// Append the signature object to the result list.

					TclList.append(interp, resultListObj, sigObj);
				}
			}
		}
		return resultListObj;
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * getMethodInfoList--
	 * 
	 * Find the list of static or instance methods.
	 * 
	 * Results: Returns a TclObject list of method signatures.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	private static TclObject getMethodInfoList(Interp interp, // Current
			// interpreter
			// for info
			// query.
			Class c, // The class for which we return the
			// methods.
			boolean statOpt, // Return only/no static method info.
			boolean typeOpt) // Include return-type info in result.
			throws TclException // Exceptions thrown as a result of bad
	// user input.
	{
		// Get the array of accessible static methods associated with the class,
		// otherwise get all the accessible non-static methods in the class,
		// its superclasses, and interfaces.

		Method[] methodArray;

		if (statOpt) {
			methodArray = FuncSig.getAccessibleStaticMethods(c);
		} else {
			methodArray = FuncSig.getAccessibleInstanceMethods(c);
		}

		TclObject resultListObj = TclList.newInstance();
		TclObject elementObj, sigObj;

		for (int m = 0; m < methodArray.length; ++m) {
			if (true) { // FIXME: left in to keep diff simple
				// Create the signature.

				sigObj = TclList.newInstance();

				elementObj = TclString.newInstance(methodArray[m].getName());
				TclList.append(interp, sigObj, elementObj);

				Class[] paramArray = methodArray[m].getParameterTypes();
				for (int p = 0; p < paramArray.length; ++p) {
					elementObj = TclString
							.newInstance(getNameFromClass(paramArray[p]));
					TclList.append(interp, sigObj, elementObj);
				}

				if (typeOpt) {
					// If -type was used, create a sublist with the
					// method type, signature and exception types.
					// Append the sublist the result list.

					TclObject sublist = TclList.newInstance();
					TclObject exceptions = TclList.newInstance();

					Class ex[] = methodArray[m].getExceptionTypes();
					for (int i = 0; i < ex.length; i++) {
						TclList.append(interp, exceptions, TclString
								.newInstance(getNameFromClass(ex[i])));
					}

					TclList.append(interp, sublist, TclString
							.newInstance(getNameFromClass(methodArray[m]
									.getReturnType())));
					TclList.append(interp, sublist, sigObj);
					TclList.append(interp, sublist, exceptions);

					TclList.append(interp, resultListObj, sublist);
				} else {
					// Append the signature object to the result list.

					TclList.append(interp, resultListObj, sigObj);
				}
			}
		}
		return resultListObj;
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * geConstructorInfoList--
	 * 
	 * Find the list of constructors' signatures.
	 * 
	 * Results: Returns a TclObject list of constructor names.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	private static TclObject getConstructorInfoList(Interp interp, // Current
			// interpreter
			// for info
			// query.
			Class c) // The class for which we return the
			// constructors.
			throws TclException // Exceptions thrown as a result of
	// bad user input.
	{
		// Get the array of constructors associated with that class.

		Constructor[] constructorArray = FuncSig.getAccessibleConstructors(c);

		TclObject resultListObj = TclList.newInstance();
		TclObject elementObj, sigObj;

		for (int m = 0; m < constructorArray.length; ++m) {
			// Create signature and append it to the result list.

			sigObj = TclList.newInstance();

			elementObj = TclString.newInstance(constructorArray[m].getName());
			TclList.append(interp, sigObj, elementObj);

			Class[] paramArray = constructorArray[m].getParameterTypes();
			for (int p = 0; p < paramArray.length; ++p) {
				elementObj = TclString
						.newInstance(getNameFromClass(paramArray[p]));
				TclList.append(interp, sigObj, elementObj);
			}
			TclList.append(interp, resultListObj, sigObj);
		}
		return resultListObj;
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * getNumDimsFromClass --
	 * 
	 * Return the number of dimension (# of nested arrays) for a type
	 * 
	 * Results: Returns a non-negative integer.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static int getNumDimsFromClass(Class type) // The class for which we return
	// the name.
	{
		int dim;
		for (dim = 0; type.isArray(); dim++) {
			type = type.getComponentType();
		}
		return dim;
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * getNameFromClass --
	 * 
	 * Return the name of the class associated with "type". If "type" is an
	 * array, for each dimension, append "[]" to the name of he base class.
	 * 
	 * Results: Returns a class name.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static String getNameFromClass(Class type) // The class for which we return
	// the name.
	{
		StringBuffer name = new StringBuffer();

		while (type.isArray()) {
			name.append("[]");
			type = type.getComponentType();
		}
		String className = type.getName().replace('$', '.'); // For inner
		// classes
		name.insert(0, className);
		return name.toString();
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * getBaseNameFromClass --
	 * 
	 * Return the name of the base class associated with "type".
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

	private static String getBaseNameFromClass(Class type) // The class for
	// which we return
	// the name.
	{
		while (type.isArray()) {
			type = type.getComponentType();
		}
		return type.getName().toString().replace('$', '.'); // For inner classes
	}

} // end JavaInfoCmd

