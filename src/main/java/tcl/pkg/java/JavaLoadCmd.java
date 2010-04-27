/* 
 * JavaLoadCmd.java --
 *
 *	This class implements the built-in "java::load" command in Tcl.
 *
 * Copyright (c) 1997 by Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and redistribution
 * of this file, and for a DISCLAIMER OF ALL WARRANTIES.
 *
 * RCS: @(#) $Id: JavaLoadCmd.java,v 1.5 2006/04/13 07:36:50 mdejong Exp $
 */

package tcl.pkg.java;

import tcl.lang.Command;
import tcl.lang.Extension;
import tcl.lang.Interp;
import tcl.lang.PackageNameException;
import tcl.lang.TclClassLoader;
import tcl.lang.TclException;
import tcl.lang.TclIndex;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;

public class JavaLoadCmd implements Command {

	// Switches that are legal in this command.

	private static final String validOpts[] = { "-classpath" };

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * cmdProc --
	 * 
	 * This procedure is invoked to process the "java::load" Tcl command. See
	 * the user documentation for details on what it does.
	 * 
	 * Results: None
	 * 
	 * Side effects: The interps result is set or a TclException is thrown.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public void cmdProc(Interp interp, // the current interpreter.
			TclObject argv[]) // command arguments.
			throws TclException // if the package cannot be loaded.
	{
		TclClassLoader tclClassLoader;
		TclObject classpath;
		String packageName;
		Class pkgClass;
		Extension pkg = null;
		String errorMsg;
		boolean validLoad = false;

		if ((argv.length != 2) && (argv.length != 4)) {
			throw new TclNumArgsException(interp, 1, argv,
					"?-classpath arg? packageName");
		}

		// Populate the classpath array with arguments from command line, if
		// the -classpath option was specified.

		if (argv.length == 4) {
			TclIndex.get(interp, argv[1], validOpts, "switch", 0);
			classpath = argv[2];
			packageName = argv[3].toString();
		} else {
			classpath = null;
			packageName = argv[1].toString();
		}

		errorMsg = "load \"" + packageName + "\" failed: ";

		// The class loader dosen't want .class at the end, so strip
		// it off if it exists.

		if (packageName.endsWith(".class")) {
			packageName = packageName.substring(0, packageName
					.lastIndexOf(".class"));
		}

		// When no -classpath argument is given, just use the interp
		// TclClassLoader which reads values from TCL_CLASSPATH. If
		// a -classpath argument was given, then defined a TclClassLoader
		// that will delagate to the interp class loader.

		if (classpath == null) {
			tclClassLoader = (TclClassLoader) interp.getClassLoader();
		} else {
			tclClassLoader = (TclClassLoader) interp.getClassLoader();
			tclClassLoader = new TclClassLoader(interp, classpath,
					tclClassLoader);
		}

		// Dynamically load the class

		try {
			validLoad = false;
			pkgClass = tclClassLoader.loadClass(packageName);
			validLoad = true;
		} catch (ClassNotFoundException e) {
			throw new TclException(interp, "package \"" + packageName
					+ "\" not found");
		} catch (ClassFormatError e) {
			throw new TclException(interp, errorMsg
					+ "use the fully qualified package name");
		} catch (PackageNameException e) {
			throw new TclException(interp, errorMsg + e);
		} finally {
			// If we did not have a valid load, the packageName
			// must be removed from the static tclClassLoader's
			// cache of loaded classes. If this is not done
			// any other attempts to load this package will always
			// use this tclClassLoader and will always fail.

			if (!validLoad) {
				tclClassLoader.removeCache(packageName);
			}
		}

		try {
			// Create an instance of the class. It is important that the class
			// be casted to a class that was created by the System ClassLoader.
			// This bridges the super class of the pkgClass with other classes
			// instantiated by the System ClassLoader.

			validLoad = false;
			pkg = (Extension) pkgClass.newInstance();

			// Initialize the given package. Usually, some new commands will
			// be created inside the interp.

			pkg.init(interp);
			validLoad = true;

		} catch (IllegalAccessException e) {
			throw new TclException(interp, errorMsg
					+ "class or initializer is not accessible");
		} catch (InstantiationException e) {
			throw new TclException(interp, errorMsg
					+ "object instantiation failure");
		} catch (ClassCastException e) {
			throw new TclException(interp, errorMsg
					+ "not a subclass of tcl.lang.Extension");
		} catch (Exception e) {
			throw new TclException(interp, errorMsg + "can't find class \""
					+ e.getMessage() + "\"");
		} catch (LinkageError e) {
			// Known to covers these error conditions:
			// NoClassDefFoundError
			// ExceptionInInitializerError
			throw new TclException(interp, "Extension \"" + packageName
					+ "\" contains a dependency \"" + e.getMessage()
					+ "\" that could not be resolved.");
		} finally {
			// If we did not have a valid load, the packageName
			// must be removed from the static tclClassLoader's
			// cache of loaded classes. If this is not done
			// any other attempts to load this package will always
			// use this tclClassLoader and will always fail.

			if (!validLoad) {
				tclClassLoader.removeCache(packageName);
			}
		}
	}

} // end JavaLoadCmd

