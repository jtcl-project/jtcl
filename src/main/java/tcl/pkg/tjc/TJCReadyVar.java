/*
 * Copyright (c) 2006 Mo DeJong
 *
 * See the file "license.amd" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: TJCReadyVar.java,v 1.1 2006/02/14 04:13:27 mdejong Exp $
 *
 */

// This class implements the TJCThread.CompiledClassReady
// interface and sets a variable to the results of the
// compilation.

package tcl.pkg.tjc;

import java.util.ArrayList;

import tcl.lang.Interp;
import tcl.lang.TCL;
import tcl.lang.TclEvent;
import tcl.lang.TclException;
import tcl.lang.TclList;
import tcl.lang.TclObject;
import tcl.lang.TclString;
import tcl.pkg.java.ReflectObject;

public class TJCReadyVar implements TJCThread.CompiledClassReady {
	final Interp interp;
	final String varname;

	// Invoked in the calling Thread to indicate
	// where a compile result should be stored.
	// varname can be either a scalar or array
	// variable, it is assumed to be global
	// unless it has explicit namespace qualifiers.
	// The variable value is initailly set to {}
	// whole the compile job is processing.

	public TJCReadyVar(Interp interp, String varname) {
		this.interp = interp;
		this.varname = varname;
		try {
			interp.setVar(varname, null, "", TCL.GLOBAL_ONLY);
		} catch (TclException te) {
		}
	}

	// Invoked by TJCThread when a compile job
	// is finished. This implementation will
	// set a variable to a list indicating the
	// status.
	//
	// {STATUS GENINFO FILENAME SRCCODE CNAMES CDATA MSG}
	//
	// STATUS: OK or FAIL
	// GENINFO: Key indicating generated info source
	// FILENAME: File name for Java source
	// SRCCODE: Java source code that was compiled
	// CNAMES: List of names for compiled classes in CDATA
	// CDATA: List of compiled class data as reflected byte[] Java objects
	// MSG: String indicating compile error if STATUS != OK

	public void compiled(final String geninfo, // Name that identifies the
			// source
			// for generated Java code. This
			// "" when compiling a Java file.
			final String jfilename, // File name for Java source,
			// like "Test.java".
			final String jsrcode, // Java source that was compiled.
			final ArrayList cnames, // List of compiled class names.
			final ArrayList cdata, // List of compiled class data as byte[].
			final int status, final String msg) {
		// Add event to Tcl queue that will
		// calculate and set variable value.

		TclEvent event = new TclEvent() {
			public int processEvent(int flags) {
				try {
					TclObject tlist = TclList.newInstance();

					// STATUS
					if (status == TJCThread.STATUS_OK) {
						TclList.append(interp, tlist, TclString
								.newInstance("OK"));
					} else {
						// FIXME: Check TJCThread status bits for more info.
						TclList.append(interp, tlist, TclString
								.newInstance("FAIL"));
					}

					// GENINFO
					TclList.append(interp, tlist, TclString
							.newInstance(geninfo));

					// FILENAME
					TclList.append(interp, tlist, TclString
							.newInstance(jfilename));

					// SRCCODE
					TclList.append(interp, tlist, TclString
							.newInstance(jsrcode));

					// CNAMES
					TclObject cnames_list = TclList.newInstance();
					if (cnames != null) {
						for (int i = 0; i < cnames.size(); i++) {
							TclList.append(interp, cnames_list, TclString
									.newInstance((String) cnames.get(i)));
						}
					}
					TclList.append(interp, tlist, cnames_list);

					// CDATA
					TclObject cdata_list = TclList.newInstance();
					if (cdata != null) {
						for (int i = 0; i < cdata.size(); i++) {
							byte[] bytes = (byte[]) cdata.get(i);
							TclObject tobj;
							if ((bytes.length == 4) && bytes[0] == 'F'
									&& bytes[1] == 'A' && bytes[2] == 'K'
									&& bytes[3] == 'E') {
								tobj = TclString.newInstance("FAKE");
							} else {
								tobj = ReflectObject.newInstance(interp,
										byte[].class, bytes);
							}
							TclList.append(interp, cdata_list, tobj);
						}
					}
					TclList.append(interp, tlist, cdata_list);

					// MSG
					if (cdata != null) {
						TclList.append(interp, tlist, TclString
								.newInstance(msg));
					} else {
						TclList
								.append(interp, tlist, TclString
										.newInstance(""));
					}

					interp.setVar(varname, null, tlist, TCL.GLOBAL_ONLY);

					// System.out.println("set TJCReadyVar result for variable "
					// + varname);
				} catch (TclException ex) {
					// If an exception was raise, just set the variable to
					// the empty string in case there is a vwait on the var.

					try {
						interp.setVar(varname, null, "", TCL.GLOBAL_ONLY);
					} catch (TclException te) {
					}
				}
				return 1;
			}
		};
		interp.getNotifier().queueEvent(event, TCL.QUEUE_TAIL);

		// Don't want for var to be set, just continue processing
		// next event in TJCThread.
		// event.sync();
	}
}
