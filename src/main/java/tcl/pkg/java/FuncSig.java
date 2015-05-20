/*
 * FuncSig.java --
 *
 *	This class implements the internal representation of a Java
 *	method or constructor signature.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 * RCS: @(#) $Id: FuncSig.java,v 1.15 2006/05/15 22:14:23 mdejong Exp $
 *
 */

package tcl.pkg.java;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map.Entry;

import tcl.lang.InternalRep;
import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclList;
import tcl.lang.TclObject;
import tcl.lang.TclString;
import tcl.pkg.java.reflect.PkgInvoker;

/**
 * This class implements the internal representation of a Java method or
 * constructor signature. Because methods and constructors are very similar to
 * each other, the operations on method signatures and constructor signatures
 * are limped in this class of "function signature."
 */

class FuncSig implements InternalRep {

	// The class that a method signature is used against. In the case of a
	// static method call by java::call, targetCls is given by the <class>
	// argument to java::call. In the case of an instance method call,
	// targetCls is the class of the instance. targetCls is used to test
	// the validity of a cached FuncSig internal rep for method
	// signatures.
	//
	// targetCls is not used for class signatures.

	private static final Method[] ZERO_METHODS = new Method[0];

	Class targetCls;

	// The PkgInvoker used to access the constructor or method.

	PkgInvoker pkgInvoker;

	// The constructor or method given by the field signature. You need to
	// apply the instanceof operator to determine whether it's a
	// Constructor or a Method.
	//
	// func may be a public, protected, package protected or private
	// member of the given class. Attempts to access func is subject to
	// the Java language access control rules. Public members can always
	// be accessed. Protected and package protected members can be
	// accessed only if a proper TclPkgInvoker class exists. Private
	// members can never be accessed.
	//
	// If the signature is a method signature and the specified method has
	// been overloaded, then func will point to the "most public" instance
	// of that method. The order of public-ness is ranked as the following
	// (a larger number means more public):
	//
	// RANK METHOD ACCESS TYPE CLASS ACCESS TYPE
	// 0 private any
	// 1 package protected protected
	// 1 protected protected
	// 1 public protected
	// 1 package protected public
	// 1 protected public
	// 2 public public

	Object func;

	// Stores all accessible instance methods for a Java class
	// Note that we use a Hashtable instead of a HashMap here
	// since these fields could be accessed from multiple
	// threads and the Hashtable class is synchronized.

	static Hashtable instanceMethodTable = new Hashtable();
	static Hashtable staticMethodTable = new Hashtable();
	static Hashtable<Class<?>, HashMap<String, Method[]>> instanceMethodTableByName = new Hashtable<Class<?>, HashMap<String, Method[]>>();

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * FuncSig --
	 * 
	 * Creates a new FuncSig instance.
	 * 
	 * Side effects: Member fields are initialized.
	 * 
	 * ----------------------------------------------------------------------
	 */

	FuncSig(Class cls, // Initial value for targetCls.
			PkgInvoker p, // Initial value for pkgInvoker.
			Object f) // Initial value for func.
	{
		targetCls = cls;
		pkgInvoker = p;
		func = f;
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
		return new FuncSig(targetCls, pkgInvoker, func);
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
	 * Returns the FuncSig internal representation of the constructor or method
	 * that matches with the signature and the parameters.
	 * 
	 * Results: The FuncSig given by the signature. An exception will be raised
	 * if the constructor provided is for an abstract class, or if the class is
	 * not accessible.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static FuncSig get(Interp interp, // Current interpreter.
			Class cls, // If null, we are looking for a constructor
			// in signature. If non-null, we are looking
			// for a method of this class in signature.
			TclObject signature, // Method/constructor signature.
			TclObject[] argv, // Arguments.
			int startIdx, // Index of the first argument in argv
			int count, // Number of arguments to pass to the
			// constructor.
			boolean isStatic) // True if signature is for a static Method.
			throws TclException {
		boolean isConstructor = (cls == null);

		/*
		 * FIXME: commented out Method caching system we comment out this code
		 * because it causes the AmbiguousSignature-2.1 test to fail under Tcl
		 * Blend. a new caching system might help but it is unclear if
		 * determining a cache "match" is any less work then just searching.
		 * 
		 * 
		 * InternalRep rep = signature.getInternalRep();
		 * 
		 * // If a valid FuncSig internal rep is already cached, return it //
		 * right away.
		 * 
		 * if (rep instanceof FuncSig) { FuncSig tmp = (FuncSig)rep; Object func
		 * = tmp.func;
		 * 
		 * if (isConstructor) { if ((func instanceof Constructor) &&
		 * (((Constructor)func).getParameterTypes().length == count)) { return
		 * tmp; } } else { if ((func instanceof Method) && (tmp.targetCls ==
		 * cls) && (((Method)func).getParameterTypes().length == count)) {
		 * return tmp; } } }
		 */

		// Look up the constructor or method using the string rep of the
		// signature object.

		Object match;
		int sigLength = TclList.getLength(interp, signature);
		String methodName = null;
		TclObject class_or_method;

		if (sigLength == 0) {
			throw new TclException(interp, "bad signature \"" + signature + "\"");
		} else if (sigLength == 1) {
			class_or_method = signature;
		} else {
			class_or_method = TclList.index(interp, signature, 0);
		}

		if (isConstructor) {
			cls = JavaInvoke.getClassByName(interp, class_or_method.toString());
		} else {
			methodName = class_or_method.toString();
		}

		if ((isConstructor || isStatic) && !PkgInvoker.isAccessible(cls)) {
			JavaInvoke.notAccessibleError(interp, cls);
		}

		if (isConstructor && Modifier.isAbstract(cls.getModifiers())) {
			throw new TclException(interp, "Class \"" + JavaInfoCmd.getNameFromClass(cls) + "\" is abstract");
		}

		if ((sigLength > 1) || (sigLength == 1 && count == 0)) {
			// We come to here if one of the following two cases in true:
			//
			// [1] (sigLength > 1): A signature has been given.
			// [2] (sigLength == 1 && count == 0): A signature of no
			// parameters is implied.
			//
			// In both cases, we search for a method that matches exactly
			// with the signature.

			int sigNumArgs = sigLength - 1;
			Class[] paramTypes = new Class[sigNumArgs];

			for (int i = 0; i < sigNumArgs; i++) {
				String clsName = TclList.index(interp, signature, i + 1).toString();
				paramTypes[i] = JavaInvoke.getClassByName(interp, clsName);
			}

			if (isConstructor) {
				try {
					match = getAccessibleConstructor(cls, paramTypes);
				} catch (NoSuchMethodException e) {
					if (sigLength > 1) {
						throw new TclException(interp, "no accessible constructor \"" + signature + "\"");
					} else {
						throw new TclException(interp, "can't find accessible constructor with " + count
								+ " argument(s) for class \"" + JavaInfoCmd.getNameFromClass(cls) + "\"");
					}
				}
			} else {
				match = lookupMethod(interp, cls, methodName, paramTypes, signature, isStatic);
			}
		} else {
			match = matchSignature(interp, cls, signature, methodName, isConstructor, argv, startIdx, count, isStatic);
		}

		FuncSig sig = new FuncSig(cls, PkgInvoker.getPkgInvoker(cls), match);
		// signature.setInternalRep(sig);

		return sig;
	}

	// lookupMethod attempts to find an exact match for the method name
	// based on the types (Java Class objects) of the arguments to the
	// method. If an exact match can not be found it will raise a TclException.

	static Method lookupMethod(Interp interp, // the tcl interpreter
			Class cls, // the Java objects class
			String methodName, // name of method
			Class[] paramTypes, // the Class object arguments
			TclObject signature, // used for error reporting
			boolean isStatic // True if signature is for a static Method.
	) throws TclException {
		Method[] methods;
		boolean foundSameName = false;

		if (isStatic)
			methods = getAccessibleStaticMethods(cls);
		else {
			// methods = getAccessibleInstanceMethods(cls);
			methods = getAccessibleInstanceMethods(cls, methodName);
		}

		// FIXME : searching Java methods for method name match
		// searching through ALL the methods is really slow
		// there really should be a better way to do this
		// as it needs to be done on every method invocation

		for (int i = 0; i < methods.length; i++) {
			if (!methodName.equals(methods[i].getName())) {
				continue;
			}

			foundSameName = true;

			Class[] pt = methods[i].getParameterTypes();
			if (pt.length != paramTypes.length) {
				continue;
			}

			boolean good = true;
			for (int j = 0; j < pt.length; j++) {
				if (pt[j] != paramTypes[j]) {
					good = false;
					break;
				}
			}
			if (good) {
				return methods[i];
			}
		}

		if (paramTypes.length > 0 || !foundSameName) {
			throw new TclException(interp, "no accessible" + (isStatic ? " static " : " ") + "method \"" + signature
					+ "\" in class " + JavaInfoCmd.getNameFromClass(cls));
		} else {
			throw new TclException(interp, "can't find accessible" + (isStatic ? " static " : " ") + "method \""
					+ signature + "\" with " + paramTypes.length + " argument(s) for class \""
					+ JavaInfoCmd.getNameFromClass(cls) + "\"");
		}
	}

	// This method will attempt to find a match for a signature
	// if an exact match can not be found then it will use
	// the types of the argument objects to "guess" what
	// method was intended by the user. If no match can be
	// found after "guessing" then a TclException will be raised.

	static Object matchSignature(Interp interp, // the tcl interpreter
			Class cls, // the Java objects class
			TclObject signature, // used for error reporting
			String methodName, // name of method, can be null
			boolean isConstructor, // duh
			TclObject[] argv, // arguments to Method or Constructor
			int startIdx, // Index of the first argument in argv
			int argv_count, // set to -1 if JFK was killed by the FBI
			boolean isStatic // True if signature is for a static Method.
	) throws TclException {
		Object[] funcs;
		boolean foundSameName = false;
		ArrayList match_list = new ArrayList();
		int i, j;

		final boolean debug = false;

		if (isConstructor) {
			funcs = getAccessibleConstructors(cls);
		} else {
			if (isStatic)
				funcs = getAccessibleStaticMethods(cls);
			else {
				// funcs = getAccessibleInstanceMethods(cls);
				funcs = getAccessibleInstanceMethods(cls, methodName);
			}
		}

		for (i = 0; i < funcs.length; i++) {
			Class[] paramTypes;
			if (isConstructor) {
				paramTypes = ((Constructor) funcs[i]).getParameterTypes();
			} else {
				Method method = (Method) funcs[i];
				if (!methodName.equals(method.getName())) {
					continue;
				}
				foundSameName = true;

				paramTypes = method.getParameterTypes();
			}

			if (paramTypes.length == argv_count) {
				match_list.add(funcs[i]);
			}
		}

		// If there is only a single remaining match then we can return it now

		if (match_list.size() == 1) {
			// debug print the single match
			// System.out.println("single match : " + match_list.get(0));

			return match_list.get(0);
		} else if (match_list.size() > 1) {

			Class[] argv_classes = new Class[argv_count];
			Class[] match_classes;

			// get the object types for the method arguments in argv
			for (i = 0; i < argv_count; i++) {
				TclObject tobj = argv[startIdx + i];

				boolean isJavaObj = true;
				Class c = null;
				try {
					c = ReflectObject.getClass(interp, tobj);
				} catch (TclException e) {
					isJavaObj = false;
				}

				if (isJavaObj) {
					argv_classes[i] = c;
				} else {
					argv_classes[i] = String.class;
				}
			}

			if (debug) {
				// debug print argv types

				System.out.println("multiple matches for method " + methodName);

				System.out.print("argv    is ");

				for (i = 0; i < argv_count; i++) {
					Class c = argv_classes[i];
					System.out.print(((c == null) ? "null" : JavaInfoCmd.getNameFromClass(c)));
					System.out.print(" ");
				}
				System.out.println();

				// debug print possible match types

				for (i = 0; i < match_list.size(); i++) {

					if (isConstructor) {
						match_classes = ((Constructor) match_list.get(i)).getParameterTypes();
					} else {
						match_classes = ((Method) match_list.get(i)).getParameterTypes();
					}

					System.out.print("match " + i + " is ");

					for (j = 0; j < match_classes.length; j++) {
						Class c = match_classes[j];
						System.out.print(JavaInfoCmd.getNameFromClass(c));
						System.out.print(" ");
					}

					System.out.println();
				}
			} // end if (debug)

			// try to match the argument types and the
			// match types exactly by comparing Class objects

			for (i = 0; i < match_list.size(); i++) {

				if (isConstructor) {
					match_classes = ((Constructor) match_list.get(i)).getParameterTypes();
				} else {
					match_classes = ((Method) match_list.get(i)).getParameterTypes();
				}

				boolean exact = true;
				for (j = 0; j < argv_count; j++) {
					if (match_classes[j] != argv_classes[j]) {
						exact = false;
						break;
					}
				}

				if (exact) {
					if (debug) {
						System.out.println("exact match at " + i);
					} // end if (debug)
					return match_list.get(i);
				}
			}

			// loop from the end of the list to the begining and
			// remove those signatures that are not assignable
			// take special care not to remove signatures that
			// have an object decended from java.lang.Object
			// in the same position as a null Class in argv_classes.
			// This means a null argument will not match a built in type.

			for (i = match_list.size() - 1; i >= 0; i--) {

				if (isConstructor) {
					match_classes = ((Constructor) match_list.get(i)).getParameterTypes();
				} else {
					match_classes = ((Method) match_list.get(i)).getParameterTypes();
				}

				// If any of the arguments are not assignable to the method
				// arguments,
				// then remove this method from the list of matches.

				for (j = 0; j < argv_count; j++) {
					if (!JavaInvoke.isAssignable(match_classes[j], argv_classes[j])) {

						if (debug) {
							System.out.println("removing non assignable match " + i);
						} // end if (debug)

						match_list.remove(i);
						break; // go on to next Method
					}
				}

			}

			if (debug) {
				// debug print match_list after isAssignabelFrom test

				if (match_list.size() > 0) {
					System.out.println("isAssignableFrom() matches");
				}

				for (i = 0; i < match_list.size(); i++) {

					if (isConstructor) {
						match_classes = ((Constructor) match_list.get(i)).getParameterTypes();
					} else {
						match_classes = ((Method) match_list.get(i)).getParameterTypes();
					}

					System.out.print("match " + i + " is ");

					for (j = 0; j < argv_count; j++) {
						Class c = match_classes[j];
						System.out.print(JavaInfoCmd.getNameFromClass(c));
						System.out.print(" ");
					}

					System.out.println();
				}
			} // end if (debug)

			// If there is only a single remaining match then we can return it
			// now

			if (match_list.size() == 1) {
				return match_list.get(0);
			}

			// at this point match_list should have only those signatures
			// that can take the argument types from argv with widining
			// conversion

			// to figure out which method we should call we need to determine
			// which signatures are "better" then others where "better" is
			// defined
			// as the shortest number of steps up the inheritance or interface
			// tree

			if (match_list.size() > 1) {

				// the first thing we need to do is get the inheritance info
				// of the arguments to the method. From this we will create
				// an array of Class objects used to match against the possible
				// Method objects that we could invoke with this class name

				// as an example if we invoked a Method that took one
				// String argument then the argv_classes_lookup array would
				// end up as a 1 x 4 array with the java.lang.Class object
				// of the argument at [0,0] in the array. The inheritance
				// tree of the object would be represented by the [0,X]
				// members of the array (X depends on number of parents)
				// Class objects and Interface objects are stored and
				// null is used to keep track of the end of each Class

				// Example argument : {String}
				// Example array : {String,Serializable,null,Object}
				// Example info : String implements 1 interface = Serializable
				// Example info : String has 1 parent = Object

				Class[][] argv_classes_lookup = new Class[argv_count][];

				// we use a vector to store up all of the Class objects
				// that make up the inheritance tree for a particular class

				ArrayList class_list = new ArrayList();

				// for each argument to the method we loop up the inheritance
				// tree
				// to find out if there is a superclass argv class that exactly
				// matches

				for (i = 0; i < argv_count; i++) {
					Class c = argv_classes[i]; // Start with class type of
					// argument

					if (c == null) {
						continue; // Skip lookup for argument when Class type is
						// null
					}

					// loop over the first elements of the argv_classes_lookup
					// to find out
					// if we have already looked up this class object and if we
					// have just
					// use the array we found last time instead of doing the
					// lookup again

					// Note that we would not need to do this if we cached the
					// lookups

					for (j = 0; j < i; j++) {
						if (c == argv_classes_lookup[j][0]) {
							if (debug) {
								System.out.println("using argv_classes_lookup shortcut");
							} // end if (debug)
							argv_classes_lookup[i] = argv_classes_lookup[j];
							continue;
						}
					}

					// loop up the inheritance tree starting from c

					while (c != null) {
						// add a null to the front of the vector
						class_list.add(null);

						// add this Class and its Interfaces to the vector
						addInterfaces(c, class_list);

						c = c.getSuperclass();
					}

					// now remove the first element of the vector (it is null)
					class_list.remove(0);

					Class[] classes = new Class[class_list.size()];
					for (j = 0; j < classes.length; j++) {
						classes[j] = (Class) class_list.get(j);
					}

					argv_classes_lookup[i] = classes;

					class_list.clear();
				}

				if (debug) {
					// debug print the argv_classes_lookup array

					System.out.println("argv_classes_lookup array");

					for (i = 0; i < argv_count; i++) {

						Class[] classes = argv_classes_lookup[i];

						if (classes == null) {
							System.out.println("{ null }");
							continue;
						}

						System.out.print("{ ");

						for (j = 0; j < classes.length; j++) {

							if (classes[j] == null) {
								System.out.print("null");
							} else {
								System.out.print(JavaInfoCmd.getNameFromClass(classes[j]));
							}

							System.out.print(' ');
						}

						System.out.println("}");

					}
				} // end if (debug)

				int[] super_steps = new int[match_list.size()];
				int[] total_steps = new int[match_list.size()];
				boolean[] trim_matches = new boolean[match_list.size()];
				int min_super_step;
				int min_total_step;
				Class min_class;

				// iterate over the arguments then the Methods
				// as opposed to Methods then over the arguments

				for (j = 0; j < argv_count; j++) {

					// we need to keep track of the smallest # of jumps up the
					// inheritance tree as well as the total min for the one
					// special case where an implemented interface inherits
					// from another interface that also matches the signature

					min_super_step = Integer.MAX_VALUE;
					min_total_step = Integer.MAX_VALUE;

					// define min_class as base object before we loop
					min_class = Object.class;

					// iterate over the matched methods to find the
					// minimum steps for this argument

					for (i = 0; i < match_list.size(); i++) {

						if (isConstructor) {
							match_classes = ((Constructor) match_list.get(i)).getParameterTypes();
						} else {
							match_classes = ((Method) match_list.get(i)).getParameterTypes();
						}

						Class match_to = match_classes[j];

						// Class objects we will compare the match_to Class
						// against
						// the index (j) gives us the Class array for argv[j]
						Class[] arg_classes = argv_classes_lookup[j];

						// If the argument type is null then skip to the next
						// argument and max the steps so they do not get removed
						if (arg_classes == null) {
							super_steps[i] = Integer.MAX_VALUE;
							total_steps[i] = Integer.MAX_VALUE;
							continue;
						}

						Class c;
						int super_step = 0;
						int total_step = 0;

						for (; total_step < arg_classes.length; total_step++) {
							c = arg_classes[total_step];

							if (c == null) {
								super_step++; // null means we have gone up to
								// the superclass
							} else if (c == match_to) {
								super_steps[i] = super_step; // # of super
								// classes up
								total_steps[i] = total_step; // total # of
								// visible
								// classes

								// when we define the min for an argument we
								// must make
								// sure that three precidence rules are followed
								// 1: an interface can replace another interface
								// as the min
								// 2: an interface can replace the class Object
								// 3: a class can replace an interface or a
								// class

								// thus if we have already found a non Object
								// min_class
								// it can not be replaced by an interface

								if (super_step <= min_super_step) {

									if (!c.isInterface() || min_class == Object.class || min_class.isInterface()) {

										if (debug) {
											// System.out.println("redefing min");
											// System.out.println("min_class was "
											// + min_class);
											// System.out.println("min_class is now "
											// + c);
										} // end if (debug)

										min_class = c;

										min_super_step = super_step;

										// check min_total_step only AFTER a
										// min_super_step
										// or equal to min_super_step has been
										// found

										if (total_step < min_total_step) {
											min_total_step = total_step;
										}
									}
								}

								break;
							}
						}
					}

					if (debug) {
						// debug print the super_step array and the total_step
						// array

						System.out.println("step arrays for argument " + j);

						for (int loop = 0; loop < match_list.size(); loop++) {
							System.out.println("(" + super_steps[loop] + "," + total_steps[loop] + ")");
						}

						System.out.println("min_super_step = " + min_super_step);
						System.out.println("min_total_step = " + min_total_step);
					} // end if (debug)

					// from the step info we know the minumum so we can
					// remove those values that are "worse" then the min

					for (i = match_list.size() - 1; i >= 0; i--) {

						if (super_steps[i] > min_super_step
								|| (super_steps[i] == min_super_step && total_steps[i] > min_total_step)) {

							if (debug) {
								System.out.println("will trim method " + i);
							} // end if (debug)

							trim_matches[i] = true; // trim this match # later
						}

					}

					// we should be able to short circut this so that we do
					// not waste loops when they are not needed

					// if all the methods have been trimmed then we do not
					// need to loop to the next argument

				}

				// remove the methods that were marked for deletion

				for (i = match_list.size() - 1; i >= 0; i--) {
					if (trim_matches[i]) {
						match_list.remove(i);
					}
				}

				if (debug) {
					// Debug print remaining matches

					System.out.println("after super steps trim");

					for (i = 0; i < match_list.size(); i++) {

						if (isConstructor) {
							match_classes = ((Constructor) match_list.get(i)).getParameterTypes();
						} else {
							match_classes = ((Method) match_list.get(i)).getParameterTypes();
						}

						System.out.print("match " + i + " is ");

						for (j = 0; j < argv_count; j++) {
							Class c = match_classes[j];
							System.out.print(JavaInfoCmd.getNameFromClass(c));
							System.out.print(" ");
						}

						System.out.println();
					}
				} // end if (debug)

			} // end if (match_list.size() > 1)

			// if there is only one item left in the match_list return it

			if (match_list.size() == 1) {
				return match_list.get(0);
			} else {
				// if we have 0 or >1 remaining matches then
				// we were unable to find the "best" match so raise an error
				// if possible, tell user what matches made the sig ambiguous.

				// System.out.println("match_list.size() is " +
				// match_list.size());

				StringBuffer sb = new StringBuffer(100);
				sb.append("ambiguous ");
				if (isConstructor) {
					sb.append("constructor");
				} else {
					sb.append("method");
				}
				sb.append(" signature");

				if (match_list.size() == 0) {

					// FIXME : better error message for no signature matches
					// case
					// We really should tell the user which methods we could
					// have
					// matched but did not for one reason or another. The tricky
					// part is knowing what matches should be shown, perhaps
					// all?

					// sb.append(" \"" + signature + "\"");
					sb.append(", could not choose between ");

					// Get all the signatures that match this name and number or
					// args

					if (isConstructor) {
						funcs = getAccessibleConstructors(cls);
					} else {
						if (isStatic)
							funcs = getAccessibleStaticMethods(cls);
						else {
							// funcs = getAccessibleInstanceMethods(cls);
							funcs = getAccessibleInstanceMethods(cls, methodName);
						}
					}

					for (i = 0; i < funcs.length; i++) {
						Class[] paramTypes;
						if (isConstructor) {
							paramTypes = ((Constructor) funcs[i]).getParameterTypes();
						} else {
							Method method = (Method) funcs[i];
							if (!methodName.equals(method.getName())) {
								continue;
							}
							foundSameName = true;

							paramTypes = method.getParameterTypes();
						}

						if (paramTypes.length == argv_count) {
							match_list.add(funcs[i]);
						}
					}

				} else {

					// iterate over remaining possible matches and add to error
					// message

					sb.append(", assignable signatures are ");

				}

				TclObject siglist = TclList.newInstance();
				siglist.preserve();

				for (i = 0; i < match_list.size(); i++) {
					TclObject cur_siglist = TclList.newInstance();
					cur_siglist.preserve();

					if (isConstructor) {
						Constructor con = ((Constructor) match_list.get(i));
						TclList.append(interp, cur_siglist, TclString.newInstance(con.getName()));

						// System.out.println("appending constructor name " +
						// con.getName());

						match_classes = con.getParameterTypes();
					} else {
						Method meth = ((Method) match_list.get(i));
						TclList.append(interp, cur_siglist, TclString.newInstance(meth.getName()));

						// System.out.println("appending method name " +
						// meth.getName());

						match_classes = meth.getParameterTypes();
					}

					for (j = 0; j < argv_count; j++) {
						Class c = match_classes[j];
						TclList.append(interp, cur_siglist, TclString.newInstance(JavaInfoCmd.getNameFromClass(c)));
						// System.out.println("appending class name " +
						// c.getName());
					}

					TclList.append(interp, siglist, cur_siglist);
					cur_siglist.release();
				}

				sb.append(siglist.toString());
				siglist.release();

				throw new TclException(interp, sb.toString());
			}

		} // end else if (match_list.size() > 1)

		// if we got to here then we could not find a matching method so raise
		// error

		if (isConstructor) {
			throw new TclException(interp, "can't find accessible constructor with " + argv_count
					+ " argument(s) for class \"" + JavaInfoCmd.getNameFromClass(cls) + "\"");
		} else {
			if (!foundSameName) {
				throw new TclException(interp, "no accessible" + (isStatic ? " static " : " ") + "method \""
						+ signature + "\" in class " + JavaInfoCmd.getNameFromClass(cls));
			} else {
				throw new TclException(interp, "can't find accessible" + (isStatic ? " static " : " ") + "method \""
						+ signature + "\" with " + argv_count + " argument(s) for class \""
						+ JavaInfoCmd.getNameFromClass(cls) + "\"");
			}
		}
	}

	// Helper method to recursively add interfaces of a class to the vector

	private static void addInterfaces(Class cls, ArrayList alist) {
		alist.add(cls);

		Class[] interfaces = cls.getInterfaces();

		for (int i = 0; i < interfaces.length; i++) {
			addInterfaces(interfaces[i], alist);
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getAccessibleConstructors --
	 * 
	 * Returns all constructors that can be invoked for a given class.
	 * 
	 * Results: An array of all the accessible constructors in the class.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static Constructor[] getAccessibleConstructors(Class cls) // The class to
	// query.
	{
		if (PkgInvoker.usesDefaultInvoker(cls)) {
			return cls.getConstructors();
		} else {
			Constructor[] constructors = cls.getDeclaredConstructors();
			ArrayList alist = null;
			boolean skipped_any = false;

			for (int i = 0; i < constructors.length; i++) {
				Constructor c = constructors[i];
				if (PkgInvoker.isAccessible(c)) {
					if (alist == null) {
						alist = new ArrayList(constructors.length);
					}
					alist.add(c);
				} else {
					skipped_any = true;
				}
			}

			if (skipped_any) {
				if (alist == null) {
					constructors = new Constructor[0];
				} else {
					constructors = new Constructor[alist.size()];
					for (int i = 0; i < constructors.length; i++) {
						constructors[i] = (Constructor) alist.get(i);
					}
				}
			}
			return constructors;
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getAccessibleConstructor --
	 * 
	 * Returns an accessable constructors for the given class that accepts the
	 * given arguments.
	 * 
	 * Results: A constructor object, raises a NoSuchMethodException if an
	 * accessible constructor cannot be found.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static Constructor getAccessibleConstructor(Class cls, // The class to
			// query.
			Class[] parameterTypes) // The constructor arguments types
			throws NoSuchMethodException {
		if (PkgInvoker.usesDefaultInvoker(cls)) {
			return cls.getConstructor(parameterTypes);
		} else {
			Constructor constructor = cls.getDeclaredConstructor(parameterTypes);
			if (!PkgInvoker.isAccessible(constructor)) {
				throw new NoSuchMethodException();
			}
			return constructor;
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getAccessibleInstanceMethods --
	 * 
	 * Returns all instance methods that can be invoked for a given class.
	 * 
	 * Results: An array of all the accessible instance methods in the class and
	 * the superclasses of the class. If a method is overloaded, only the
	 * "most public" instance of that method is included in the array. A method
	 * is considered accessible if it has public access or if it does not have
	 * private access and the package has a custom PkgInvoker. See comments
	 * above the "func" member variable for more details.
	 * 
	 * Side effects: The array of methods are saved in a hashtable for faster
	 * access in the future.
	 * 
	 * ----------------------------------------------------------------------
	 */
	static Method[] getAccessibleInstanceMethods(Class cls, String name) {
		getAccessibleInstanceMethods(cls);
		Method[] methods = instanceMethodTableByName.get(cls).get(name);
		if (methods == null) {
			return ZERO_METHODS;
		}
		return methods;
	}

	static Method[] getAccessibleInstanceMethods(Class cls) // The class to
	// query.
	{
		Method[] methods = (Method[]) instanceMethodTable.get(cls);
		if (methods != null) {
			return methods;
		}

		// Avoid using Class.getMethods() because it includes
		// static members and it does not account for interfaces
		// which need to include methods from the Object class.

		ArrayList alist = new ArrayList();

		for (Class c = cls; c != null;) {
			methods = c.getDeclaredMethods();
			mergeInstanceMethods(c, methods, alist);

			Class interfaces[] = c.getInterfaces();
			for (int i = 0; i < interfaces.length; i++) {
				mergeInstanceMethods(interfaces[i], interfaces[i].getMethods(), alist);
			}

			if (c.isInterface()) {
				c = Object.class; // if cls is an interface add Object methods
			} else {
				c = c.getSuperclass();
			}
		}

		sortMethods(alist);
		methods = new Method[alist.size()];
		HashMap<String, ArrayList<Method>> map = new HashMap<String, ArrayList<Method>>();

		for (int i = 0; i < methods.length; i++) {
			methods[i] = (Method) alist.get(i);
			String name = methods[i].getName();
			ArrayList<Method> lst = map.get(name);
			if (lst == null)
				map.put(name, lst = new ArrayList<Method>());
			lst.add(methods[i]);
		}

		HashMap<String, Method[]> mapT = new HashMap<String, Method[]>();
		for (Entry<String, ArrayList<Method>> entry : map.entrySet()) {
			mapT.put(entry.getKey(), entry.getValue().toArray(new Method[0]));
		}
		instanceMethodTableByName.put(cls, mapT);

		instanceMethodTable.put(cls, methods);

		return methods;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getAccessibleStaticMethods --
	 * 
	 * Returns all static methods that can be invoked for a given class.
	 * 
	 * Results: An array of all the accessible static methods in the class. A
	 * method is considered accessible if it has public access or if it is not
	 * private and the package has a custom PkgInvoker. See comments above the
	 * "func" member variable for more details.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static Method[] getAccessibleStaticMethods(Class cls) // The class to query.
	{
		Method[] methods = (Method[]) staticMethodTable.get(cls);
		if (methods != null) {
			return methods;
		}

		// When searching for static methods in this class,
		// call getDeclaredMethods() and filter out those
		// methods that are not static or are not accessible.
		// This should be quicker than calling getMethods()
		// since that returns both static and instance methods
		// for the class and its superclasses.

		methods = cls.getDeclaredMethods();
		ArrayList alist = new ArrayList();

		for (int i = 0; i < methods.length; i++) {
			Method m = methods[i];
			if (Modifier.isStatic(m.getModifiers()) && PkgInvoker.isAccessible(m)) {
				alist.add(m);
			}
		}

		sortMethods(alist);
		methods = new Method[alist.size()];
		for (int i = 0; i < methods.length; i++) {
			methods[i] = (Method) alist.get(i);
		}
		staticMethodTable.put(cls, methods);

		return methods;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * mergeInstanceMethods --
	 * 
	 * Add instance methods declared by a super-class or an interface to the
	 * list of accessible instance methods.
	 * 
	 * Results: None.
	 * 
	 * Side effects: Elements of methods[] are added to vec. If an instance of
	 * an overloaded method is already in vec, it will be replaced by a new
	 * instance only if the new instance has a higher rank.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private static void mergeInstanceMethods(Class c, Method methods[], ArrayList alist) {
		for (int i = 0; i < methods.length; i++) {
			boolean sameSigExists = false;
			Method newMeth = methods[i];

			if (newMeth == null)
				continue;

			// Don't merge static methods or inaccessible methods
			if (Modifier.isStatic(newMeth.getModifiers()) || !PkgInvoker.isAccessible(newMeth)) {
				continue;
			}
			
			// Don't merge methods whose return type is not accessible. issue #9
			if (! PkgInvoker.isAccessible(newMeth.getReturnType())) {
				continue;
			}

			for (int j = 0; j < alist.size(); j++) {
				Method oldMeth = (Method) alist.get(j);

				if (methodSigEqual(oldMeth, newMeth)) {
					sameSigExists = true;

					Class oldCls = oldMeth.getDeclaringClass();
					int newRank = getMethodRank(c, newMeth);
					int oldRank = getMethodRank(oldCls, oldMeth);
					
					if (newRank > oldRank) {
						alist.set(j, newMeth);
					} else if (newRank == oldRank) {
						// if ranks are equal, replace if newMeth is more specific, i.e., has
						// more parents in class hierarchy.  issue #9
						int newHier = getParents(newMeth.getDeclaringClass());
						int oldHier = getParents(oldMeth.getDeclaringClass());
						if (newHier > oldHier) {
							alist.set(j, newMeth);
						}
					}
					break;
				}
			}

			if (!sameSigExists) {
				// We copy a method into the list only if no method
				// with the same signature is already in the list.
				// Otherwise the matching routine in the get()
				// procedure may run into "ambiguous method signature"
				// errors when it sees instances of overloaded
				// methods.

				alist.add(newMeth);
			}
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * methodSigEqual --
	 * 
	 * Returns whether the two methods have the same signature.
	 * 
	 * Results: True if the method names and arguments are the same. False
	 * otherwise
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private static boolean methodSigEqual(Method method1, Method method2) {
		if (!method1.getName().equals(method2.getName())) {
			return false;
		}

		Class param1[] = method1.getParameterTypes();
		Class param2[] = method2.getParameterTypes();

		if (param1.length != param2.length) {
			return false;
		}

		for (int i = 0; i < param1.length; i++) {
			if (param1[i] != param2[i]) {
				return false;
			}
		}

		return true;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * sortMethods --
	 * 
	 * This method will sort a list of Method objects. We need to sort the
	 * methods so that the order of the methods does not depend on the order the
	 * methods as returned by the JVM.
	 * 
	 * Results: None.
	 * 
	 * Side effects: The order of the elements in the List is changed.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private static void sortMethods(ArrayList alist) {
		final boolean debug = false; // set to true for debug output
		int insize = alist.size();

		if (debug) {
			System.out.println("Pre sort dump");
			for (int i = 0; i < alist.size(); i++) {
				Method m = (Method) alist.get(i);
				System.out.println("Method " + i + " is \t\"" + getMethodDescription(m) + "\"");
			}
		}

		for (int i = 1; i < alist.size(); i++) {
			int c = i;
			Method cm = (Method) alist.get(c);
			String cms = getMethodDescription(cm);

			// loop down array swapping elements into sorted order

			for (int j = c - 1; j >= 0; j--) {
				Method jm = (Method) alist.get(j);
				String jms = getMethodDescription(jm);

				if (debug) {
					System.out.println("checking \"" + cms + "\" from index " + c);
					System.out.println("against  \"" + jms + "\" from index " + j);
					System.out.println("compareTo() is " + cms.compareTo(jms));
				}

				if (cms.compareTo(jms) <= 0) {
					if (debug) {
						System.out.println("swapping " + c + " and " + j);
					}

					// Swap the Methods at c and j
					alist.set(c, jm);
					alist.set(j, cm);

					// Current becomes the value of j for next loop
					c = j;
					// cm = jm;
					// cms = jms;

				} else {
					if (debug) {
						System.out.println("no swap at index " + j);
					}
					break;
				}
			}
		}

		if (debug) {
			System.out.println("Post sort dump");
			for (int i = 0; i < alist.size(); i++) {
				Method m = (Method) alist.get(i);
				System.out.println("Method " + i + " is \t\"" + getMethodDescription(m) + "\"");
			}
		}

		if (insize != alist.size()) {
			throw new RuntimeException("lost elements");
		}
		return;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getMethodDescription --
	 * 
	 * This helper method will return a string description of a method and the
	 * arguments and return type of the methos. This helper is only called by
	 * sortMethods().
	 * 
	 * Results: None.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */
	private static String getMethodDescription(Method m) {
		StringBuffer sb = new StringBuffer(50);

		sb.append(m.getName());

		Class[] params = m.getParameterTypes();

		sb.append('(');

		for (int i = 0; i < params.length; i++) {

			sb.append(JavaInfoCmd.getNameFromClass(params[i]));

			if (i < (params.length - 1)) {
				sb.append(", ");
			}
		}

		sb.append(") returns ");

		Class ret = m.getReturnType();

		sb.append(JavaInfoCmd.getNameFromClass(ret));

		return sb.toString();
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getMethodRank --
	 * 
	 * Returns the rank of "public-ness" of the method. See comments above the
	 * "func" member variable for more details on public-ness ranking.
	 * 
	 * Results: The rank of "public-ness" of the method.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private static int getMethodRank(Class declaringCls, // The class that
			// declares the
			// method.
			Method method) // Return the rank of this method.
	{
		int methMod = method.getModifiers();

		if (Modifier.isPrivate(methMod)) {
			return 0;
		}

		int clsMod = declaringCls.getModifiers();

		if (Modifier.isPublic(methMod) && Modifier.isPublic(clsMod)) {
			return 2;
		}

		return 0;
	}
	
	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getParents --
	 * 
	 * Returns the number of parent super classes for a given class.
	 * 
	 * Results: number of parent classes Plus 1 (for self).
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */
	private static int getParents(Class c) {
		Class parent = c.getSuperclass();
		if (parent == null) {
			return 1;
		} else {
			return 1 + getParents(parent);
		}
	}

} // end FuncSig.

