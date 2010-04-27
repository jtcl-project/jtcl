/* 
 * JavaDefineClassCmd.java --
 *
 *	 This class implements the built-in "java::defineclass" command.
 *
 * Copyright (c) 1997 by Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and redistribution
 * of this file, and for a DISCLAIMER OF ALL WARRANTIES.
 *
 * RCS: @(#) $Id: JavaDefineClassCmd.java,v 1.5 2006/02/08 23:53:47 mdejong Exp $
 */

package tcl.pkg.java;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.TclClassLoader;
import tcl.lang.TclException;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;

public class JavaDefineClassCmd implements Command {

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * cmdProc --
	 * 
	 * This procedure is invoked to process the "java::defineclass" Tcl comamnd.
	 * See the user documentation for details on what it does.
	 * 
	 * Results: None.
	 * 
	 * Side effects: A standard Tcl result is stored in the interpreter.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public void cmdProc(Interp interp, // Current interpreter.
			TclObject argv[]) // Argument list.
			throws TclException // A standard Tcl exception.
	{
		byte[] classData = null;
		Class result;

		if (argv.length != 2) {
			throw new TclNumArgsException(interp, 1, argv, "classbytes");
		}

		TclObject classBytesObj = argv[1];

		// If the classbytes argument is a ReflectObject
		// that contains a byte[] then unwrap it and
		// use the bytes directly. Creating a TclByteArray
		// actually creates a copy of the passed in array,
		// so passing the byte[] directly is faster.

		if (classBytesObj.getInternalRep() instanceof ReflectObject) {
			Object obj = ReflectObject.get(interp, classBytesObj);
			if (obj instanceof byte[]) {
				classData = (byte[]) obj;
			}
		}

		if (classData == null) {
			// FIXME: It would be better if the TclByteArray class
			// was available in both Tcl Blend and Jacl so that we
			// could query bytes directly instead of converting to
			// a string and then converting back to bytes.

			String str = classBytesObj.toString();
			final int str_length = str.length();
			classData = new byte[str_length];
			for (int i = 0; i < str_length; i++) {
				classData[i] = (byte) str.charAt(i);
			}
		}

		// Use TclClassLoader defined on a per-interp basis
		TclClassLoader tclClassLoader = (TclClassLoader) interp
				.getClassLoader();

		result = tclClassLoader.defineClass(null, classData);

		interp
				.setResult(ReflectObject.newInstance(interp, Class.class,
						result));
	}

} // end JavaNewCmd

