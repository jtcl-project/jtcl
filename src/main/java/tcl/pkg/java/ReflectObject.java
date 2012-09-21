/*
 * ReflectObject.java --
 *
 *	Implements the Tcl internal representation of Java
 *	reflection object.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 * RCS: @(#) $Id: ReflectObject.java,v 1.19 2006/04/13 07:36:50 mdejong Exp $
 *
 */

package tcl.pkg.java;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;

import tcl.lang.Command;
import tcl.lang.CommandWithDispose;
import tcl.lang.InternalRep;
import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;
import tcl.lang.TclRuntimeError;
import tcl.pkg.java.reflect.PkgInvoker;

/**
 * A ReflectObject is used to create and access arbitrary Java objects using the
 * Java Reflection API. It wraps around a Java object (i.e., an instance of any
 * Java class) and expose it to Tcl scripts. The object is registered inside the
 * interpreter and is given a string name. Tcl scripts can manipulate this
 * object as long as the the reference count of the object is greater than zero.
 */

public class ReflectObject implements InternalRep, CommandWithDispose {

	// The java.lang.Object wrapped by the ReflectObject representation.

	Object javaObj;
	Class javaClass;

	// The interpreter in which the java.lang.Object is registered in.
	// ReflectObject's are not shared among interpreters for safety
	// reasons.

	Interp ownerInterp;

	// The reference ID of this object. (same as instance command name)

	String refID;

	// This variables records how many TclObject's are using
	// this ReflectObject internal rep. In this example:
	//
	// set x [new java.lang.Integer 1]
	// set y [format %s $x]
	// java::info methods $y
	//
	// The two objects $x and $y share the same ReflectObject instance.
	// useCount is 2 when the java::info command has just translated the
	// string $y into a ReflectObject.
	//
	// useCount is initially 1. It will be more than 1 only when the
	// script tries to refer to the object using its string form, or when
	// the same object is returned by the Reflection API more than once.
	//
	// This variable is called useCount rather than refCount to avoid
	// confusion with TclObject.refCount.

	private int useCount;

	// This variable marks whether the object is still considered "valid"
	// in Tcl scripts. An object is no longer valid if its object command
	// has been explicitly deleted from the interpreter.

	private boolean isValid;

	// Stores the bindings of this ReflectObject. This member variable is used
	// in the BeanEventMgr class.

	Hashtable bindings;

	// the string representation of the null reflect object

	private static final String NULL_REP = "java0x0";

	/*
	 * 
	 * // this really should be final but there is a bug in Sun's javac which //
	 * incorrectly flags this as a "final not initialized" error //private
	 * static final ReflectObject NULL_OBJECT;
	 * 
	 * private static ReflectObject NULL_OBJECT;
	 * 
	 * 
	 * // Allocate single object used to represent the untyped null java //
	 * Object. A null object is not registered (hence it can't be deleted).
	 * static { NULL_OBJECT = makeNullObject(null, null); }
	 */

	protected static final String NOCONVERT = "-noconvert";

	protected static final String CMD_PREFIX = "java0x";

	// set to true to see extra output

	private static final boolean debug = false;

	// set to true to see dump the relfect table
	// we adding or removing an object from the table

	private static final boolean dump = false;

	// Private helper for creating reflected null objects

	private static ReflectObject makeNullObject(Interp i, Class c) {
		ReflectObject ro = new ReflectObject();

		ro.ownerInterp = i;

		ro.refID = NULL_REP;
		ro.useCount = 1;
		ro.isValid = true;

		ro.javaObj = null;
		ro.javaClass = c;

		return ro;
	}

	// Return the string used to hash this Java object into the reflect table
	// or the conflict table (in case of a duplicate hash in reflect table.
	// For example: java.lang.Object.546464 -> ReflectObject

	private static String getHashString(Class cl, Object obj) {
		StringBuffer buff = new StringBuffer();
		buff.append(JavaInfoCmd.getNameFromClass(cl));
		buff.append('.');

		// A bad hash would suck in terms of performance but it should not
		// generate any errors. Use '1' for an extreme test of this case.
		// buff.append('1');
		buff.append(System.identityHashCode(obj));

		return buff.toString();
	}

	// Private helper used to add a reflect object to the reflect table

	private static void addToReflectTable(ReflectObject roRep) {
		Interp interp = roRep.ownerInterp;
		Class cl = roRep.javaClass;
		Object obj = roRep.javaObj;
		String id = roRep.refID;

		String hash = getHashString(cl, obj);
		ReflectObject found = (ReflectObject) interp.reflectObjTable.get(hash);

		if (found == null) {
			// There was no mapping for this hash value, add one now.

			if (debug) {
				System.out.println("new reflect table entry for " + id
						+ " with hash \"" + hash + "\"");
			}

			interp.reflectObjTable.put(hash, roRep);
		} else {
			// If there is already an entry for this hash value, it means
			// that there are two different objects of the same class that
			// have the same hash value. In this case, add the ReflectObject
			// to the conflict table for this hash value.
			// java.lang.Object.546464 -> {ReflectObject ReflectObject
			// ReflectObject}

			if (debug) {
				System.out.println("hash conflict in reflect table for " + id
						+ " with hash \"" + hash + "\"");
			}

			ArrayList conflicts = (ArrayList) interp.reflectConflictTable
					.get(hash);

			if (conflicts == null) {
				conflicts = new ArrayList();
				interp.reflectConflictTable.put(hash, conflicts);
			}

			if (debug) {
				if (conflicts.contains(roRep)) {
					throw new TclRuntimeError(
							"the conflict table already contains the ReflectObject");
				}
			}

			conflicts.add(roRep);
		}
	}

	// Private helper used to remove a reflected object from the reflect table.

	private static void removeFromReflectTable(ReflectObject roRep) {
		Interp interp = roRep.ownerInterp;
		Class cl = roRep.javaClass;
		Object obj = roRep.javaObj;
		String id = roRep.refID;

		String hash = getHashString(cl, obj);
		ReflectObject found = (ReflectObject) interp.reflectObjTable.get(hash);

		// This should never happen
		if (found == null) {

			throw new TclRuntimeError("reflect table returned null for " + id
					+ " with hash \"" + hash + "\"");
		} else {

			// In the first case, the ReflectObject we hashed to is the same as
			// the
			// one we passed in, we can just remove it from the reflect table.
			// Be careful to also check the conflict table, if there is 1 entry
			// in the conflict table then toast the conflict table and remap the
			// reflect object in the conflict table. Otherwise, grab the first
			// value out of the conflict table and hash that in the reflect
			// table.

			if (found == roRep) {
				interp.reflectObjTable.remove(hash);

				if (debug) {
					System.out.println("removing reflect table entry " + hash);
				}

				ArrayList conflicts = (ArrayList) interp.reflectConflictTable
						.get(hash);

				if (conflicts != null) {
					Object first = conflicts.remove(0);

					if (conflicts.isEmpty()) {
						interp.reflectConflictTable.remove(hash);
					}

					interp.reflectObjTable.put(hash, first);

					if (debug) {
						System.out
								.println("replaced reflect table entry from conflict "
										+ hash);
					}
				}
			} else {

				// In the second case, the ReflectObject we hashed to did not
				// match
				// the entry in the reflect table. This means it must be in the
				// conflict table so remove it from there. Be sure to remove the
				// conflict table mapping if we are removing the last conflict!

				ArrayList conflicts = (ArrayList) interp.reflectConflictTable
						.get(hash);

				// This should never happen!

				if (conflicts == null) {
					throw new TclRuntimeError(
							"conflict table mapped to null for " + id
									+ " with hash \"" + hash + "\"");
				}

				if (debug) {
					System.out.println("removing conflict table entry for "
							+ id + " with hash \"" + hash + "\"");
				}

				// FIXME: double check that this uses == compare
				// This remove should never fail!

				int index = conflicts.indexOf(roRep);
				if (index == -1) {
					throw new TclRuntimeError("no entry in conflict table for "
							+ id + " with hash \"" + hash + "\"");
				}
				conflicts.remove(index);

				if (conflicts.isEmpty()) {
					interp.reflectConflictTable.remove(hash);
				}
			}
		}
	}

	// Find in ConflictTable will search the reflect hash conflict table
	// for a given {Class Object} pair. If the pair exists its ReflectObject
	// will be returned. If not, null will be returned. In the case where
	// we want to add a new object that conflicts, the table will not
	// exists yet so we can't find a match.

	private static ReflectObject findInConflictTable(Interp interp, Object obj,
			String hash) {
		ArrayList conflicts = (ArrayList) interp.reflectConflictTable.get(hash);

		if (conflicts == null) {
			return null;
		}

		for (ListIterator iter = conflicts.listIterator(); iter.hasNext();) {
			ReflectObject found = (ReflectObject) iter.next();
			if (found.javaObj == obj) {
				if (debug) {
					System.out.println("found conflict table entry for hash "
							+ hash);
				}

				return found;
			}
		}

		return null;
	}

	// Find in ReflectTable will search the reflect table for a given
	// {Class Object} pair. If the pair exists its ReflectObject
	// will be returned. If not, null will be returned.

	private static ReflectObject findInReflectTable(Interp interp, Class cl,
			Object obj) {
		String hash = getHashString(cl, obj);
		ReflectObject found = (ReflectObject) interp.reflectObjTable.get(hash);

		// If there is no mapping in the reflect table for this object, return
		// null

		if (found == null) {
			if (debug) {
				System.out.println("could not find reflect object for hash \""
						+ hash + "\"");
			}

			return null;
		} else {
			// If we find a mapping in the reflect table there are two cases
			// we need to worry about. If the object we mapped to is the same
			// object as the one we are have, then just return it. In the
			// case where the object we map to is not the same, we need
			// to look in the reflect table because it could be in there.

			if (found.javaObj == obj) {
				if (debug) {
					System.out.println("found reflect table match for id "
							+ found.refID + " with hash \"" + hash + "\"");
				}

				return found;
			} else {
				return findInConflictTable(interp, obj, hash);
			}
		}
	}

	// This method is only used for debugging, it will dump the contents of the
	// reflect table in a human readable form. The dump is to stdout.

	// dump the table from a Tcl/Java shell like this
	// set i [java::getinterp]
	// java::call tcl.lang.ReflectObject dump $i

	public static void dump(Interp interp) {
		try {
			System.out.println("BEGIN DUMP -------------------------------");
			System.out.println("interp.reflectObjCount = "
					+ interp.reflectObjCount);
			System.out.println("interp.reflectObjTable.size() = "
					+ interp.reflectObjTable.size());
			System.out.println("interp.reflectConflictTable.size() = "
					+ interp.reflectConflictTable.size());

			// Loop over the entries in the reflectObjTable and dump them out.

			for (Iterator iter = interp.reflectObjTable.entrySet().iterator(); iter
					.hasNext();) {
				Map.Entry entry = (Map.Entry) iter.next();

				System.out.println();
				String hash = (String) entry.getKey();
				ReflectObject roRep = (ReflectObject) entry.getValue();

				if (roRep == null) {
					throw new RuntimeException("Reflect table entry \"" + hash
							+ "\" hashed to null");
				}

				// do sanity check
				if (roRep.ownerInterp != interp) {
					throw new RuntimeException(
							"roRep.ownerInterp not the same as current interp");
				}

				// Check to see if the command is in the Tcl command table.
				if (interp.getCommand(roRep.refID) == null) {
					System.out.println("could not find command named \""
							+ roRep.refID + "\"");
				}

				String hash2 = getHashString(roRep.javaClass, roRep.javaObj);

				if (!hash.equals(hash2)) {
					throw new RuntimeException("hash \"" + hash
							+ "\" is not equal to calculated" + " hash \""
							+ hash2);
				}

				System.out.println("hash \"" + hash
						+ "\" corresponds to ReflectObject with " + "refID \""
						+ roRep.refID + "\" useCount = \"" + roRep.useCount
						+ "\" isValid = \"" + roRep.isValid + "\""
						+ " javaClass = \""
						+ JavaInfoCmd.getNameFromClass(roRep.javaClass) + "\""
						+ " System.identityHashCode(javaObj) = \""
						+ System.identityHashCode(roRep.javaObj) + "\"");

				ArrayList conflicts = (ArrayList) interp.reflectConflictTable
						.get(hash);

				if (conflicts != null) {
					System.out.println("Found conflict table for hash " + hash);

					for (ListIterator iter2 = conflicts.listIterator(); iter
							.hasNext();) {
						ReflectObject found = (ReflectObject) iter2.next();

						System.out.println("hash conflict for \"" + hash
								+ "\" corresponds to ReflectObject with "
								+ "refID \"" + found.refID + "\"");
					}
				}
			}
		} catch (Throwable e) {
			e.printStackTrace(System.out);
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * makeReflectObject --
	 * 
	 * Wraps an Java Object in a ReflectObject. If the same Java Object has
	 * already been wrapped in a ReflectObject, return that ReflectObject.
	 * Otherwise, create a new ReflectObject to wrap the Java Object.
	 * 
	 * Results: None.
	 * 
	 * Side effects: The object is unregistered (and thus no longer accessible
	 * from Tcl scripts) if no other if no other TclObjects are still using this
	 * internal rep.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private static ReflectObject makeReflectObject(Interp interp, Class cl,
			Object obj) throws TclException // if a null class with a non null
	// object is passed in
	{
		// final boolean debug = false;

		if (cl != null && !PkgInvoker.isAccessible(cl)) {
			JavaInvoke.notAccessibleError(interp, cl);
		}

		if (obj == null) {
			// this is the null reflect object case

			if (debug) {
				System.out.println("null object");
			}

			if (debug && (cl != null)) {
				System.out.println("non null class with null object");
				System.out.println("non null class was " + cl);
			}

			// null objects are not added to the reflect table like other
			// instances

			return makeNullObject(interp, cl);
		}

		if (cl == null) {
			// we have no way to deal with a non null object that has a
			// null class reference type, we must give up in this case

			throw new TclException(interp,
					"non null reflect object with null class is not valid");
		}

		// apply builtin type conversion rules (so int becomes
		// java.lang.Integer)

		if (cl == Integer.TYPE)
			cl = Integer.class;
		else if (cl == Boolean.TYPE)
			cl = Boolean.class;
		else if (cl == Long.TYPE)
			cl = Long.class;
		else if (cl == Float.TYPE)
			cl = Float.class;
		else if (cl == Double.TYPE)
			cl = Double.class;
		else if (cl == Byte.TYPE)
			cl = Byte.class;
		else if (cl == Short.TYPE)
			cl = Short.class;
		else if (cl == Character.TYPE)
			cl = Character.class;
		else if (cl == Void.TYPE)
			throw new TclException(interp,
					"void object type can not be reflected");

		if (debug) {
			System.out.println("object will be reflected as "
					+ JavaInfoCmd.getNameFromClass(cl));
		}

		// Try to find this {Class Object} pair in the reflect table.

		ReflectObject roRep = findInReflectTable(interp, cl, obj);

		if (roRep != null && roRep.isValid) {
			// If it is already in the table just increment the use count and
			// return it

			roRep.useCount++;

			if (debug) {
				System.out.println("incr useCount of found object "
						+ roRep.refID + " to " + roRep.useCount);
			}

			return roRep;
		} else {
			if (cl.isArray()) {
				roRep = new ArrayObject();
			} else {
				roRep = new ReflectObject();
			}

			roRep.ownerInterp = interp;
			roRep.javaObj = obj;
			roRep.javaClass = cl;

			// make sure the object can be represented by the given Class
			Class obj_class = roRep.javaObj.getClass();

			if (!roRep.javaClass.isAssignableFrom(obj_class)) {
				throw new TclException(interp, "object of type "
						+ JavaInfoCmd.getNameFromClass(obj_class)
						+ " can not be referenced as type "
						+ JavaInfoCmd.getNameFromClass(roRep.javaClass));
			}

			if (dump) {
				System.out.println("PRE REGISTER DUMP");
				dump(interp);
			}

			// Register the object in the interp.

			interp.reflectObjCount++; // incr id, the first id used will be 1
			roRep.refID = CMD_PREFIX + Long.toHexString(interp.reflectObjCount);

			interp.createCommand(roRep.refID, roRep);
			addToReflectTable(roRep);

			if (debug) {
				System.out.println("reflect object " + roRep.refID
						+ " of type "
						+ JavaInfoCmd.getNameFromClass(roRep.javaClass)
						+ " registered");
			}

			roRep.useCount = 1;
			roRep.isValid = true;

			if (dump) {
				System.out.println("POST REGISTER DUMP");
				dump(interp);
			}

			return roRep;
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * dispose --
	 * 
	 * Called when a TclObject no longers uses this internal rep. We unregister
	 * the java.lang.Object if no other TclObjects are still using this internal
	 * rep.
	 * 
	 * Results: None.
	 * 
	 * Side effects: The object is unregistered (and thus no longer accessible
	 * from Tcl scripts) if no other if no other TclObjects are still using this
	 * internal rep.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public void dispose() {
		if (debug) {
			System.out.println("dispose called for reflect object " + refID);
		}

		useCount--;
		if ((useCount == 0) && (refID != NULL_REP)) {
			// No TclObject is using this internal rep anymore. Free it.

			if (debug) {
				System.out.println("reflect object " + refID
						+ " is no longer being used");
			}

			if (dump) {
				System.out.println("PRE DELETE DUMP");
				dump(ownerInterp);
			}

			// Don't delete command if interp was already deleted
			if (isValid)
				ownerInterp.deleteCommand(refID);
			removeFromReflectTable(this);

			ownerInterp = null;
			javaObj = null;
			javaClass = null;
			bindings = null;
			refID = NULL_REP;
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * duplicate --
	 * 
	 * Get a copy of this ReflectObject for copy-on-write operations. We just
	 * increment its useCount and return the same ReflectObject because
	 * ReflectObject's cannot be modified, so they don't need copy-on-write
	 * protections.
	 * 
	 * Results: The same internal rep.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public InternalRep duplicate() {
		useCount++;

		if (debug) {
			System.out.println("duplicate(): incr useCount of " + refID
					+ " to " + useCount);
		}

		return this;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * setReflectObjectFromAny --
	 * 
	 * Called to convert an TclObject's internal rep to ReflectObject.
	 * 
	 * Results: None.
	 * 
	 * Side effects: When successful, the internal representation of tobj is
	 * changed to ReflectObject, if it is not already so.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private static void setReflectObjectFromAny(Interp interp, // Current
			// interpreter.
			// Must be
			// non-null.
			TclObject tobj) // The TclObject to convert.
			throws TclException // If the object's internal rep is not
	// already ReflectObject, and the string rep
	// is not the name of a java.lang.Object
	// registered in the given interpreter.
	// Error message is left inside interp.
	{
		InternalRep rep = tobj.getInternalRep();
		ReflectObject roRep;

		if (rep instanceof ReflectObject) {
			roRep = (ReflectObject) rep;
			if (roRep.isValid && (roRep.ownerInterp == interp)) {
				return;
			}
		}

		String s = tobj.toString();
		if (s.startsWith(CMD_PREFIX)) {
			if (s.equals(NULL_REP)) {
				tobj.setInternalRep(makeReflectObject(interp, null, null));
				return;
			} else {
				Command cmd = interp.getCommand(s);
				if ((cmd != null) && (cmd instanceof ReflectObject)
						&& ((ReflectObject) cmd).isValid) {
					roRep = (ReflectObject) cmd;
					roRep.useCount++;

					if (debug) {
						System.out
								.println("setReflectObjectFromAny(): incr useCount of "
										+ roRep.refID + " to " + roRep.useCount);
					}

					tobj.setInternalRep(roRep);
					return;
				}
			}
		}

		throw new TclException(interp, "unknown java object \"" + tobj + "\"");
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * newInstance --
	 * 
	 * Creates a new instance of a TclObject that wraps a java.lang.Object.
	 * 
	 * Results: The newly created TclObject.
	 * 
	 * Side effects: The java.lang.Object will be registered in the interpreter.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public static TclObject newInstance(Interp interp, // Current interpreter.
			Class cl, // class of the reflect instance
			Object obj) // java.lang.Object to wrap.
			throws TclException {
		return new TclObject(makeReflectObject(interp, cl, obj));
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * get --
	 * 
	 * Returns a java.lang.Object represented by tobj. tobj must have a
	 * ReflectObject internal rep, or its string rep must be one of the
	 * currently registered objects.
	 * 
	 * Results: The Java object represented by tobj.
	 * 
	 * Side effects: When successful, the internal representation of tobj is
	 * changed to ReflectObject, if it is not already so.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public static Object get(Interp interp, // Current interpreter. Must be
			// non-null.
			TclObject tobj) // The TclObject to query.
			throws TclException // If the internal rep of tobj cannot
	// be converted to a ReflectObject.
	// Error message is left inside interp.
	{
		setReflectObjectFromAny(interp, tobj);
		ReflectObject rep = (ReflectObject) tobj.getInternalRep();
		return rep.javaObj;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getClass --
	 * 
	 * Returns a java.lang.Class object that is the ref type of this reflect
	 * object. This is not always the same class as is returned by a call to
	 * ((Object) o).getClass().
	 * 
	 * Results: The Java class object used to reference tobj.
	 * 
	 * Side effects: When successful, the internal representation of tobj is
	 * changed to ReflectObject, if it is not already so.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public static Class getClass(Interp interp, // Current interpreter. Must be
			// non-null.
			TclObject tobj) // The TclObject to query.
			throws TclException // If the internal rep of tobj cannot
	// be converted to a ReflectObject.
	// Error message is left inside interp.
	{
		setReflectObjectFromAny(interp, tobj);
		ReflectObject rep = (ReflectObject) tobj.getInternalRep();
		return rep.javaClass;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getReflectObject --
	 * 
	 * Returns the InternalRep of a the ReflectObject represented by tobj. Only
	 * the java:: commands should call this method. (java::bind, java::call,
	 * etc).
	 * 
	 * Results: The Java object represented by tobj.
	 * 
	 * Side effects: When successful, the internal representation of tobj is
	 * changed to ReflectObject, if it is not already so.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static ReflectObject getReflectObject(Interp interp, // Current interpreter.
			// Must be non-null
			TclObject tobj) // The TclObject to query.
			throws TclException // If the internal rep of tobj cannot
	// be converted to a ReflectObject.
	// Error message is left inside interp.
	{
		setReflectObjectFromAny(interp, tobj);
		return (ReflectObject) tobj.getInternalRep();
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * cmdProc --
	 * 
	 * This cmdProc implements the Tcl command used to invoke methods of the
	 * java.lang.Object stored in this ReflectObject internal rep. For example,
	 * this method is called to process the "$v" command at the second line of
	 * this script:
	 * 
	 * set v [java::new java.util.Vector] $v addElement "foo"
	 * 
	 * Results: None.
	 * 
	 * Side effects: If the given method returns a value, it is converted into a
	 * TclObject and stored as the result of the interpreter.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public void cmdProc(Interp interp, // Current interpreter.
			TclObject[] argv) // Argument list.
			throws TclException // Standard Tcl exception;
	{
		boolean convert;
		int sigIdx;

		if (!isValid) {
			throw new TclException(interp,
					"reflected object is no longer valid");
		}

		if (argv.length < 2) {
			throw new TclNumArgsException(interp, 1, argv,
					"?-noconvert? signature ?arg arg ...?");
		}

		String arg1 = argv[1].toString();
		if ((arg1.length() >= 2) && (NOCONVERT.startsWith(arg1))) {
			convert = false;
			sigIdx = 2;
		} else {
			convert = true;
			sigIdx = 1;
		}

		if (argv.length < sigIdx + 1) {
			throw new TclNumArgsException(interp, 1, argv,
					"?-noconvert? signature ?arg arg ...?");
		}

		int startIdx = sigIdx + 1;
		int count = argv.length - startIdx;

		TclObject result = JavaInvoke.callMethod(interp, argv[0], argv[sigIdx],
				argv, startIdx, count, convert);

		if (result == null)
			interp.resetResult();
		else
			interp.setResult(result);
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * disposeCmd --
	 * 
	 * This method is called when the object command has been deleted from an
	 * interpreter. It marks the ReflectObject no longer accessible from Tcl
	 * scripts.
	 * 
	 * Results: None.
	 * 
	 * Side effects: The ReflectObject is no longer accessible from Tcl scripts.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public void disposeCmd() {
		if (debug) {
			System.out.println("ReflectObject instance " + refID
					+ " -> disposedCmd()");
		}

		isValid = false;
		dispose();
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * toString --
	 * 
	 * Called to query the string representation of the Tcl object. This method
	 * is called only by TclObject.toString() when TclObject.stringRep is null.
	 * 
	 * Results: Returns the string representation of this ReflectObject.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public String toString() {
		return refID;
	}

} // end ReflectObject

