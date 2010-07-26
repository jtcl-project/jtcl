/*
 * JavaEventCmd.java --
 *
 *	Implements the built-in "java:event" Tcl command.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: JavaEventCmd.java,v 1.1.1.1 1998/10/14 21:09:14 cvsadmin Exp $
 *
 */

package tcl.pkg.java;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclIndex;
import tcl.lang.TclInteger;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;

/*
 * This class implements the built-in "java::event" command in Tcl.
 */

public class JavaEventCmd implements Command {

	/*
	 * The Bean Event Manager associated with the interp that owns this BindCmd
	 * instance.
	 */

	BeanEventMgr eventMgr = null;

	/*
	 * Valid command options.
	 */

	static final private String validOpts[] = { "-index", };

	static final int OPT_INDEX = 0;

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * cmdProc --
	 * 
	 * This procedure is invoked as part of the Command interface to process the
	 * "java::event" Tcl command. See the user documentation for details on what
	 * it does.
	 * 
	 * Results: None.
	 * 
	 * Side effects: See the user documentation.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public void cmdProc(Interp interp, // Current interpreter.
			TclObject argv[]) // Argument list.
			throws TclException // A standard Tcl exception.
	{
		int index;
		TclObject propObj;

		if (argv.length > 4) {
			throw new TclNumArgsException(interp, 1, argv,
					"?-index num? ?propertyName?");
		}

		if (argv.length <= 2) {
			index = 0;
		} else {
			TclIndex.get(interp, argv[1], validOpts, "option", 0);

			/*
			 * Since we just have one valid option, if the above call returns
			 * without an exception, we've got "-index" (or abreviations).
			 */

			index = TclInteger.getInt(interp, argv[2]);
		}

		if (argv.length == 2) {
			propObj = argv[1];
		} else if (argv.length == 4) {
			propObj = argv[3];
		} else {
			propObj = null;
		}

		if (eventMgr == null) {
			eventMgr = BeanEventMgr.getBeanEventMgr(interp);
		}

		BeanEventParamSet p = eventMgr.peekEventParamSet();
		if (p == null) {
			throw new TclException(interp, "\"" + argv[0]
					+ "\" cannot be invoked outside of an event handler");
		}

		if ((index < 0) || (index >= p.params.length)) {
			throw new TclException(interp, "event parameter #" + index
					+ " doesn't exist");
		}

		TclObject param = JavaInvoke.convertJavaObject(interp,
				p.paramTypes[index], p.params[index]);

		if (propObj == null) {
			/*
			 * The property name is not specified, return the whole parameter.
			 */

			interp.setResult(param);
		} else {
			/*
			 * Return the given property of the parameter.
			 */

			interp.setResult(JavaInvoke.getProperty(interp, param, propObj,
					true));
		}
	}

} // end JavaEventCmd

