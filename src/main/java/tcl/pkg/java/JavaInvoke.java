/*
 * JavaInvoke.java --
 *
 *	This class implements the common routines used by the java::*
 *	commands to access the Java Reflection API.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 * RCS: @(#) $Id: JavaInvoke.java,v 1.26 2009/07/10 14:22:00 rszulgo Exp $
 *
 */

package tcl.pkg.java;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import tcl.lang.Interp;
import tcl.lang.PackageNameException;
import tcl.lang.TclBoolean;
import tcl.lang.TclClassLoader;
import tcl.lang.TclDouble;
import tcl.lang.TclException;
import tcl.lang.TclInteger;
import tcl.lang.TclObject;
import tcl.lang.TclRuntimeError;
import tcl.lang.TclString;
import tcl.pkg.java.reflect.PkgInvoker;

/**
 * This class implements the common routines used by the java::* commands to
 * create Java objects, call Java methods and access fields and properties. It
 * also has auxiliary routines for converting between TclObject's and Java
 * Object's.
 */

public class JavaInvoke {

	// We need to use empty array Object[0] a lot. We keep a static copy
	// and re-use it to avoid garbage collection.

	static private Object EMPTY_ARGS[] = new Object[0];

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * newInstance --
	 * 
	 * Call the specified constructor.
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

	static TclObject newInstance(Interp interp, // Current interpreter.
			TclObject signature, // Constructor signature.
			TclObject[] argv, // Arguments.
			int startIdx, // Index of the first argument in argv to
			// pass to the constructor.
			int count) // Number of arguments to pass to the
			// constructor.
			throws TclException // Standard Tcl exception.
	{
		// Some built-in types have wrapper classes that behave in
		// unexpected ways. For example, the Boolean constructor
		// is overloaded to accept either a boolean primitive or
		// a String value. The FuncSig module prefers method signatures
		// that accept a String, but the version that accepts a String
		// does not match Tcl's number parsing semantics. Fix this problem
		// by explicitly invoking the wrapper constructor that accepts
		// a primitive type so that the type conversion logic in this
		// module is used to pass a Tcl value to a Java primitive argument.

		if (count == 1) {
			final String sig = signature.toString();
			if (sig.equals("Boolean") || sig.equals("java.lang.Boolean")) {
				signature = TclString.newInstance("java.lang.Boolean boolean");
			} else if (sig.equals("Integer") || sig.equals("java.lang.Integer")) {
				signature = TclString.newInstance("java.lang.Integer int");
			} else if (sig.equals("Byte") || sig.equals("java.lang.Byte")) {
				signature = TclString.newInstance("java.lang.Byte byte");
			} else if (sig.equals("Short") || sig.equals("java.lang.Short")) {
				signature = TclString.newInstance("java.lang.Short short");
			} else if (sig.equals("Character")
					|| sig.equals("java.lang.Character")) {
				signature = TclString.newInstance("java.lang.Character char");
			} else if (sig.equals("Long") || sig.equals("java.lang.Long")) {
				signature = TclString.newInstance("java.lang.Long long");
			} else if (sig.equals("Float") || sig.equals("java.lang.Float")) {
				signature = TclString.newInstance("java.lang.Float float");
			} else if (sig.equals("Double") || sig.equals("java.lang.Double")) {
				signature = TclString.newInstance("java.lang.Double double");
			}
		}

		FuncSig sig = FuncSig.get(interp, null, signature, argv, startIdx,
				count, false);

		Object javaObj = call(interp, sig.pkgInvoker, signature, sig.func,
				null, argv, startIdx, count);

		return ReflectObject.newInstance(interp, sig.targetCls, javaObj);
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * callMethod --
	 * 
	 * Call the specified instance or static method of the given object.
	 * 
	 * Results: When successful, this method returns the Java object that the
	 * Java method would have returned. If the Java method has a void return
	 * type then null is returned.
	 * 
	 * Side effects: The method can cause arbitrary side effects.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static TclObject callMethod(Interp interp, // Current interpreter.
			TclObject reflectObj, // The object whose method to invoke.
			TclObject signature, // Method signature.
			TclObject argv[], // Arguments.
			int startIdx, // Index of the first argument in argv[] to
			// pass to the method.
			int count, // Number of arguments to pass to the
			// method.
			boolean convert) // Whether the value should be converted
			// into Tcl objects of the closest types.
			throws TclException {
		Object javaObj = ReflectObject.get(interp, reflectObj);
		Class javaCl = ReflectObject.getClass(interp, reflectObj);
		FuncSig sig = FuncSig.get(interp, javaCl, signature, argv, startIdx,
				count, false);
		Method method = (Method) sig.func;
		Class rtype = method.getReturnType();

		if (!PkgInvoker.isAccessible(rtype)) {
			throw new TclException(interp, "Return type \""
					+ JavaInfoCmd.getNameFromClass(rtype)
					+ "\" is not accessible");
		}

		Object result = call(interp, sig.pkgInvoker, signature, method,
				javaObj, argv, startIdx, count);

		if (rtype == Void.TYPE) {
			return null;
		} else {
			return wrap(interp, rtype, result, convert);
		}
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * callStaticMethod --
	 * 
	 * Call the specified static method of the given object.
	 * 
	 * Results: When successful, this method returns the Java object that the
	 * Java method would have returned. If the Java method has a void return
	 * type then null is returned.
	 * 
	 * Side effects: The method can cause arbitrary side effects.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static TclObject callStaticMethod(Interp interp, // Current interpreter.
			TclObject classObj, // Class whose static method to invoke.
			TclObject signature, // Method signature.
			TclObject argv[], // Arguments.
			int startIdx, // Index of the first argument in argv[] to
			// pass to the method.
			int count, // Number of arguments to pass to the
			// method.
			boolean convert) // Whether the value should be converted
			// into Tcl objects of the closest types.
			throws TclException {
		Class cls = ClassRep.get(interp, classObj);
		FuncSig sig = FuncSig.get(interp, cls, signature, argv, startIdx,
				count, true);

		Method method = (Method) sig.func;
		Class rtype = method.getReturnType();

		if (!PkgInvoker.isAccessible(rtype)) {
			throw new TclException(interp, "Return type \""
					+ JavaInfoCmd.getNameFromClass(rtype)
					+ "\" is not accessible");
		}

		Object result = call(interp, sig.pkgInvoker, signature, method, null,
				argv, startIdx, count);

		if (rtype == Void.TYPE) {
			return null;
		} else {
			return wrap(interp, method.getReturnType(), result, convert);
		}
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * call --
	 * 
	 * Call the constructor, instance method, or static method with the given
	 * parameters. Check the parameter types and perform TclObject to JavaObject
	 * conversion.
	 * 
	 * Results: The object created by the constructor, or the return value of
	 * the method call.
	 * 
	 * Side effects: The constructor/method call may have arbitrary side
	 * effects.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static Object call(Interp interp, PkgInvoker invoker, // The PkgInvoked used
			// to invoke the
			// method or constructor.
			TclObject signature, // For formatting error message.
			Object func, // The Constructor or Method to call.
			Object obj, // The object associated with an instace
			// method call. Should be null for
			// constructor calls and static method
			// calls.
			TclObject argv[], // Argument list.
			int startIdx, // Index of the first argument in argv[] to
			// pass to the method or constructor.
			int count) // Number of arguments to pass to the
			// method or constructor.
			throws TclException // Standard Tcl exception.
	{
		Class paramTypes[];
		Constructor cons = null;
		Method method = null;
		int i;
		boolean isConstructor = (func instanceof Constructor);

		if (isConstructor) {
			cons = (Constructor) func;
			paramTypes = cons.getParameterTypes();
		} else {
			method = (Method) func;
			paramTypes = method.getParameterTypes();
		}

		if (count != paramTypes.length) {
			throw new TclException(interp, "wrong # args for calling "
					+ (isConstructor ? "constructor" : "method") + " \""
					+ signature + "\"");
		}

		Object args[];

		if (count == 0) {
			args = EMPTY_ARGS;
		} else {
			args = new Object[count];
			for (i = 0; i < count; i++) {
				args[i] = convertTclObject(interp, paramTypes[i], argv[i
						+ startIdx]);
			}
		}

		try {
			final boolean debug = false;
			Object result;

			if (isConstructor) {
				result = invoker.invokeConstructor(cons, args);
			} else {
				result = invoker.invokeMethod(method, obj, args);
			}

			if (debug) {
				System.out.println("result object from invocation is \""
						+ result + "\"");
			}

			return result;
		} catch (InstantiationException e) {
			throw new TclRuntimeError("unexpected abstract class: "
					+ e.getMessage());
		} catch (IllegalAccessException e) {
			throw new TclRuntimeError(
					"unexpected inaccessible ctor or method: " + e.getMessage());
		} catch (IllegalArgumentException e) {
			throw new TclRuntimeError("unexpected IllegalArgumentException: "
					+ e.getMessage());
		} catch (InvocationTargetException e) {
			Throwable te = e.getTargetException();
			if (te instanceof TclException) {
				interp.setResult(te.getMessage());
				throw (TclException) te;
			} else {
				throw new ReflectException(interp, te);
			}
		}
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * getField --
	 * 
	 * Returns the value of a field in the given object.
	 * 
	 * Results: When successful, returns an array: Object result[2]. result[0]
	 * is the value of the field; result[1] is the type of the field.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static final TclObject getField(Interp interp, // Current interpreter.
			TclObject classOrObj, // Class or object whose field to get.
			TclObject signature, // Signature of the field.
			boolean convert) // Whether the value should be converted
			// into Tcl objects of the closest types.
			throws TclException // Standard Tcl exception.
	{
		return getsetField(interp, classOrObj, signature, null, convert, true);
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * setField --
	 * 
	 * Sets the value of a field in the given object.
	 * 
	 * Results: None.
	 * 
	 * Side effects: When successful, the field is set to the given value.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static final void setField(Interp interp, // Current interpreter.
			TclObject classOrObj, // Class or object whose field to get.
			TclObject signature, // Signature of the field.
			TclObject value) // New value for the field.
			throws TclException // Standard Tcl exception.
	{
		getsetField(interp, classOrObj, signature, value, false, false);
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * getsetField --
	 * 
	 * Gets or sets the field in the given object.
	 * 
	 * Results: None.
	 * 
	 * Side effects: When successful, the field is set to the given value if
	 * isget is false.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static TclObject getsetField(Interp interp, // Current interpreter.
			TclObject classOrObj, // Class or object whose field to get.
			TclObject signature, // Signature of the field.
			TclObject value, // New value for the field.
			boolean convert, // Whether the value should be converted
			// into Tcl objects of the closest types.
			boolean isget) throws TclException // Standard Tcl exception.
	{
		Class cls = null;
		Object obj = null;
		boolean isStatic = false;

		try {
			obj = ReflectObject.get(interp, classOrObj);
		} catch (TclException e) {
			try {
				cls = ClassRep.get(interp, classOrObj);
			} catch (TclException e1) {
				throw new TclException(interp, "unknown class or object \""
						+ classOrObj + "\"");
			}
			isStatic = true;

			if (!PkgInvoker.isAccessible(cls)) {
				JavaInvoke.notAccessibleError(interp, cls);
			}
		}

		if (!isStatic) {
			if (obj == null) {
				throw new TclException(interp,
						"can't access fields in a null object reference");
			}
			cls = ReflectObject.getClass(interp, classOrObj);
		}

		// Check for the special case where the field is named "class"
		// which has a special meaning and is enforced by the javac compiler.
		// If found, return the java.lang.Class object for the named class.

		if (isStatic && isget && signature.toString().equals("class")) {
			return wrap(interp, Class.class, cls, false);
		}

		FieldSig sig = FieldSig.get(interp, signature, cls);
		Field field = sig.field;
		if (isStatic && (!(Modifier.isStatic(field.getModifiers())))) {
			throw new TclException(interp,
					"can't access an instance field without an object");
		}
		Class ftype = field.getType();

		if (!PkgInvoker.isAccessible(field.getType())) {
			throw new TclException(interp, "Field type \""
					+ JavaInfoCmd.getNameFromClass(ftype)
					+ "\" is not accessible");
		}

		if (!isget && Modifier.isFinal(field.getModifiers())) {
			throw new TclException(interp, "can't set final field \""
					+ signature + "\"");
		}

		try {
			if (isget) {
				return wrap(interp, ftype, sig.pkgInvoker.getField(field, obj),
						convert);
			} else {
				Object javaValue = convertTclObject(interp, ftype, value);
				sig.pkgInvoker.setField(field, obj, javaValue);
				return null;
			}
		} catch (IllegalArgumentException e) {
			throw new TclRuntimeError("unexpected IllegalArgumentException: "
					+ e.getMessage());
		} catch (IllegalAccessException e) {
			throw new TclRuntimeError("unexpected IllegalAccessException: "
					+ e.getMessage());
		}
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * getProperty --
	 * 
	 * Returns the value of a property in the given object.
	 * 
	 * Results: When successful, returns a the value of the property inside a
	 * TclObject
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static TclObject getProperty(Interp interp, // Current interpreter.
			TclObject reflectObj, // The object whose property to query.
			TclObject propName, // The name of the property to query.
			boolean convert) // Whether the value should be converted
			// into Tcl objects of the closest types.
			throws TclException // A standard Tcl exception.
	{
		Object javaObj = ReflectObject.get(interp, reflectObj);
		if (javaObj == null) {
			throw new TclException(interp,
					"can't get property from null object");
		}

		Class javaClass = ReflectObject.getClass(interp, reflectObj);
		PropertySig sig = PropertySig.get(interp, javaClass, propName);

		Method readMethod = sig.desc.getReadMethod();

		if (readMethod == null) {
			throw new TclException(interp, "can't get write-only property \""
					+ propName + "\"");
		}

		try {
			return wrap(interp, readMethod.getReturnType(), sig.pkgInvoker
					.invokeMethod(readMethod, javaObj, EMPTY_ARGS), convert);
		} catch (IllegalAccessException e) {
			throw new TclRuntimeError("unexpected inaccessible readMethod: "
					+ e.getMessage());
		} catch (IllegalArgumentException e) {
			throw new TclRuntimeError("unexpected IllegalArgumentException: "
					+ e.getMessage());
		} catch (InvocationTargetException e) {
			throw new ReflectException(interp, e);
		}

	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * setProperty --
	 * 
	 * Returns the value of a property in the given object.
	 * 
	 * Results: None.
	 * 
	 * Side effects: When successful, the property will have the new value.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static void setProperty(Interp interp, // Current interpreter.
			TclObject reflectObj, // The object whose property to query.
			TclObject propName, // The name of the property to query.
			TclObject value) // Whether the value should be converted
			// into Tcl objects of the closest types.
			throws TclException // A standard Tcl exception.
	{
		Object javaObj = ReflectObject.get(interp, reflectObj);
		if (javaObj == null) {
			throw new TclException(interp, "can't set property in null object");
		}

		Class javaClass = ReflectObject.getClass(interp, reflectObj);
		PropertySig sig = PropertySig.get(interp, javaClass, propName);

		Method writeMethod = sig.desc.getWriteMethod();
		Class type = sig.desc.getPropertyType();

		if (writeMethod == null) {
			throw new TclException(interp, "can't set read-only property \""
					+ propName + "\"");
		}

		Object args[] = new Object[1];
		args[0] = convertTclObject(interp, type, value);

		try {
			sig.pkgInvoker.invokeMethod(writeMethod, javaObj, args);
		} catch (IllegalAccessException e) {
			throw new TclRuntimeError("unexpected inaccessible writeMethod: "
					+ e.getMessage());
		} catch (IllegalArgumentException e) {
			throw new TclRuntimeError("unexpected IllegalArgumentException: "
					+ e.getMessage());
		} catch (InvocationTargetException e) {
			throw new ReflectException(interp, e);
		}
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * getClassByName --
	 * 
	 * Returns Class object identified by the string name. We allow abbreviation
	 * of the java.lang.* class if there is no ambiguity: e.g., if there is no
	 * class whose fully qualified name is "String", then "String" means
	 * java.lang.String. Inner classes are supported both with fully qualified
	 * names and imported class names.
	 * 
	 * Results: If successful, The Class object identified by the string name.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	public static Class getClassByName(Interp interp, // Interp used by
			// TclClassLoader
			String clsName) // String name of the class.
			throws TclException // If the class cannot be found or loaded.

	{
		Class result = null;
		int dimension;

		final boolean debug = false;
		if (debug) {
			System.out
					.println("JavaInvoke.getClassByName(\"" + clsName + "\")");
		}

		// If the string is of the form className[][]..., strip out the trailing
		// []s and record the dimension of the array.

		StringBuffer prefix_buf = new StringBuffer(64);
		StringBuffer suffix_buf = new StringBuffer(64);
		StringBuffer clsName_buf = new StringBuffer(clsName);

		String lname;

		int clsName_len;
		for (dimension = 0; true; dimension++) {
			clsName_len = clsName_buf.length();

			if ((clsName_len > 2)
					&& (clsName_buf.charAt(clsName_len - 2) == '[')
					&& (clsName_buf.charAt(clsName_len - 1) == ']')) {

				clsName_buf.setLength(clsName_len - 2);
				prefix_buf.append('[');
			} else {
				break;
			}
		}

		boolean package_name_exception = false;

		if (true) {
			clsName = clsName_buf.toString(); // Use shortened form of name

			// Search for the char '.' in the name. If '.' is in
			// the name then we know it is not a builtin type.

			if (clsName.indexOf('.') == -1) {
				if (dimension > 0) {
					boolean isPrimitive = true;

					if (clsName.equals("int")) {
						prefix_buf.append('I');
					} else if (clsName.equals("boolean")) {
						prefix_buf.append('Z');
					} else if (clsName.equals("long")) {
						prefix_buf.append('J');
					} else if (clsName.equals("float")) {
						prefix_buf.append('F');
					} else if (clsName.equals("double")) {
						prefix_buf.append('D');
					} else if (clsName.equals("byte")) {
						prefix_buf.append('B');
					} else if (clsName.equals("short")) {
						prefix_buf.append('S');
					} else if (clsName.equals("char")) {
						prefix_buf.append('C');
					} else {
						isPrimitive = false;
					}

					if (isPrimitive) {
						try {
							return Class.forName(prefix_buf.toString());
						} catch (ClassNotFoundException e) {
							throw new TclRuntimeError(
									"unexpected ClassNotFoundException: "
											+ e.getMessage());
						}
					}

					// Otherwise, not a primitive array type

					prefix_buf.append('L');
					suffix_buf.append(';');
				} else {
					if (clsName.equals("int")) {
						return Integer.TYPE;
					} else if (clsName.equals("boolean")) {
						return Boolean.TYPE;
					} else if (clsName.equals("long")) {
						return Long.TYPE;
					} else if (clsName.equals("float")) {
						return Float.TYPE;
					} else if (clsName.equals("double")) {
						return Double.TYPE;
					} else if (clsName.equals("byte")) {
						return Byte.TYPE;
					} else if (clsName.equals("short")) {
						return Short.TYPE;
					} else if (clsName.equals("char")) {
						return Character.TYPE;
					}
				}

				// Use TclClassLoader defined on a per-interp basis.
				TclClassLoader tclClassLoader = (TclClassLoader) interp
						.getClassLoader();

				try {
					lname = prefix_buf + clsName + suffix_buf;

					if (debug) {
						System.out.println("attempting load of \"" + lname
								+ "\"");
					}

					result = tclClassLoader.loadClass(lname);
				} catch (ClassNotFoundException e) {
					result = null;
				} catch (PackageNameException e) {
					// Should not be possible to catch a PackageNameException
					// here since the class name above should contain no '.'
					// chars.
					throw new TclRuntimeError(
							"unexpected PackageNameException :"
									+ e.getMessage());
				}

				if (result == null) {
					// If the class loader can not find the class then check
					// with
					// the "import" feature to see if the given clsName maps to
					// a fully qualified class name.

					boolean inJavaLang = false;
					String fullyqualified = JavaImportCmd.getImport(interp,
							clsName);

					// If we do not find a fully qualified name in the import
					// table
					// then try to fully qualify the class with the java.lang
					// prefix

					if (fullyqualified == null) {
						inJavaLang = true;
						fullyqualified = "java.lang." + clsName;
					}

					// If the class starts with "java." and it can't be
					// loaded with the system class loader, then a
					// PackageNameException is raised.

					try {
						lname = prefix_buf + fullyqualified + suffix_buf;

						if (debug) {
							System.out.println("attempting load of \"" + lname
									+ "\"");
						}

						result = tclClassLoader.loadClass(lname);
					} catch (ClassNotFoundException e) {
						result = null;
					} catch (PackageNameException e) {
						// If loading a class from java.lang package fails
						// and we fully qualified the class name with the
						// java.lang prefix, then don't emit a special
						// error message related to the package name.

						if (inJavaLang) {
							// No-op
						} else {
							package_name_exception = true;
						}
						result = null;
					}

					if (debug) {
						if (result == null) {
							System.out.println("load failed");
						} else {
							System.out.println("load worked");
						}
					}
				}
			} else {
				// clsName contains a '.' character. It is either a fully
				// qualified toplevel class name or an inner class name.
				// Note that use of a '$' to indicate an inner class is
				// supported only for backwards compatibility and
				// works only with a fully qualified class name.

				TclClassLoader tclClassLoader = (TclClassLoader) interp
						.getClassLoader();

				if (dimension > 0) {
					prefix_buf.append("L");
					suffix_buf.append(";");
				}

				try {
					lname = prefix_buf + clsName + suffix_buf;

					if (debug) {
						System.out.println("attempting load of \"" + lname
								+ "\"");
					}

					result = tclClassLoader.loadClass(lname);
				} catch (ClassNotFoundException e) {
					result = null;
				} catch (PackageNameException e) {
					package_name_exception = true;
					result = null;
				}

				if (debug) {
					if (result == null) {
						System.out.println("load failed");
					} else {
						System.out.println("load worked");
					}
				}

				if ((result == null) && (clsName.indexOf('$') == -1)) {
					// Toplevel class with fully qualified name not found.
					// Search for an inner class with this name. This
					// search is tricky because inner classes can be
					// nested inside other inner classes. Find a containing
					// class that exists, then search for an inner class
					// relative to the containing class. Old style inner class
					// names that contain a literal '$' character are not
					// searched.

					ArrayList parts = new ArrayList(5);
					int si = 0;
					int clsNameLength = clsName.length();
					for (int i = 0; i <= clsNameLength; i++) {
						if ((i == clsNameLength) || (clsName.charAt(i) == '.')) {
							parts.add(clsName.substring(si, i));
							si = i + 1;
						}
					}
					if (debug) {
						System.out.println("clsName parts is " + parts);
					}

					// Search for a contanining class, construct inner
					// class name if a contanining class was found.

					String toplevel = null;
					String inner = null;
					boolean load_inner = false;

					for (int i = parts.size() - 1; i > 0; i--) {
						StringBuffer sb;

						sb = new StringBuffer(64);
						for (int bi = 0; bi < i; bi++) {
							sb.append(parts.get(bi));
							sb.append('.');
						}
						if ((sb.length() > 0)
								&& (sb.charAt(sb.length() - 1) == '.')) {
							sb.setLength(sb.length() - 1);
						}
						toplevel = sb.toString();

						sb = new StringBuffer(64);
						for (int ai = i; ai < parts.size(); ai++) {
							sb.append(parts.get(ai));
							sb.append('$');
						}
						if ((sb.length() > 0)
								&& (sb.charAt(sb.length() - 1) == '$')) {
							sb.setLength(sb.length() - 1);
						}
						inner = sb.toString();

						if (debug) {
							System.out.println("loop " + i + ":");
							System.out.println("toplevel is " + toplevel);
							System.out.println("inner is " + inner);
						}

						try {
							lname = prefix_buf + toplevel + suffix_buf;

							if (debug) {
								System.out.println("attempting load of \""
										+ lname + "\"");
							}

							result = tclClassLoader.loadClass(lname);
						} catch (ClassNotFoundException e) {
							// Not an enclosing toplevel class, raise
							// TclException
							result = null;
						} catch (PackageNameException e) {
							package_name_exception = true;
							result = null;
						}

						if (debug) {
							if (result == null) {
								System.out.println("load failed");
							} else {
								System.out.println("load worked");
							}
						}

						if (result != null) {
							// Containing class was loaded, break out of
							// this loop and load the inner class by name.

							load_inner = true;
							break;
						} else if ((toplevel.indexOf('.') == -1)) {
							// The toplevel class was not loaded, it could
							// be an imported class name. Check the import
							// table for this class name. Don't bother
							// loading an imported name since the class
							// had to exist to be imported in the first place.

							if (debug) {
								System.out
										.println("checking import table for \""
												+ toplevel + "\"");
							}
							String fullyqualified = JavaImportCmd.getImport(
									interp, toplevel);
							if (debug) {
								if (fullyqualified == null) {
									System.out.println("was not imported");
								} else {
									System.out.println("was imported as \""
											+ fullyqualified + "\"");
								}
							}

							if (fullyqualified != null) {
								load_inner = true;
								toplevel = fullyqualified;
								break;
							} else {
								// Not an imported toplevel class. Check to
								// see if the class is in the java.lang package.

								fullyqualified = "java.lang." + toplevel;

								try {
									lname = prefix_buf + fullyqualified
											+ suffix_buf;

									if (debug) {
										System.out
												.println("attempting load of \""
														+ lname + "\"");
									}

									result = tclClassLoader.loadClass(lname);
								} catch (ClassNotFoundException e) {
									result = null;
								} catch (PackageNameException e) {
									result = null;
								}

								if (debug) {
									if (result == null) {
										System.out.println("load failed");
									} else {
										System.out.println("load worked");
									}
								}

								if (result != null) {
									load_inner = true;
									toplevel = fullyqualified;
									break;
								}
							}
						}
					}

					if (load_inner) {
						// If enclosing class exists, attempt to load inner
						// class.

						try {
							lname = prefix_buf + toplevel + "$" + inner
									+ suffix_buf;

							if (debug) {
								System.out.println("attempting load of \""
										+ lname + "\"");
							}

							result = tclClassLoader.loadClass(lname);
						} catch (ClassNotFoundException e) {
							// Not an inner class, raise TclException
							result = null;
						} catch (PackageNameException e) {
							package_name_exception = true;
							result = null;
						}

						if (debug) {
							if (result == null) {
								System.out.println("load failed");
							} else {
								System.out.println("load worked");
							}
						}
					} // end if (load_inner)
				}
			}
		} // end if (true) block

		if ((result == null) && package_name_exception) {
			if (debug) {
				System.out
						.println("throwing TclException because of PackageNameException");
			}

			throw new TclException(interp,
					"cannot load new class into java or tcl package");
		}

		if (result == null) {
			if (debug) {
				System.out.println("throwing unknown class TclException");
			}

			throw new TclException(interp, "unknown class \"" + clsName_buf
					+ "\"");
		}

		return result;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * convertJavaObject --
	 * 
	 * Converts the java.lang.Object into a Tcl object and return TclObject that
	 * holds the reult. Primitive data types are converted into primitive Tcl
	 * data types. Otherwise, a ReflectObject wrapper is created for the object
	 * so that it can be later accessed with the Reflection API.
	 * 
	 * Results: The TclObject representation of the Java object.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static TclObject convertJavaObject(Interp interp, // Current interpreter.
			Class cls, // The class of the Java Object
			Object javaObj) // The java.lang.Object to convert to a TclObject.
			throws TclException {
		if (javaObj == null) {
			if (cls == String.class) {
				return TclString.newInstance("");
			} else {
				return ReflectObject.newInstance(interp, cls, javaObj);
			}

		} else if ((cls == Integer.TYPE) || (cls == Integer.class)) {
			return TclInteger.newInstance(((Integer) javaObj).intValue());

		} else if ((cls == Long.TYPE) || (cls == Long.class)) {
			// A long can not be represented as a TclInteger
			return TclString.newInstance(javaObj.toString());

		} else if ((cls == Short.TYPE) || (cls == Short.class)) {
			return TclInteger.newInstance(((Short) javaObj).intValue());

		} else if ((cls == Byte.TYPE) || (cls == Byte.class)) {
			return TclInteger.newInstance(((Byte) javaObj).intValue());

		} else if ((cls == Double.TYPE) || (cls == Double.class)) {
			return TclDouble.newInstance(((Double) javaObj).doubleValue());

		} else if ((cls == Float.TYPE) || (cls == Float.class)) {
			return TclDouble.newInstance(((Float) javaObj).doubleValue());

		} else if ((cls == Boolean.TYPE) || (cls == Boolean.class)) {
			return TclBoolean.newInstance(((Boolean) javaObj).booleanValue());

		} else if ((cls == Character.TYPE) || (cls == Character.class)) {
			return TclString.newInstance(((Character) javaObj).toString());

		} else if (cls == String.class) {
			return TclString.newInstance((String) javaObj);

		} else {
			return ReflectObject.newInstance(interp, cls, javaObj);
		}
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * convertTclObject --
	 * 
	 * Converts a Tcl object to a Java Object of the required type.
	 * 
	 * Results: An Object of the required type.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static final Object convertTclObject(Interp interp, // Current interpreter.
			Class type, // Convert to this type.
			TclObject tclObj) // From this Tcl object.
			throws TclException // If conversion fails.
	{
		Object javaObj = null;
		Class javaClass = null;
		boolean isReflectObj = false;

		try {
			javaObj = ReflectObject.get(interp, tclObj);
			javaClass = ReflectObject.getClass(interp, tclObj);
			isReflectObj = true;
		} catch (TclException e) {
			interp.resetResult();
		}

		if (!isReflectObj) {
			// tclObj a Tcl "primitive" value. We try convert it to the
			// corresponding primitive value in Java.
			//
			// To optimize performance, the following "if" statements are
			// arranged according to (my guesstimation of) the frequency
			// that a certain type is used.

			if (type == String.class) {
				return tclObj.toString();

			} else if (type == Object.class) {
				return tclObj.toString();

			} else if ((type == Integer.TYPE) || (type == Integer.class)) {
				// If an object is already a TclInteger type, then pass
				// the existing value directly. Otherwise, parse the
				// number as a Java int and see if it can be represented
				// as a Java int. This logic will raise an exception
				// when a number can't be represented as a 32bit signed int.
				// Tcl's weird number parsing rules will wrap the integer
				// around and calling code can't detect an overflow.

				int jint = parseJavaInt(interp, tclObj);
				return new Integer(jint);

			} else if ((type == Boolean.TYPE) || (type == Boolean.class)) {
				return new Boolean(TclBoolean.get(interp, tclObj));

			} else if ((type == Long.TYPE) || (type == Long.class)) {
				// If an object is already a TclInteger type, then pass
				// the existing value directly. Otherwise, parse the
				// number as a Java long. Raise a TclException if the
				// number is not an integer or is outside the long bounds.

				long jlong = parseJavaLong(interp, tclObj);
				return new Long(jlong);

			} else if ((type == Float.TYPE) || (type == Float.class)) {
				// Tcl stores floating point numbers as doubles,
				// so we just need to check to see if the value
				// is outside the float bounds. Invoking a Java
				// method should not automatically lose precision.

				double jdouble = TclDouble.get(interp, tclObj);
				float jfloat = (float) jdouble;

				if ((Double.isNaN(jdouble))
						|| (jdouble == Double.NEGATIVE_INFINITY)
						|| (jdouble == Double.POSITIVE_INFINITY)) {
					// No-op
				} else if ((jdouble != 0.0)
						&& ((Math.abs(jdouble) > (double) Float.MAX_VALUE) || (Math
								.abs(jdouble) < (double) Float.MIN_VALUE))) {
					throw new TclException(interp,
							"double value too large to represent in a float");
				}
				return new Float(jfloat);

			} else if ((type == Double.TYPE) || (type == Double.class)) {
				return new Double(TclDouble.get(interp, tclObj));

			} else if ((type == Byte.TYPE) || (type == Byte.class)) {
				// Parse a Java int, then check valid byte range.

				int jint = parseJavaInt(interp, tclObj);
				if ((jint < Byte.MIN_VALUE) || (jint > Byte.MAX_VALUE)) {
					throw new TclException(interp,
							"integer value too large to represent in a byte");
				}
				return new Byte((byte) jint);

			} else if ((type == Short.TYPE) || (type == Short.class)) {
				// Parse a Java int, then check valid byte range.

				int jint = parseJavaInt(interp, tclObj);
				if ((jint < Short.MIN_VALUE) || (jint > Short.MAX_VALUE)) {
					throw new TclException(interp,
							"integer value too large to represent in a short");
				}
				return new Short((short) jint);

			} else if ((type == Character.TYPE) || (type == Character.class)) {
				String str = tclObj.toString();
				if (str.length() != 1) {
					throw new TclException(interp,
							"expected character but got \"" + tclObj + "\"");
				}
				return new Character(str.charAt(0));

			} else if (type == TclObject.class) {
				// Pass a non ReflectObject TclObject directly to a Java method.
				return tclObj;
			} else {
				throw new TclException(interp, "\"" + tclObj
						+ "\" is not an object handle of class \""
						+ JavaInfoCmd.getNameFromClass(type) + "\"");
			}
		} else {
			// The TclObject is a ReflectObject that contains javaObj. We
			// check to see if javaObj can be converted to the required
			// type. If javaObj is a wrapper for a primitive type then
			// we check to see if the object is an instanceof the type.

			if (isAssignable(type, javaClass)) {
				return javaObj;
			}

			if (type.isPrimitive()) {
				if (type == Boolean.TYPE) {
					if (javaObj instanceof Boolean) {
						return javaObj;
					}
				} else if (type == Character.TYPE) {
					if (javaObj instanceof Character) {
						return javaObj;
					}
				} else if (type == Byte.TYPE) {
					if (javaObj instanceof Byte) {
						return javaObj;
					}
				} else if (type == Short.TYPE) {
					if (javaObj instanceof Short) {
						return javaObj;
					}
				} else if (type == Integer.TYPE) {
					if (javaObj instanceof Integer) {
						return javaObj;
					}
				} else if (type == Long.TYPE) {
					if (javaObj instanceof Long) {
						return javaObj;
					}
				} else if (type == Float.TYPE) {
					if (javaObj instanceof Float) {
						return javaObj;
					}
				} else if (type == Double.TYPE) {
					if (javaObj instanceof Double) {
						return javaObj;
					}
				} else if (type == Void.TYPE) {
					// void is not a valid type for conversions
				}
			}

			// Pass TclObject that contains the ReflectObject directly.
			if (type == TclObject.class) {
				return tclObj;
			}

			throw new TclException(interp, "expected object of type "
					+ JavaInfoCmd.getNameFromClass(type)
					+ " but got \""
					+ tclObj
					+ "\" ("
					+ ((javaClass == null) ? "null" : JavaInfoCmd
							.getNameFromClass(javaClass)) + ")");
		}
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * wrap --
	 * 
	 * Wraps a Java Object into a TclObject according to whether the convert
	 * flag is set.
	 * 
	 * Results: The TclObject that wraps the Java Object.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	private static final TclObject wrap(Interp interp, // Current interpreter.
			Class cls, // The class of the Java Object
			Object javaObj, // The Java Object to wrap.
			boolean convert) // Whether the value should be converted
			// into Tcl objects of the closest types.
			throws TclException {
		if (convert) {
			return convertJavaObject(interp, cls, javaObj);
		} else {
			return ReflectObject.newInstance(interp, cls, javaObj);
		}
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * isAssignable --
	 * 
	 * Return true if the argument object can be assigned to convert flag is
	 * set.
	 * 
	 * Results: The TclObject that wraps the Java Object.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static final boolean isAssignable(Class to_cls, // The class we want to
			// assign to
			Class from_cls) // The class we want to assign from (can be null)
	{
		// A primitive type can not be assigned the null value, but it
		// can be assigned to any type derived from Object.

		if (from_cls == null) {
			if (to_cls.isPrimitive()) {
				return false;
			} else {
				return true;
			}
		} else {
			if ((to_cls == from_cls) || to_cls.isAssignableFrom(from_cls)) {
				return true;
			} else {
				return false;
			}
		}
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * notAccessibleError --
	 * 
	 * Raise a specific TclException when a class that is not accessible is
	 * found.
	 * 
	 * Results: None.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static void notAccessibleError(Interp interp, Class cls)
			throws TclException {
		throw new TclException(interp, "Class \""
				+ JavaInfoCmd.getNameFromClass(cls) + "\" is not accessible");
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * isInnerClass --
	 * 
	 * Return true is a class is either an inner class or an inner interface.
	 * This is true only for classes defined inside other classes.
	 * 
	 * Results: None.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static boolean isInnerClass(Class cls) throws TclException {
		String cname = cls.getName();
		if (cname.indexOf('$') == -1) {
			return false;
		} else {
			return true;
		}
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * parseJavaInt --
	 * 
	 * Parse a Java int type from a TclObject. Unlike the rest of Tcl, this
	 * method will raise an error when an integer value is not in the range
	 * Integer.MIN_VALUE to Integer.MAX_VALUE. This is the range of a 32bit
	 * signed number as defined by Java. Tcl parses integers as 32bit unsigned
	 * numbers and wraps values outside the valid range. This method will catch
	 * the case of an integer outside of the valid range and raise a
	 * TclException so that a bogus value is not passed to Java.
	 * 
	 * Results: Returns an int value or raises a TclException to indicate that
	 * the number can't be parsed as a Java int.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static int parseJavaInt(Interp interp, TclObject obj) throws TclException {
		// No point in reparsing a "pure" integer.

		if (obj.hasNoStringRep() && obj.isIntType()) {
			return TclInteger.getInt(interp, obj);
		}

		String srep = obj.toString();
		String s = srep;
		int len = s.length();
		char c;
		int startInd, endInd;
		boolean isNegative = false;

		// Trim whitespace off front of string

		int i = 0;
		while (i < len
				&& (((c = s.charAt(i)) == ' ') || Character.isWhitespace(c))) {
			i++;
		}
		if (i >= len) {
			throw new TclException(interp, "expected integer but got \"" + s
					+ "\"");
		}
		startInd = i;

		// Trim whitespace off end of string

		endInd = len - 1;
		while (endInd > startInd
				&& (((c = s.charAt(endInd)) == ' ') || Character
						.isWhitespace(c))) {
			endInd--;
		}

		// Check for optional '-' sign, needed for hex and octal parse.

		c = s.charAt(i);
		if (c == '-') {
			isNegative = true;
			i++;
		}
		if (i >= (endInd + 1)) {
			throw new TclException(interp, "expected integer but got \"" + s
					+ "\"");
		}

		// Check for hex or octal string prefix characters

		int radix = Character.MIN_RADIX - 1; // An invalid value

		c = s.charAt(i);
		if (c == '0' && len > 1) {
			// Either hex or octal
			i++;
			c = s.charAt(i);

			if (len > 2 && (c == 'x' || c == 'X')) {
				// Parse as hex
				radix = 16;
				i++;
			} else {
				// Parse as octal
				radix = 8;
			}

			// Create string that contains a leading negative sign followed
			// by the radix letters, leaving out the radix prefix.
			// For example, "-0xFF" is parsed as "-FF".

			if (isNegative) {
				s = "-" + s.substring(i, endInd + 1);
			} else {
				s = s.substring(i, endInd + 1);
			}
		} else {
			// Parse as decimal integer

			if ((startInd > 0) || (endInd < (len - 1))) {
				s = s.substring(startInd, endInd + 1);
			}

			radix = 10;
		}

		if (s.length() == 0) {
			throw new TclException(interp, "expected integer but got \"" + srep
					+ "\"");
		}

		int ival;
		try {
			ival = Integer.parseInt(s, radix);
		} catch (NumberFormatException nfe) {
			// If one of the letters is not a valid radix character, then
			// the number is not a valid. Otherwise, the number must be
			// an integer value that is outside the valid range.

			for (i = 0; i < s.length(); i++) {
				c = s.charAt(i);
				if (i == 0 && c == '-') {
					continue; // Skip minus sign
				}
				if (Character.digit(c, radix) == -1) {
					throw new TclException(interp,
							"expected integer but got \"" + srep + "\"");
				}
			}

			throw new TclException(interp,
					"integer value too large to represent in a int");
		}

		return ival;
	}

	/*
	 * --------------------------------------------------------------------------
	 * ---
	 * 
	 * parseJavaLong --
	 * 
	 * Parse a Java long type from a TclObject. Tcl may not support 64 bit
	 * integers (Jacl does not), so this method needs to be used to determine if
	 * a string can be parsed into a long and if the result is in the range
	 * Long.MIN_VALUE to Long.MAX_VALUE. This method will catch the case of an
	 * long outside of the valid range and raise a TclException so that a bogus
	 * value is not passed to Java.
	 * 
	 * Results: Returns a long value or raises a TclException to indicate that
	 * the number can't be parsed as a Java long.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 * ---
	 */

	static long parseJavaLong(Interp interp, TclObject obj) throws TclException {
		// No point in reparsing a "pure" integer.

		if (obj.hasNoStringRep() && obj.isIntType()) {
			return (long) TclInteger.getLong(interp, obj);
		}

		String srep = obj.toString();
		String s = srep;
		int len = s.length();
		char c;
		int startInd, endInd;
		boolean isNegative = false;

		// Trim whitespace off front of string

		int i = 0;
		while (i < len
				&& (((c = s.charAt(i)) == ' ') || Character.isWhitespace(c))) {
			i++;
		}
		if (i >= len) {
			throw new TclException(interp, "expected integer but got \"" + s
					+ "\"");
		}
		startInd = i;

		// Trim whitespace off end of string

		endInd = len - 1;
		while (endInd > startInd
				&& (((c = s.charAt(endInd)) == ' ') || Character
						.isWhitespace(c))) {
			endInd--;
		}

		// Check for optional '-' sign, needed for hex and octal parse.

		c = s.charAt(i);
		if (c == '-') {
			isNegative = true;
			i++;
		}
		if (i >= (endInd + 1)) {
			throw new TclException(interp, "expected integer but got \"" + s
					+ "\"");
		}

		// Check for hex or octal string prefix characters

		int radix = Character.MIN_RADIX - 1; // An invalid value

		c = s.charAt(i);
		if (c == '0' && len > 1) {
			// Either hex or octal
			i++;
			c = s.charAt(i);

			if (len > 2 && (c == 'x' || c == 'X')) {
				// Parse as hex
				radix = 16;
				i++;
			} else {
				// Parse as octal
				radix = 8;
			}

			// Create string that contains a leading negative sign followed
			// by the radix letters, leaving out the radix prefix.
			// For example, "-0xFF" is parsed as "-FF".

			if (isNegative) {
				s = "-" + s.substring(i, endInd + 1);
			} else {
				s = s.substring(i, endInd + 1);
			}
		} else {
			// Parse as decimal integer

			if ((startInd > 0) || (endInd < (len - 1))) {
				s = s.substring(startInd, endInd + 1);
			}

			radix = 10;
		}

		if (s.length() == 0) {
			throw new TclException(interp, "expected integer but got \"" + srep
					+ "\"");
		}

		long lval;
		try {
			lval = Long.parseLong(s, radix);
		} catch (NumberFormatException nfe) {
			// If one of the letters is not a valid radix character, then
			// the number is not a valid. Otherwise, the number must be
			// an integer value that is outside the valid range.

			for (i = 0; i < s.length(); i++) {
				c = s.charAt(i);
				if (i == 0 && c == '-') {
					continue; // Skip minus sign
				}
				if (Character.digit(c, radix) == -1) {
					throw new TclException(interp,
							"expected integer but got \"" + srep + "\"");
				}
			}

			throw new TclException(interp,
					"integer value too large to represent in a long");
		}

		return lval;
	}

} // end JavaInvoke

