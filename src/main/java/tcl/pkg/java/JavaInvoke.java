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

	/**
	 * Call the specified constructor
	 * 
	 * @param interp current interpreter
	 * @param signature Constructor signature
	 * @param argv arguments
	 * @param startIdx index of first argument in argv to pass to constructor
	 * @param count number of arguments to pass to constructor
	 * @return the object created by the constructor
	 * @throws TclException
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


	/**
	 * Call the specified instance or static method of the given object.
	 * 
	 * @param interp Current interpreter
	 * @param reflectObj The object whose method to invoke
	 * @param signature Method signature
	 * @param argv Arguments
	 * @param startIdx Index of the first argument in argv[] to pass to the method.
	 * @param count Number of arguments to pass to the method.
	 * @param convert Whether the value should be converted into Tcl objects of the closest types.
	 * @return
	 * @throws TclException
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

	/**
	 * Call the specified static method of the given object.
	 * 
	 * @param interp Current interpreter
	 * @param classObj Class whose static method to invoke
	 * @param signature Method signature
	 * @param argv Arguments
	 * @param startIdx Index of the first argument in argv[] to pass to the method.
	 * @param count Number of arguments to pass to the method.
	 * @param convert Whether the value should be converted into Tcl objects of the closest types.
	 * @return
	 * @throws TclException
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


	/**
	 * Call the constructor, instance method, or static method with the given
	 * parameters. Check the parameter types and perform TclObject to JavaObject
	 * conversion
	 * 
	 * @param interp Current interpreter
	 * @param func Constructor or method to call 
	 * @param obj The object associated with an instance method call, should be null for static method calls
	 * @param argv Arguments
	 * @param startIdx Index of the first argument in argv[] to pass to the method.
	 * @param count Number of arguments to pass to the method.
	 * @param convert Whether the value should be converted into Tcl objects of the closest types.
	 * @return
	 * @throws TclException
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

	
	/**
	 * 
	 * @param interp current interpreter
	 * @param classOrObj Class or object whose field to get
	 * @param signature Signature of the field
	 * @param convert set to true to convert into Tcl objects
	 * @return an array: Object result[2]. result[0]
	 * is the value of the field; result[1] is the type of the field.
	 * @throws TclException
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


	/**
	 * Sets the value of a field in the given object
	 * 
	 * @param interp current interpreter
	 * @param classOrObj class or object whose field to get
	 * @param signature signature of the field
	 * @param value new value for the field
	 * @throws TclException
	 */
	static final void setField(Interp interp, // Current interpreter.
			TclObject classOrObj, // Class or object whose field to get.
			TclObject signature, // Signature of the field.
			TclObject value) // New value for the field.
			throws TclException // Standard Tcl exception.
	{
		getsetField(interp, classOrObj, signature, value, false, false);
	}

	
	/**
	 * Gets or sets the value of a field in the given object
	 * 
	 * @param interp current interpreter
	 * @param classOrObj class or object whose field to get
	 * @param signature signature of the field
	 * @param value new value for the field
	 * @param convert set to true to convert into Tcl Object
	 * @param isget set to true to get field, false to set field
	 * @throws TclException
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


	/**
	 * 
	 * @param interp current interpreter 
	 * @param reflectObj object whose property to query
	 * @param propName name of property to query
	 * @param convert if true, convert to Tcl Objects
	 * @return the value of a property in the given object.
	 * @throws TclException
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

	/**
	 * Set a property of an object
	 * 
	 * @param interp current interpreter 
	 * @param reflectObj object whose property to query
	 * @param propName name of property to query
	 * @param value new value for the property 
	 * @throws TclException
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

	/**
	 * Returns Class object identified by the string name. We allow abbreviation
	 * of the java.lang.* class if there is no ambiguity: e.g., if there is no
	 * class whose fully qualified name is "String", then "String" means
	 * java.lang.String. Inner classes are supported both with fully qualified
	 * names and imported class names.
	 * 
	 * @param interp current interpreter
	 * @param clsName string name of the class
	 * @return Class object for the class
	 * @throws TclException
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


	/**
	 * Converts the java.lang.Object into a Tcl object and return TclObject that
	 * holds the result. Primitive data types are converted into primitive Tcl
	 * data types. Otherwise, a ReflectObject wrapper is created for the object
	 * so that it can be later accessed with the Reflection API.
	 * 
	 * @param interp current interpreter
	 * @param cls class of the Java Object
	 * @param javaObj java.lang.Object object to convert to a TclObject
	 * @return TclObject that was created
	 * @throws TclException
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
			return TclInteger.newInstance(((Long) javaObj).longValue());
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

	/**
	 * Converts a Tcl object to a Java object of the required type
	 * 
	 * @param interp current interpreter
	 * @param type Convert to this type
	 * @param tclObj Convert from this Tcl object
	 * @return object of the requested type
	 * @throws TclException if the conversion fails
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
				return new Integer(TclInteger.getInt(interp, tclObj));

			} else if ((type == Boolean.TYPE) || (type == Boolean.class)) {
				return new Boolean(TclBoolean.get(interp, tclObj));

			} else if ((type == Long.TYPE) || (type == Long.class)) {
				return new Long(TclInteger.getLong(interp, tclObj));

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

				int jint = TclInteger.getInt(interp, tclObj);
				if ((jint < Byte.MIN_VALUE) || (jint > Byte.MAX_VALUE)) {
					throw new TclException(interp,
							"integer value too large to represent in a byte");
				}
				return new Byte((byte) jint);

			} else if ((type == Short.TYPE) || (type == Short.class)) {
				// Parse a Java int, then check valid byte range.

				int jint = TclInteger.getInt(interp, tclObj);
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

	
	/**
	 * Wraps a Java Object into a TclObject according to whether the convert
	 * flag is set.
	 * 
	 * @param interp current interpreter
	 * @param cls the class of the java object
	 * @param javaObj the java object to wrap
	 * @param convert if true, convert value to Tcl objects
	 * @return wrapped Java object, if so requested
	 * @throws TclException
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


	/**
	 * @param to_cls class we want to assign to
	 * @param from_cls class we want to assign from; can be null
	 * @return true if the argument object can be assigned to 
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

	
	/**
	 * @param interp current interpreter
	 * @param cls class to throw exception about
	 * @throws TclException always, indicating class is not accessible
	 */
	static void notAccessibleError(Interp interp, Class cls)
			throws TclException {
		throw new TclException(interp, "Class \""
				+ JavaInfoCmd.getNameFromClass(cls) + "\" is not accessible");
	}


	/**
	 * @param cls class to test
	 * @return true if is a class is either an inner class or an inner interface.
	 * This is true only for classes defined inside other classes.
	 * @throws TclException
	 */
	static boolean isInnerClass(Class cls) throws TclException {
		String cname = cls.getName();
		if (cname.indexOf('$') == -1) {
			return false;
		} else {
			return true;
		}
	}

} // end JavaInvoke

