/*
 * PkgInvoker --
 *
 *	This class is used for the java::* commands to gain access to
 *	package protected and protected members of a Java package.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 * RCS: @(#) $Id: PkgInvoker.java,v 1.9 2006/06/30 00:30:52 mdejong Exp $
 *
 */

package tcl.pkg.java.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Hashtable;

/*
 * This class is used for the java::* commands to gain access to
 * package protected and protected members of a Java package. 
 *
 * Normally, the java::* command can only access public members of
 * public classes inside any package. With the help of the PkgInvoker
 * class, the java::* command can access any member (constructor,
 * method, field or property) of any class in a package as long as the
 * member is not explicitly declared "private"
 *
 * The ability for Tcl to access protected members is desirable when
 * we use Tcl to perform "white-box" testing on the methods of a Java
 * package.
 *
 * To grant Tcl access to protected members of a package, do the
 * following:
 *
 *	+ Create a subclass of PkgInvoker, give it the name
 *	  "TclPkgInvoker", declare it public and place it inside the
 *	  said package.
 *
 *	+ Give your TclPkgInvoker class a public constructor with
 *	  no arguments.
 *
 *	+ Cut-and-paste the definitions of the following functions into
 *	  TclPkgInvoker class: invokeMethod, invokeConstructor,
 *	  getField and setField.
 *
 * An example of using PkgInvoker can be found in the directory
 * src/tests/pkg1 and the file tests/common/PkgInvoker.test
 *
 */

public class PkgInvoker {

	// PkgInvokers of the packages that we have already visited are stored
	// in this hashtable. They key is the String name of a package.

	// FIXME: There is a problem here when mutliple interps could be making
	// use of the same cachedInvokers table. If a name conflict were
	// encountered, incorrect result would be the result.
	static Hashtable cachedInvokers = new Hashtable();

	// This is the default invoker to use if a package doesn't include a
	// proper TclPkgInvoker class. This means only the public members
	// of the public classes of that package can be accessed diretly
	// from Tcl.

	static PkgInvoker defaultInvoker = new PkgInvoker();

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * invokeConstructor --
	 * 
	 * Invoke the given constructor with the arguments.
	 * 
	 * Results: The new object instance returned by the constructor.
	 * 
	 * Side effects: The constructor may have arbitraty side effects.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public Object invokeConstructor(Constructor constructor, // The constructor
			// to invoke.
			Object args[]) // Arguments for the constructor.
			throws InstantiationException, // Standard exceptions thrown by
			IllegalAccessException, // Constructor.newInstance.
			IllegalArgumentException, InvocationTargetException {
		return constructor.newInstance(args);
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * invokeMethod --
	 * 
	 * Invoke the given method of the obj with the arguments.
	 * 
	 * Results: The value returned by the method.
	 * 
	 * Side effects: The method may have arbitraty side effects.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public Object invokeMethod(Method method, // The method to invoke.
			Object obj, // The object associated with the method.
			// May be null if the method is static.
			Object args[]) // The arguments for the method.
			throws IllegalAccessException, // Standard exceptions throw by
			// Method.Invoke.
			IllegalArgumentException, InvocationTargetException {
		return method.invoke(obj, args);
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getField --
	 * 
	 * Query the value of the given field.
	 * 
	 * Results: The value of the field.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public Object getField(Field field, // The field to query.
			Object obj) // The object that owns the field. May be
			// null for static fields.
			throws IllegalArgumentException, // Standard exceptions thrown by
			// Field.get().
			IllegalAccessException {
		return field.get(obj);
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * setField --
	 * 
	 * Modify the value of the given field.
	 * 
	 * Results: None.
	 * 
	 * Side effects: When successful, the field is modified to be the new value.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public void setField(Field field, // The field to modify.
			Object obj, // The object that owns the field. May be
			// null for static fields.
			Object value) // New value for the field.
			throws IllegalArgumentException, // Standard exceptions thrown by
			// Field.set().
			IllegalAccessException {
		field.set(obj, value);
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getPkgInvoker --
	 * 
	 * Returns the PkgInvoker for the package that includes the given class.
	 * 
	 * Results: An instance of the PkgInvoker which is included in the package,
	 * or defaultInvoker if the package doesn't include a proper PkgInvoker.
	 * 
	 * Side effects: The returned value is also stored in a hashtable for faster
	 * access in the future.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public static final PkgInvoker getPkgInvoker(Class cls) // Query the
	// PkgInvoker of the
	// package
	// that owns this class.
	{
		String clsName = cls.getName();
		int index = clsName.lastIndexOf('.');
		String pkg;

		if (index == -1) {
			pkg = "";
		} else {
			pkg = clsName.substring(0, index);
		}

		PkgInvoker invoker = (PkgInvoker) cachedInvokers.get(pkg);
		if (invoker == null) {
			// Use the class loader that loaded the class
			// in question. The Class.getClassLoader()
			// API can return null to indicate the
			// bootstrap or system loader.

			ClassLoader cloader = cls.getClassLoader();

			try {
				if (cloader != null) {
					Class invCls = cloader.loadClass(pkg + ".TclPkgInvoker");
					invoker = (PkgInvoker) invCls.newInstance();
				}
			} catch (Exception e) {
				// The package doesn't include a PkgInvoker class. We use
				// the default invoker, which means we can't invoke
				// any of the protected members inside this package.

				invoker = defaultInvoker;
			}

			if (invoker == null) {
				invoker = defaultInvoker;
			}

			// FIXME: should we store default pkg invoker in invoker table.
			// Should we store all the possible package that do not have
			// an invoker. How will we know if an earlier check failed ???
			// This also needs to be tied into a common "cache" system that can
			// be controlled by the user with some tcl commands.

			cachedInvokers.put(pkg, invoker);
		}

		return invoker;
	}

	// Return true if the passed in class uses the default invoker,
	// meaning there is no custom invoker for the package.

	public static boolean usesDefaultInvoker(Class cls) {
		PkgInvoker invoker = getPkgInvoker(cls);
		return (invoker == defaultInvoker);
	}

	// Return true if the given class is accessible,
	// meaning it is public or it is not private
	// and we have an invoker for the package.

	public static boolean isAccessible(Class cls) {
		int mod = cls.getModifiers();
		if (Modifier.isPublic(mod))
			return true;
		if (Modifier.isPrivate(mod))
			return false;
		if (usesDefaultInvoker(cls))
			return false;
		return true;
	}

	// Return true if the given Method is accessible,
	// meaning it is public or it is not private
	// and we have an invoker for the package.

	public static boolean isAccessible(Method meth) {
		int mod = meth.getModifiers();
		if (Modifier.isPublic(mod))
			return true;
		if (Modifier.isPrivate(mod))
			return false;
		if (usesDefaultInvoker(meth.getDeclaringClass()))
			return false;
		return true;
	}

	// Return true if the given Constructor is accessible,
	// meaning it is public or it is not private
	// and we have an invoker for the package.

	public static boolean isAccessible(Constructor cons) {
		int mod = cons.getModifiers();
		if (Modifier.isPublic(mod))
			return true;
		if (Modifier.isPrivate(mod))
			return false;
		if (usesDefaultInvoker(cons.getDeclaringClass()))
			return false;
		return true;
	}

	// Return true if the given Field is accessible,
	// meaning it is public or it is not private
	// and we have an invoker for the package.

	public static boolean isAccessible(Field fld) {
		int mod = fld.getModifiers();
		if (Modifier.isPublic(mod))
			return true;
		if (Modifier.isPrivate(mod))
			return false;
		if (usesDefaultInvoker(fld.getDeclaringClass()))
			return false;
		return true;
	}

} // end PkgInvoker

