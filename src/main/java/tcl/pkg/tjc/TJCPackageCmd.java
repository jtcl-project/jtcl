/*
 * Copyright (c) 2005 Advanced Micro Devices, Inc.
 *
 * See the file "license.amd" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: TJCPackageCmd.java,v 1.4 2006/06/04 20:35:21 mdejong Exp $
 *
 */

package tcl.pkg.tjc;

import tcl.lang.Command;
import tcl.lang.Extension;
import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;
import tcl.pkg.java.JavaInvoke;

public class TJCPackageCmd implements Command {

	// Implementation of TJC::package used to load
	// packages at runtime via a Java package name.

	public void cmdProc(Interp interp, TclObject[] objv) throws TclException {
		if (objv.length != 2) {
			throw new TclNumArgsException(interp, 1, objv, "package");
		}
		String pkgname = objv[1].toString();
		String classname = (pkgname.equals("default") ? "TJCExtension"
				: pkgname + ".TJCExtension");

		// Create instance of Extension
		Class c = JavaInvoke.getClassByName(interp, classname);

		Object o = null;
		try {
			o = c.newInstance();
		} catch (InstantiationException ie) {
			throw new TclException(interp, "class " + classname
					+ " could not be created");
		} catch (IllegalAccessException iae) {
			throw new TclException(interp, "class " + classname
					+ " could not be created");
		}
		if (!(o instanceof Extension)) {
			throw new TclException(interp, "class " + classname
					+ " must extend Extension");
		}
		Extension ext = (Extension) o;
		ext.init(interp);
		interp.resetResult();
		return;
	}
}
