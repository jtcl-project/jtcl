/* 
 * TclClassLoader.java --
 *
 *	Implements the Class Loader for dynamically loading
 *      Tcl packages.  When attempting to resolve and load a
 *      new Package the loader looks in four places to find
 *      the class.  In order they are:
 *
 *          1) The unique cache, "classes", inside the TclClassLoader.
 *          2) Using the system class loader (via the context class loader).
 *          3) Any paths passed into the constructor via the pathList variable.
 *          4) Any path in the interps env(TCL_CLASSPATH) variable.
 *
 *      The class will be found if it is any of the above paths
 *      or if it is in a jar file located in one of the paths.
 *
 * TclClassLoader.java --
 *
 *      A class that helps filter directory listings when
 *      for jar/zip files during the class resolution stage.
 *
 * Copyright (c) 1997 by Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and redistribution
 * of this file, and for a DISCLAIMER OF ALL WARRANTIES.
 *
 * RCS: @(#) $Id: TclClassLoader.java,v 1.15 2006/09/11 20:45:30 mdejong Exp $
 */

package tcl.lang;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class TclClassLoader extends ClassLoader {

	// Cache of classes loaded by this class loader. Typically, a
	// class loader is defined on a per-interp basis, so this
	// will cache instances of class data for each access in
	// the interp. Different interpreters require different
	// caches since the same class name could be loaded from
	// two different locations in different interps.

	private HashMap class_cache = new HashMap();

	// Each instance can have a list of additional paths to search. This
	// needs to be stored on a per instance basis because classes may be
	// resolved at later times. classpath is passed into the constructor,
	// and loadpath is extracted from the env(TCL_CLASSPATH) interp variable.

	private String[] classpath = null;
	private String[] loadpath = null;
	private String cached_tclclasspath = null;

	// Used only for error reporting when something went wrong with a class
	// that was loaded out of a jar and we want to know which jar. Will
	// be null unless the last searched class was found in a jar.

	private String lastSearchedClassFile = null;
	private String lastSearchedJarFile = null;

	// Pointer to parent class loader. This value will never be null.

	private ClassLoader parent;

	// Pointer to interp, non-null when the value of env(TCL_CLASSPATH)
	// should be used and checked for updates.

	private Interp interp = null;

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * TclClassLoader --
	 * 
	 * TclClassLoader searches a possible -classpath path and the
	 * env(TCL_CLASSPATH) path for classes and resources to load. A
	 * TclClassLoader is defined on a per-interp basis, if a specific command
	 * needs to search additional paths then that search is done in a
	 * TclClassLoader() that has the interp class loader as a parent. Note that
	 * a TclClassLoader will always have a non-null parent.
	 * 
	 * The list of paths in pathList and env(TCL_CLASSPATH) can be relative to
	 * the current interp dir. The full path names are resolved, before they are
	 * stored.
	 * 
	 * Results: None.
	 * 
	 * Side effects: Creates a new TclClassLoader object.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public TclClassLoader(Interp interp, // Used to get env(TCL_CLASSPATH) and
											// current
			// working dir
			TclObject pathList, // List of additional paths to search
			ClassLoader parent) // parent ClassLoader
	{
		super(parent);
		if (parent == null) {
			throw new TclRuntimeError("parent ClassLoader can't be null");
		}
		this.parent = parent;
		this.interp = interp;
		init(interp, pathList);
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * init --
	 * 
	 * TclClassLoader stores the values to classpath and env(TCL_CLASSPATH) on a
	 * per object basis. This is necessary because classes may not be loaded
	 * immediately, but classpath and loadpath may change over time, or from
	 * object to to object.
	 * 
	 * The list of paths in pathList and env(TCL_CLASSPATH) can be relative to
	 * the current interp dir. The full path names are resolved, before they are
	 * stored.
	 * 
	 * Results:
	 * 
	 * 
	 * Side effects:
	 * 
	 * 
	 * ----------------------------------------------------------------------
	 */

	private void init(Interp interp, // Used to get env(TCL_CLASSPATH) and
										// current
			// working dir
			TclObject pathList) // List of additional paths to search
	{
		TclObject[] elem;
		int i;

		try {
			boolean searchTclClasspath = true;

			// A TclClassLoader that is a child of the interp class loader
			// will search only on a passed in -classpath not
			// env(TCL_CLASSPATH).

			if (parent instanceof TclClassLoader) {
				if (pathList == null) {
					throw new TclRuntimeError(
							"TclClassLoader is a child of the interp "
									+ "class loader but it does not have a -classpath to search");
				}
				searchTclClasspath = false;
			}

			if (pathList != null) {
				elem = TclList.getElements(interp, pathList);
				classpath = new String[elem.length];
				for (i = 0; i < elem.length; i++) {
					classpath[i] = absolutePath(interp, elem[i].toString());
				}
			}

			if (searchTclClasspath) {
				checkTclClasspath();
			}
		} catch (TclException e) {
		}
	}

	// Check the env(TCL_CLASSPATH) variable to see if it has changed since
	// the last invocation. In the init case, the loadpath array is null.

	private void checkTclClasspath() {
		final boolean debug = false;
		TclObject[] elems = null;

		if (debug) {
			System.out.println("checkTclClasspath()");
		}

		try {
			TclObject tobj = interp.getVar("env", "TCL_CLASSPATH",
					TCL.GLOBAL_ONLY);
			String current_tclclasspath = tobj.toString();
			if (debug) {
				System.out.println("current_tclclasspath is: "
						+ current_tclclasspath);
			}

			// env(TCL_CLASSPATH) is set to ""

			if (current_tclclasspath.length() == 0) {
				if (debug) {
					System.out.println("env(TCL_CLASSPATH) is \"\"");
				}

				cached_tclclasspath = "";
				loadpath = null;
				return;
			}

			if (debug) {
				System.out.println("comparing \"" + cached_tclclasspath
						+ "\" to \"" + current_tclclasspath + "\"");
			}

			if ((cached_tclclasspath == null)
					|| !current_tclclasspath.equals(cached_tclclasspath)) {
				// env(TCL_CLASSPATH) has changed, reset cache and reparse

				if (debug) {
					System.out.println("resetting cache");
				}

				cached_tclclasspath = current_tclclasspath;
				elems = TclList.getElements(interp, tobj);
			}
		} catch (TclException e) {
			// env(TCL_CLASSPATH) not set

			if (debug) {
				System.out
						.println("env(TCL_CLASSPATH) not set, TclException was: "
								+ e.getMessage());
			}

			interp.resetResult();
			cached_tclclasspath = null;
			loadpath = null;
			return;
		}

		if (elems == null) {
			// env(TCL_CLASSPATH) is the same value it was before

			if (debug) {
				System.out.println("env(TCL_CLASSPATH) unchanged");
			}
			return;
		} else {
			// env(TCL_CLASSPATH) was changed, reparse path

			if (debug) {
				System.out.println("env(TCL_CLASSPATH) changed, reparsing");
			}

			loadpath = new String[elems.length];
			for (int i = 0; i < elems.length; i++) {
				loadpath[i] = absolutePath(interp, elems[i].toString());
			}
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * loadClass --
	 * 
	 * Resolves the specified name to a Class. This method differs from the
	 * regular loadClass(String) because it always passes a true resolveIt
	 * argument to loadClass(String, boolean).
	 * 
	 * Results: the resolved Class, or null if it was not found.
	 * 
	 * Side effects: ClassNotFoundException if the class loader cannot find a
	 * definition for the class.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public Class loadClass(String className) // The name of the desired Class.
			throws ClassNotFoundException, // The class could not be found.
			PackageNameException // The class is in the java or tcl package
	// but it could not be loaded by system loader.
	{
		return loadClass(className, true);
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * loadClass --
	 * 
	 * Resolves the specified name to a Class. The method loadClass() is called
	 * by the JavaLoadCmd and via Interp.loadClass().
	 * 
	 * Results: the resolved Class, or null if it was not found.
	 * 
	 * Side effects: ClassNotFoundException if the class loader cannot find a
	 * definition for the class.
	 * 
	 * ----------------------------------------------------------------------
	 */

	protected Class loadClass(String className, // The name of the desired
												// Class.
			boolean resolveIt) // If true, then resolve all referenced classes.
			throws ClassNotFoundException, // The class could not be found.
			PackageNameException, // The classes package starts with java or tcl
									// prefix.
			SecurityException // If something goes terribly wrong in
								// defineClass().
	{
		Class result; // The Class that is loaded.
		byte[] classData = null; // The bytes that compose the class file.

		final boolean debug = false;
		final boolean printStack = false;

		if (debug) {
			System.out.println("loadClass " + className);
		}

		// Check our local cache of classes

		result = (Class) class_cache.get(className);
		if (result != null) {
			if (debug) {
				System.out.println("found class_cache entry for key "
						+ className);
			}
			return result;
		} else {
			if (debug) {
				System.out.println("no class_cache entry for key " + className);
			}
		}

		// Resolve with parent ClassLoader to see if it can resolve the class.
		// The parent could be the system class loader, a thread context
		// class loader, or the TclClassLoader for a specific interp.

		try {
			if (debug) {
				if (parent == getSystemClassLoader()) {
					System.out
							.println("Parent ClassLoader is SystemClassLoader");
				} else if (parent == Thread.currentThread()
						.getContextClassLoader()) {
					System.out
							.println("Parent ClassLoader is ContextClassLoader");
				} else if (parent instanceof TclClassLoader) {
					System.out
							.println("Parent ClassLoader is interp TclClassLoader");
				} else {
					System.out.println("Parent ClassLoader is of type "
							+ parent.getClass().toString());
				}
				System.out.println("parent attempting load of class \""
						+ className + "\"");
			}

			result = Class.forName(className, resolveIt, parent);

			// Don't cache classes resolved by a parent class loader, we assume
			// the
			// parent will do any needed caching.

			if (debug) {
				System.out.println("parent load worked for class \""
						+ className + "\"");
			}

			return result;
		} catch (ClassNotFoundException e) {
			if (printStack) {
				e.printStackTrace(System.err);
			}
		} catch (IllegalArgumentException e) {
			if (printStack) {
				e.printStackTrace(System.err);
			}
		} catch (NoClassDefFoundError e) {
			if (printStack) {
				e.printStackTrace(System.err);
			}
		} catch (IncompatibleClassChangeError e) {
			if (printStack) {
				e.printStackTrace(System.err);
			}
		}

		if (debug) {
			System.out.println("parent load did not work for class \""
					+ className + "\"");
		}

		// Protect against attempts to load a class that contains the 'java'
		// or 'tcl.lang' prefix, but is not in the corresponding file structure.

		// FIXME: why??

		if (!className.startsWith("tcl.lang.library.")) {
			if ((className.startsWith("java."))
					|| (className.startsWith("tcl.lang."))) {
				throw new PackageNameException(
						"Java loader failed to load the class "
								+ "and the TclClassLoader is not permitted to "
								+ "load classes in the tcl or java package at runtime, "
								+ "check your CLASSPATH.", className);
			}
		}

		if (debug) {
			System.out.println("TclClassLoader attempting search for class \""
					+ className + "\"");
			if (classpath != null) {
				System.out.println("classpath is defined");
			} else {
				System.out.println("classpath is null");
			}
			if (loadpath != null) {
				System.out.println("loadpath is defined");
			} else {
				System.out.println("loadpath is null");
			}

		}

		// Try to load class from -classpath if it exists

		if (classpath != null) {
			classData = getClassFromPath(classpath, className);
		}
		if (classData == null) {
			// -classpath does not exists or class was not found.
			// Check to see if env(TCL_CLASSPATH) was changed
			// since the last lookup and then search it for
			// possible matches.

			checkTclClasspath();
			if (loadpath != null) {
				classData = getClassFromPath(loadpath, className);
			}
		}

		if (classData == null) {
			throw new ClassNotFoundException(className);
		}

		// Define it (parse the class file)

		// we have to include this catch for java.lang.NoClassDefFoundError
		// because Sun seems to have changed the Spec for JDK 1.2
		try {
			result = defineClass(className, classData, 0, classData.length);
		} catch (NoClassDefFoundError err) {
			throw new ClassFormatError();
		} catch (ClassFormatError err) {
			// This exception can be generated when the className argument
			// does not match the actual name of the class. For instance
			// if we try to define Test.class with data from tester/Test.class
			// we will get this error. Sadly, there does not seem to be any
			// to find out the real name of the class without knowing the
			// format of the .class file and parsing it.

			StringBuffer buf = new StringBuffer(50);
			buf.append(err.getMessage());
			buf.append(". ");
			if (lastSearchedClassFile != null) {
				buf.append(lastSearchedClassFile);
			} else {
				buf.append(className);
			}

			if (lastSearchedJarFile != null) {
				buf.append(" loaded from ");
				buf.append(lastSearchedJarFile);
			}

			buf.append(": class name does not match");
			buf.append(" the name defined in the classfile");

			throw new ClassFormatError(buf.toString());
		}

		if (result == null) {
			throw new ClassFormatError();
		}
		if (resolveIt) {
			resolveClass(result);
		}

		// Store it in our local cache

		if (debug) {
			System.out.println("added class_cache entry for key " + className);
		}

		class_cache.put(className, result);

		return result;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * findResource --
	 * 
	 * Resolves the specified resource name to a URL via a search of
	 * env(TCL_CLASSPATH). This method is invoked by getResource() when a
	 * resource has not been found by the system loader or the parent loader.
	 * 
	 * Results: the resolved URL, or null if it was not found.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	protected URL findResource(String resName) // The name of the desired
												// resource.
			throws PackageNameException // In case resource starts with java or
										// tcl prefix
	{
		final boolean debug = false;
		URL result = null;

		if (debug) {
			System.out.println("findResource " + resName);
		}

		// FIXME: Support for relative resources with dots between them
		// should be implemented and tested.

		// Only know how to resolve absolute resources via TCL_CLASSPATH.

		if (resName.length() == 0 || resName.charAt(0) != '/') {
			return null;
		}
		resName = resName.substring(1);

		// Can't load resources that start with "java/" or "tcl/",
		// these should have been resolved with the system loader.

		// FIXME: why do we not want to load from java/ or tcl/ ???

		if (!resName.startsWith("tcl/lang/library/")) {
			if ((resName.startsWith("java/"))
					|| (resName.startsWith("tcl/lang/"))) {
				throw new PackageNameException("Can't load resource \""
						+ resName
						+ "\" with java or tcl prefix via TCL_CLASSPATH",
						resName);
			}
		}

		if (debug) {
			System.out
					.println("TclClassLoader attempting search for resource \""
							+ resName + "\"");
			if (classpath != null) {
				System.out.println("classpath is defined");
			} else {
				System.out.println("classpath is null");
			}
			if (loadpath != null) {
				System.out.println("loadpath is defined");
			} else {
				System.out.println("loadpath is null");
			}
		}

		// Try to load URL from -classpath if it exists

		if (classpath != null) {
			result = getURLFromPath(classpath, resName);
		}
		if (result == null) {
			// -classpath does not exists or class was not found.
			// Check to see if env(TCL_CLASSPATH) was changed
			// since the last lookup and then search it for
			// possible matches.

			checkTclClasspath();
			if (loadpath != null) {
				result = getURLFromPath(loadpath, resName);
			}
		}

		return result;

		// FIXME: May also need to overload findResources() in order to
		// get the proper resource loading WRT parents working. This
		// would need to return a whole mess of URL objects, not sure
		// how wasteful that is.
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getResource --
	 * 
	 * Attempt to resolve a resource using the parent class loader and then the
	 * tcl class loader. This method seems to be needed because the JDK is not
	 * behaving the way it should as it is not loading resources from the
	 * parent.
	 * 
	 * Results: the resolved URL, or null if it was not found.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public URL getResource(String resName) // The name of the desired resource.
	{
		final boolean debug = false;

		URL res = null;

		if (debug) {
			System.out.println("TclClassLoader.getResource(): " + resName);

			if (parent == getSystemClassLoader()) {
				System.out.println("parent is SystemClassLoader");
			}
			if (Interp.class.getClassLoader() == getSystemClassLoader()) {
				System.out.println("interp loader is SystemClassLoader");
			}
		}

		// Resource searching is kind of tricky. Calling Class.getResource()
		// will search in the jar that the class was loaded from. This does
		// not appear to be very well documented and was found only via
		// testing the runtime impl. Note that this is not the same as
		// calling Interp.class.getClassLoader().getResource() which
		// will not search in the jar from the CLASSPATH.

		if (res == null) {
			// Search in tcljava.jar, jacl.jar, or tclblend.jar.
			res = Interp.class.getResource(resName);

			if (debug) {
				if (res == null) {
					System.out
							.println("did not find resource with jar visibility");
				} else {
					System.out.println("found resource with jar visibility");
				}
			}
		}

		if (res == null) {
			// Search in parent class loader
			res = parent.getResource(resName);

			if (debug) {
				if (res == null) {
					System.out.println("did not find resource in parent");
				} else {
					System.out.println("found resource in parent");
				}
			}
		}

		if (res == null) {
			// Search on env(TCL_CLASSPATH)
			res = findResource(resName);

			if (debug) {
				if (res == null) {
					System.out
							.println("did not find resource in tcl class loader");
				} else {
					System.out.println("found resource in tcl class loader");
				}
			}
		}

		return res;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * defineClass --
	 * 
	 * Given an array of bytes that define a class, create the Class. If the
	 * className is null, we are creating a lambda class. Otherwise cache the
	 * className and definition in the loaders cache.
	 * 
	 * Results: A Class object or null if it could not be defined.
	 * 
	 * Side effects: Cache the Class object in the classes Hashtable.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public Class defineClass(String className, // Name of the class, possibly
												// null.
			byte[] classData) // Binary data of the class structure.
	{
		Class result = null; // The Class object defined by classData.

		// Create a class from the array of bytes

		try {
			result = defineClass(null, classData, 0, classData.length);
		} catch (ClassFormatError ex) {
			// Don't allow this exception to terminate execution, but
			// print some debug info to stderr since this will likely
			// be cause by a compiler bug and we want to know about that.

			System.err.println("TclClassLoader.defineClass():");
			System.err
					.println(ex.getClass().getName() + ": " + ex.getMessage());
		} catch (LinkageError ex) {
			// Don't allow this exception to terminate execution, but
			// print some debug info to stderr since this will likely
			// be cause by a compiler bug and we want to know about that.

			System.err.println("TclClassLoader.defineClass():");
			System.err
					.println(ex.getClass().getName() + ": " + ex.getMessage());
		}

		if (result != null) {
			// If the name of the class is null, extract the className
			// from the Class object, and use that as the key.

			if (className == null) {
				className = result.getName();
			}

			// If a class was created, then store the class
			// in the loaders cache.

			class_cache.put(className, result);
		}

		return (result);
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getClassFromPath --
	 * 
	 * At this point, the class wasn't found in the cache or by the parent
	 * loader. Search through 'classpath' list and the Tcl environment
	 * TCL_CLASSPATH to see if the class file can be found and resolved. If
	 * ".jar" or ".zip" files are found, search them for the class as well.
	 * 
	 * Results: an array of bytes that is the content of the className file.
	 * null is returned if the class could not be found or resolved (e.g.
	 * permissions error).
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private byte[] getClassFromPath(String[] paths, String className) // the
																		// name
																		// of
																		// the
																		// class
																		// trying
																		// to be
																		// resolved
	{
		final boolean debug = false;
		int i = 0;
		byte[] classData = null; // The bytes that compose the class file.
		String curDir; // The directory to search for the class file.
		File file; // The class file.
		int total; // Total number of bytes read from the stream

		if (debug) {
			System.out.println("getClassFromPath for " + className);
		}

		// Search through the list of "paths" for the className.
		// ".jar" or ".zip" files found in the path will also be
		// searched. Yhe first occurence found is returned.
		lastSearchedClassFile = null;
		lastSearchedJarFile = null;

		if (paths != null) {
			// When the class being loaded implements other classes that are
			// not yet loaded, the TclClassLoader will recursively call this
			// procedure. However the format of the class name is
			// foo.bar.name and it needs to be foo/bar/name. Convert to
			// proper format.

			className = className.replace('.', '/') + ".class";

			for (i = 0; i < paths.length; i++) {
				curDir = paths[i].toString();
				try {
					if ((curDir.endsWith(".jar")) || (curDir.endsWith(".zip"))) {
						// If curDir points to a jar file, search it
						// for the class. If classData is not null
						// then the class was found in the jar file.

						classData = extractClassFromJar(curDir, className);
						if (classData != null) {
							return (classData);
						}
					} else {
						// If curDir and className point to an existing file,
						// then the class is found. Extract the bytes from
						// the file.

						file = new File(curDir, className);
						if (file.exists()) {
							FileInputStream fi = new FileInputStream(file);
							classData = new byte[fi.available()];

							total = fi.read(classData);
							while (total != classData.length) {
								total += fi.read(classData, total,
										(classData.length - total));

							}

							// Set this so we can get the full name of the
							// file we loaded the class from later
							lastSearchedClassFile = file.toString();

							return (classData);
						}
					}
				} catch (Exception e) {
					// No error thrown, because the class may be found
					// in subsequent paths.
				}
			}
			for (i = 0; i < paths.length; i++) {
				curDir = paths[i].toString();
				try {
					// The class was not found in the paths list.
					// Search all the directories in paths for
					// any jar files, in an attempt to locate
					// the class inside a jar file.

					classData = getClassFromJar(curDir, className);

					if (classData != null) {
						return classData;
					}
				} catch (Exception e) {
					// No error thrown, because the class may be found
					// in subsequent paths.
				}
			}
		}

		// No matching classes found.

		return null;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getClassFromJar --
	 * 
	 * Given a directory and a class to be found, get a list of ".jar" or ".zip"
	 * files in the current directory. Call extractClassFromJar to search the
	 * Jar file and extract the class if a match is found.
	 * 
	 * Results: An array of bytes that is the content of the className file.
	 * null is returned if the class could not be resolved or found.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private byte[] getClassFromJar(String curDir, // An absoulte path for a
													// directory to search
			String className) // The name of the class to extract from the jar
								// file.
			throws IOException {
		byte[] result = null; // The bytes that compose the class file.
		String[] jarFiles; // The list of files in the curDir.
		JarFilenameFilter jarFilter; // Filter the jarFiles list by only
		// accepting ".jar" or ".zip"

		File file = new File(curDir);

		if (!file.isDirectory()) {
			return null;
		}

		jarFilter = new JarFilenameFilter();
		jarFiles = file.list(jarFilter);

		if (jarFiles == null) {
			return null;
		}

		for (int i = 0; i < jarFiles.length; i++) {
			result = extractClassFromJar(curDir + File.separatorChar
					+ jarFiles[i], className);
			if (result != null) {
				break;
			}
		}
		return result;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * extractClassFromJar --
	 * 
	 * Look inside the jar file, jarName, for a ZipEntry that matches the
	 * className. If a match is found extract the bytes from the input stream.
	 * 
	 * Results: An array of bytes that is the content of the className file.
	 * null is returned if the class could not be resolved or found.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private byte[] extractClassFromJar(String jarName, // An absoulte path for a
														// jar file to search.
			String className) // The name of the class to extract from the jar
								// file.
			throws IOException {
		final boolean debug = false;
		ZipInputStream zin; // The jar file input stream.
		ZipEntry entry; // A file contained in the jar file.
		byte[] result; // The bytes that compose the class file.
		int size; // Uncompressed size of the class file.
		int total; // Number of bytes read from class file.

		if (debug) {
			System.out.println("searching for " + className + " in " + jarName);
		}

		zin = new ZipInputStream(new FileInputStream(jarName));

		try {
			while ((entry = zin.getNextEntry()) != null) {
				// see if the current ZipEntry's name equals
				// the file we want to extract. If equal
				// get the extract and return the contents of the file.

				if (className.equals(entry.getName())) {
					size = getEntrySize(jarName, className);
					result = new byte[size];
					total = zin.read(result);
					while (total != size) {
						total += zin.read(result, total, (size - total));
					}

					// Set these so we can determine which
					// Jar a class was extracted from later
					lastSearchedClassFile = className;
					lastSearchedJarFile = jarName;

					if (debug) {
						System.out.println("class " + className + " found in "
								+ jarName);
					}

					return result;
				}
			}
			if (debug) {
				System.out.println("class " + className + " not found in "
						+ jarName);
			}
			return null;
		} finally {
			zin.close();
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getEntrySize --
	 * 
	 * For some reason, using ZipInputStreams, the ZipEntry returned by
	 * getNextEntry() doesn't contain a valid uncompressed size, so there is no
	 * way to determine how much to read. Using the ZipFile object will return
	 * useful values for the size, but the inputStream returned doesn't work.
	 * The solution was to use both methods to ultimtely extract the class,
	 * which results in an order n^2 algorithm. Hopefully this will change...
	 * 
	 * Results: The size of the uncompressed class file.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private int getEntrySize(String jarName, String className)
			throws IOException {
		ZipEntry entry; // A file contained in the jar file.
		ZipFile zip; // Used to get the enum of ZipEntries.
		Enumeration e; // List of the contents of the jar file.

		zip = new ZipFile(jarName);
		e = zip.entries();

		while (e.hasMoreElements()) {
			// see if the current ZipEntry's
			// name equals the file we want to extract.

			entry = (ZipEntry) e.nextElement();
			if (className.equals(entry.getName())) {
				zip.close();
				return ((int) entry.getSize());
			}
		}
		return (-1);
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * absolutePath --
	 * 
	 * Given a String, construct a File object. If it is not an absoulte path,
	 * then prepend the interps current working directory, to the dirName.
	 * 
	 * Results: The absolute path of dirName
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private static String absolutePath(Interp interp, // the current Interp
			String dirName) // name of directory to be qualified
	{
		File dir;
		String newName;

		dir = new File(dirName);
		if (!dir.isAbsolute()) {
			newName = interp.getWorkingDir().toString()
					+ System.getProperty("file.separator") + dirName;
			dir = new File(newName);
		}
		return (dir.toString());
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * removeCache --
	 * 
	 * Remove the given className from the internal cache.
	 * 
	 * Results: |>None.<|
	 * 
	 * Side effects: |>None.<|
	 * 
	 * ----------------------------------------------------------------------
	 */

	public void removeCache(String className) {
		// The cache could contain the key in the case where the load
		// worked but the object could not be instantiated.

		class_cache.remove(className);
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getURLFromPath --
	 * 
	 * Given a resource name like "/testext/cmd.tcl" loop over the classpath
	 * elements looking for a match to this resource name. If ".jar" or ".zip"
	 * files are found, search them for the resource as well.
	 * 
	 * Results: A URL object.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private URL getURLFromPath(String[] paths, String resName) // the name of
																// the resource
																// trying to be
																// resolved
	{
		final boolean debug = false;
		int i = 0;
		URL url = null;
		String curDir; // The directory to search for the class file.
		File file; // The class file.
		int total; // Total number of bytes read from the stream

		if (debug) {
			System.out.println("getURLFromPath for " + resName);
		}

		// Search through the list of "paths" for the resName.
		// ".jar" or ".zip" files found in the path will also be
		// searched. Yhe first occurence found is returned.
		lastSearchedClassFile = null;
		lastSearchedJarFile = null;

		if (paths != null) {
			for (i = 0; i < paths.length; i++) {
				curDir = paths[i].toString();
				try {
					if ((curDir.endsWith(".jar")) || (curDir.endsWith(".zip"))) {
						// If curDir points to a jar file, search it
						// for the class. If classData is not null
						// then the class was found in the jar file.

						url = extractURLFromJar(curDir, resName);
						if (url != null) {
							return (url);
						}
					} else {
						// If curDir and url point to an existing file,
						// then the resource is found.

						file = new File(curDir, resName);
						if (file.exists()) {
							url = file.toURL();
							return (url);
						}
					}
				} catch (Exception e) {
					// No error thrown, because the class may be found
					// in subsequent paths.
				}
			}
			for (i = 0; i < paths.length; i++) {
				curDir = paths[i].toString();
				try {
					// The resource was not found in the paths list.
					// Search all the directories in paths for
					// any jar files, in an attempt to locate
					// the class inside a jar file.

					url = getURLFromJar(curDir, resName);
					if (url != null) {
						return (url);
					}
				} catch (Exception e) {
					// No error thrown, because the resource may be found
					// in subsequent paths.
				}
			}
		}

		// No matching resource found.

		return null;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getURLFromJar --
	 * 
	 * Given a directory and a resource to be found, get a list of ".jar" or
	 * ".zip" files in the current directory. Call extractURLFromJar to search
	 * the Jar file and extract the resource if a match is found.
	 * 
	 * Results: A URL or null.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private URL getURLFromJar(String curDir, // An absoulte path for a directory
												// to search
			String resName) // The name of the resource to extract from the jar
							// file.
			throws IOException {
		URL result = null;
		String[] jarFiles; // The list of files in the curDir.
		JarFilenameFilter jarFilter; // Filter the jarFiles list by only
		// accepting ".jar" or ".zip"

		jarFilter = new JarFilenameFilter();
		jarFiles = (new File(curDir)).list(jarFilter);

		for (int i = 0; i < jarFiles.length; i++) {
			result = extractURLFromJar(curDir + File.separatorChar
					+ jarFiles[i], resName);
			if (result != null) {
				break;
			}
		}
		return (result);
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * extractURLFromJar --
	 * 
	 * Look inside the jar file, jarName, for a ZipEntry that matches the
	 * resName. If a match is found then generate a URL object.
	 * 
	 * Results: A URL object, or null.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private URL extractURLFromJar(String jarName, // An absoulte path for a jar
													// file to search.
			String resName) // The name of the resource to extract from the jar
							// file.
			throws IOException {
		final boolean debug = false;
		ZipInputStream zin; // The jar file input stream.
		ZipEntry entry; // A file contained in the jar file.
		URL result;
		int size; // Uncompressed size of the class file.
		int total; // Number of bytes read from class file.

		if (debug) {
			System.out.println("searching for " + resName + " in " + jarName);
		}

		zin = new ZipInputStream(new FileInputStream(jarName));

		try {
			while ((entry = zin.getNextEntry()) != null) {
				// see if the current ZipEntry's name equals
				// the file we want to extract. If equal
				// get the extract and return the contents of the file.

				if (debug) {
					System.out.println("comparing " + resName + " to "
							+ entry.getName());
				}

				if (resName.equals(entry.getName())) {
					File file = new File(jarName);
					URL fileURL = file.toURL();
					URL jarURL = new URL("jar:" + fileURL.toString() + "!/"
							+ resName);
					if (debug) {
						System.out.println("match found: file is " + file);
						System.out.println("fileURL is " + fileURL);
						System.out.println("jarURL is " + jarURL);
					}
					return jarURL;
				}
			}
			if (debug) {
				System.out.println("resource " + resName + " not found in "
						+ jarName);
			}
			return null;
		} finally {
			zin.close();
		}
	}

} // end TclClassLoader

/*
 * 
 * TclClassLoader.java --
 * 
 * A class that helps filter directory listings when for jar/zip files during
 * the class resolution stage.
 */

class JarFilenameFilter implements FilenameFilter {

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * accept --
	 * 
	 * Used by the getClassFromJar method. When list returns a list of files in
	 * a directory, the list will only be of jar or zip files.
	 * 
	 * Results: True if the file ends with .jar or .zip
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public boolean accept(File dir, String name) {
		if (name.endsWith(".jar") || name.endsWith(".zip")) {
			return (true);
		} else {
			return (false);
		}
	}

} // end JarFilenameFilter

