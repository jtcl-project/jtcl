/*
 * JavaImportCmd.java --
 *
 *	This class implements the java::import command which is used
 *	to indicate which classes will be imported. An imported class
 *	simply means that we can use the class name insteead of the
 *	full pkg.class name.
 *
 * Copyright (c) 1999 Mo DeJong.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 * RCS: @(#) $Id: JavaImportCmd.java,v 1.7 2006/04/13 07:36:50 mdejong Exp $
 *
 */

package tcl.pkg.java;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.PackageNameException;
import tcl.lang.TclClassLoader;
import tcl.lang.TclException;
import tcl.lang.TclList;
import tcl.lang.TclObject;
import tcl.lang.TclRuntimeError;
import tcl.lang.TclString;
import tcl.pkg.java.reflect.PkgInvoker;

public class JavaImportCmd implements Command {

	/**
	 * This procedure is invoked to process the "java::import" Tcl comamnd. See
	 * the user documentation for details on what it does.
	 * 
	 * @see tcl.lang.Command#cmdProc(tcl.lang.Interp, tcl.lang.TclObject[])
	 */
	public void cmdProc(Interp interp, TclObject[] objv) throws TclException {

		final String usage = "java::import ?-forget? ?-package pkg? ?class ...?";

		HashMap classTable = interp.importTable[0];
		HashMap packageTable = interp.importTable[1];
		HashMap wildcardTable = interp.importTable[2];

		boolean forget = false;
		String pkg = null;
		TclObject import_list;
		String elem, elem2;
		int startIdx, i;

		// If there are no args simply return all the imported classes
		if (objv.length == 1) {
			import_list = TclList.newInstance();

			for (Iterator iter = classTable.entrySet().iterator(); iter.hasNext();) {
				Map.Entry entry = (Map.Entry) iter.next();
				String key = (String) entry.getKey();
				String value = (String) entry.getValue();
				TclList.append(interp, import_list, TclString.newInstance(value));
			}
			ArrayList wildcardList = (ArrayList) wildcardTable.get("*");
			if (wildcardList != null) {
				for (Iterator iter = wildcardList.iterator(); iter.hasNext(); ) {
					String wildcardPkg = (String) iter.next();
					TclList.append(interp, import_list, TclString.newInstance(wildcardPkg + ".*"));
				}
			}
			interp.setResult(import_list);
			return;
		}

		// See if there is a -forget argument
		startIdx = 1;
		elem = objv[startIdx].toString();
		if (elem.equals("-forget")) {
			forget = true;
			startIdx++;
		}

		// When -forget is given with no arguments, we simply
		// return. This is needed to support the following usage.
		//
		// eval {java::import -forget} [java::import]
		//
		// This happens when java::import returns the empty list

		if (startIdx >= objv.length) {
			interp.resetResult();
			return;
		}

		// Figure out if the "-package pkg" arguments are given
		elem = objv[startIdx].toString();
		if (elem.equals("-package")) {
			startIdx++;

			// "java::import -package" is not a valid usage
			// "java::import -forget -package" is not a valid usage
			if (startIdx >= objv.length) {
				throw new TclException(interp, usage);
			}

			pkg = objv[startIdx].toString();
			if (pkg.length() == 0) {
				throw new TclException(interp, usage);
			}
			startIdx++;
		}

		// No additional arguments means we have hit one of
		// two conditions.
		//
		// "java::import -forget -package pkg"
		// "java::import -package pkg"

		if (startIdx >= objv.length) {
			if (forget) {
				// We must have "java::import -forget -package pkg"

				// Ceck that it is not "java::import -forget" which is invalid!
				if (pkg == null) {
					throw new TclException(interp, usage);
				}

				// forget each member of the given package

				boolean found = false;

				for (Iterator iter = packageTable.entrySet().iterator(); iter.hasNext();) {
					Map.Entry entry = (Map.Entry) iter.next();
					elem = (String) entry.getKey();

					if (elem.equals(pkg)) {
						// This is the package we are looking for, remove it!
						if (found) {
							throw new TclRuntimeError("unexpected : found == true");
						}
						found = true;

						// Loop over each class imported from this package
						// and remove the class and package entry.
						ArrayList alist = (ArrayList) entry.getValue();

						for (ListIterator iter2 = alist.listIterator(); iter2.hasNext();) {
							// Remove imported class from the classTable
							elem2 = (String) iter2.next();
							if (classTable.remove(elem2) == null) {
								throw new TclRuntimeError("key " + elem2 + " not in classTable");
							}
						}

						// Remove the package entry
						if (packageTable.remove(elem) == null) {
							throw new TclRuntimeError("key " + elem + " not in packageTable");
						}
					}
				}

				if (!found) {
					// It is an error to forget a package that
					// does not have any classes imported from it

					throw new TclException(interp, "cannot forget package \"" + pkg
							+ "\", no classes were imported from it");
				}

				interp.resetResult();
				return;
			} else {
				if (pkg == null) {
					throw new TclRuntimeError("unexpected : pkg == null");
				}

				// "java::import -package pkg" should return each imported
				// class in the given package

				for (Iterator iter = packageTable.entrySet().iterator(); iter.hasNext();) {
					Map.Entry entry = (Map.Entry) iter.next();
					elem = (String) entry.getKey();

					if (elem.equals(pkg)) {
						// This is the package we are looking for.

						import_list = TclList.newInstance();

						// Loop over each class imported from this package
						ArrayList alist = (ArrayList) entry.getValue();
						for (ListIterator iter2 = alist.listIterator(); iter2.hasNext();) {
							// Remove imported class from the classTable
							elem2 = (String) iter2.next();

							TclList.append(interp, import_list, TclString.newInstance((String) classTable.get(elem2)));
						}
						
						// add wildcard entry, if any
						ArrayList wildcardList = (ArrayList) wildcardTable.get("*");
						if (wildcardList != null) {
							if (wildcardList.contains(pkg)) {
								TclList.append(interp, import_list, TclString.newInstance((String) pkg + ".*"));
							}
						}
						
						interp.setResult(import_list);
						return;
					}
				}
				
				// no specific classes defined, check for a wildcard entry:
				// add wildcard entry, if any
				ArrayList wildcardList = (ArrayList) wildcardTable.get("*");
				if (wildcardList != null) {
					if (wildcardList.contains(pkg)) {
						import_list = TclList.newInstance();
						TclList.append(interp, import_list, TclString.newInstance((String) pkg + ".*"));
						interp.setResult(import_list);
						return;
					}
				}
				

				// If we got this far then we have not imported
				// any classes from the given package, just return

				interp.resetResult();
				return;
			}
		}

		// Keep track of the classes we will import and forget about

		ArrayList importClasses = new ArrayList();
		ArrayList forgetClasses = new ArrayList();
		boolean addedWildcard = false;
		
		// Get the operation type string to be used in error messages
		String operation = "import";
		if (forget) {
			operation = "forget";
		}

		// Start processing class arguments begining at startIdx.

		for (i = startIdx; i < objv.length; i++) {
			elem = objv[i].toString();

			// Check that the class name is not "" or "-forget" or "-package"
			if ((elem.length() == 0) || elem.equals("-forget") || elem.equals("-package")) {
				throw new TclException(interp, usage);
			}

			// Check that the class name does not have the '.'
			// char in it if the package argument was given

			if (pkg != null) {
				if (elem.indexOf('.') != -1) {
					throw new TclException(interp, "class argument must not contain a package specifier"
							+ " when the -package pkg arguments are given");
				}
			}

			// Make sure the class is not a primitive type
			if (elem.equals("int") || elem.equals("boolean") || elem.equals("long") || elem.equals("float")
					|| elem.equals("double") || elem.equals("byte") || elem.equals("short") || elem.equals("char")) {
				throw new TclException(interp, "cannot " + operation + " primitive type \"" + elem + "\"");
			}

			// Create fully qualified name by combining -package argument
			// (if it was given) and the class argument

			String fullyqualified;

			if (pkg == null) {
				fullyqualified = elem;
			} else {
				fullyqualified = pkg + "." + elem;
			}

			// split the fullyqualified name into a package and class
			// by looking for the last '.' character in the string.
			// If there is no '.' in the string the class is in the
			// global package which is not valid.

			int ind = fullyqualified.lastIndexOf('.');
			if (ind == -1) {
				throw new TclException(interp, "cannot " + operation + " from global package");
			}

			String class_package = fullyqualified.substring(0, ind);
			String class_name = fullyqualified.substring(ind + 1, fullyqualified.length());
			boolean wildcardClass = "*".equals(class_name);
			
			// Make sure the class is not in the java.lang package
			if (class_package.equals("java.lang")) {
				throw new TclException(interp, "cannot " + operation + " class \"" + fullyqualified
						+ "\", it is in the java.lang package");
			}

			if (!forget) {
				// if importing "*", just add to the wildcard Table
				if (wildcardClass) {
					ArrayList wildcardList = (ArrayList) wildcardTable.get("*");
					if (wildcardList == null) {
						wildcardTable.put("*", wildcardList = new ArrayList());
					}
					if (! wildcardList.contains(class_package)) {
						wildcardList.add(class_package);
					}
					addedWildcard = true;
					
				} else {
				
					// attemptLoadClass will throw exception if class cannot be
					// loaded, is not accessible, or is inner class
					attemptLoadClass(interp, fullyqualified, class_name);
				}
			}

			// When processing a -forget argument, make sure the class
			// was already imported.  ignore "*" class.

			if (forget) {
				if (! wildcardClass && classTable.get(class_name) == null) {
					throw new TclException(interp, "cannot forget class \"" + fullyqualified
							+ "\", it was never imported");
				}
			}

			// We now know that everything was ok. Add this class
			// to the import or export list for later processing
			if (wildcardClass ) {
				if (forget) {
					ArrayList wildcardList = (ArrayList) wildcardTable.get("*");
					if (wildcardList != null) {
						wildcardList.remove(class_package);
					} 
				}
			} else {
				if (forget) {
					forgetClasses.add(fullyqualified);
				} else {
					importClasses.add(fullyqualified);
				}
			}
		}

		// We now process the forgetClasses or the importClasses.
		// Only one of these can contain elements.
		// ignore if wildcard classes added

		if (! addedWildcard && forgetClasses.size() != 0 && importClasses.size() != 0) {
			throw new TclRuntimeError("unexpected : forgetClasses and importClasses are both nonempty");
		}

		if (forgetClasses.size() != 0) {
			// Loop through each class we want to forget

			for (ListIterator iter = forgetClasses.listIterator(); iter.hasNext();) {
				String fullyqualified = (String) iter.next();
				int ind = fullyqualified.lastIndexOf('.');
				if (ind == -1) {
					throw new TclRuntimeError("unexpected : no package in forget class");
				}

				String class_package = fullyqualified.substring(0, ind);
				String class_name = fullyqualified.substring(ind + 1, fullyqualified.length());

				// Hash the class package key to the package list
				ArrayList class_list = (ArrayList) packageTable.get(class_package);

				// Remove the current class from the list

				int cindex = class_list.indexOf(class_name);
				if (cindex == -1) {
					throw new TclRuntimeError("unexpected : class not found in package list");
				}
				if (class_list.remove(cindex) == null) {
					throw new TclRuntimeError("could not remove element at index " + cindex + " from {" + class_list
							+ "}, class_name is " + class_name);
				}

				// If there are no more classes in the list, remove the package
				if (class_list.size() == 0) {
					if (packageTable.remove(class_package) == null) {
						throw new TclRuntimeError("could not remove " + class_package + " from packageTable");
					}
				}

				// Remove the name -> fullyqualified entry
				if (classTable.remove(class_name) == null) {
					throw new TclRuntimeError("could not remove " + class_name + " from classTable");
				}
			}
		}

		if (importClasses.size() != 0) {

			// Loop through each class we want to import

			for (ListIterator iter = importClasses.listIterator(); iter.hasNext();) {
				String fullyqualified = (String) iter.next();
				int ind = fullyqualified.lastIndexOf('.');
				if (ind == -1) {
					throw new TclRuntimeError("unexpected : no package in import class");
				}

				String class_package = fullyqualified.substring(0, ind);
				String class_name = fullyqualified.substring(ind + 1, fullyqualified.length());

				// If this import already exists, just continue on to the next
				// class

				if (classTable.get(class_name) != null) {
					continue;
				} else {
					// We are adding a new class import

					classTable.put(class_name, fullyqualified);

					// Hash the class package key to the package list
					ArrayList class_list = (ArrayList) packageTable.get(class_package);

					if (class_list == null) {
						// A new package is being added
						class_list = new ArrayList();
						packageTable.put(class_package, class_list);
					}

					// Add the name of the class (not fully qualified) to the
					// list
					class_list.add(class_name);
				}
			}
		}

		interp.resetResult();

		return;
	}

	/**
	 * Attempt to load a class, successful completion indicates that the class
	 * was loaded.
	 * 
	 * @param interp
	 * @param fullyqualified
	 * @param class_name
	 * @throws TclException
	 */
	private static void attemptLoadClass(Interp interp, String fullyqualified, String class_name) throws TclException {

		TclClassLoader tclClassLoader = (TclClassLoader) interp.getClassLoader();

		// Use TclClassLoader defined on a per-interp basis
		// Make sure class is not in the global package
		// We need to test to see if the class exists only
		// when doing an import because doing a -forget will
		// only work if the class had been imported already

		boolean inGlobal = true;

		try {
			tclClassLoader.loadClass(class_name);
		} catch (ClassNotFoundException e) {
			inGlobal = false;
		} catch (PackageNameException e) {
			throw e;
		}

		if (inGlobal) {
			tclClassLoader.removeCache(class_name);

			throw new TclException(interp, "cannot import \"" + fullyqualified
					+ "\" it conflicts with a class with the same name" + " in the global package");
		}

		// Make sure the class can be loaded (using the fully qualified
		// name)

		Class c = null;
		try {
			c = tclClassLoader.loadClass(fullyqualified);

			if (!PkgInvoker.isAccessible(c)) {
				JavaInvoke.notAccessibleError(interp, c);
			}
			if (JavaInvoke.isInnerClass(c)) {
				throw new TclException(interp, "can't import an inner class");
			}
		} catch (ClassNotFoundException e) {
		} catch (PackageNameException e) {
		}

		if (c == null) {
			// Generate a specific error message for an inner class.
			// An inner class would not have been loaded by loadClass()
			// above, we need to invoke getClassByName() to find it.

			Class inner = null;
			try {
				inner = JavaInvoke.getClassByName(interp, fullyqualified);
			} catch (TclException e2) {
				// No-op
			}

			if (inner != null && JavaInvoke.isInnerClass(inner)) {
				throw new TclException(interp, "can't import an inner class");
			} else {
				throw new TclException(interp, "cannot import class \"" + fullyqualified + "\", it does not exist");
			}
		}
	}

	/**
	 * This method is invoked can be invoked to query the import system to find
	 * the fully qualified name of a class. See the user documentation for
	 * details on what it does.
	 * 
	 * @param interp
	 *            Current interp
	 * @param name
	 *            Class name to qualify
	 * @return Returns the fully qualified name if it was imported. If the class
	 *         was not imported then null will be returned.
	 */
	public static String getImport(Interp interp, String name) {
		HashMap classTable = interp.importTable[0];
		String fullyqualified = (String) classTable.get(name);
		if (fullyqualified != null) {
			return fullyqualified;
		}
		HashMap packageTable = interp.importTable[1];
		HashMap wildcardTable = interp.importTable[2];
		ArrayList wildcardList = (ArrayList) wildcardTable.get("*");
		if (wildcardList != null) {
			Iterator iter = wildcardList.iterator();
			while (iter.hasNext()) {
				String class_package = (String) iter.next();
				fullyqualified = class_package + "." + name;
				boolean loaded = false;
				TclClassLoader tclClassLoader = (TclClassLoader) interp.getClassLoader();
				try {
					tclClassLoader.loadClass(fullyqualified);
					loaded = true;
				} catch (ClassNotFoundException e) {
				} catch (PackageNameException e) {
				}
				
				if (! loaded) {
					continue;
				}
				
				// loaded class successfully, add to import table 
				classTable.put(name, fullyqualified);
				
				// Hash the class package key to the package list
				ArrayList class_list = (ArrayList) packageTable.get(class_package);

				if (class_list == null) {
					packageTable.put(class_package, class_list = new ArrayList());
				}
				// Add the name of the class (not fully qualified) to the list
				if (! class_list.contains(name)){
					class_list.add(name);
				}
				
				return fullyqualified;
			}
		}
		return null;
	}

}
