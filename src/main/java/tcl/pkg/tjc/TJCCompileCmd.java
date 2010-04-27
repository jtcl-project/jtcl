/*
 * Copyright (c) 2005 Advanced Micro Devices, Inc.
 *
 * See the file "license.amd" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: TJCCompileCmd.java,v 1.4 2006/08/06 00:38:58 mdejong Exp $
 *
 */

package tcl.pkg.tjc;

import java.util.ArrayList;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.PackageNameException;
import tcl.lang.Procedure;
import tcl.lang.TCL;
import tcl.lang.TclClassLoader;
import tcl.lang.TclEvent;
import tcl.lang.TclException;
import tcl.lang.TclList;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;
import tcl.lang.TclRuntimeError;
import tcl.lang.TclString;
import tcl.lang.cmd.NamespaceCmd;

public class TJCCompileCmd implements Command {

	// Implementation of TJC::compile used to compile
	// Tcl commands into Java byte code and load them
	// into the JVM at runtime.

	public void cmdProc(Interp interp, TclObject[] objv) throws TclException {
		// Usage:
		//
		// TJC::compile cmd
		// TJC::compile cmd -readycmd {my_command_ready_cmd}
		// TJC::compile cmd -readyvar myarr(cmd)
		//
		// TJC::compile -java JINFO -readycmd {my_java_ready_cmd}
		// TJC::compile -java JINFO -readyvar my_java_status

		if (objv.length < 2 || objv.length == 3 || objv.length > 5) {
			throw new TclNumArgsException(interp, 1, objv, "?cmd? ?options?");
		}

		String javaInfo = null;
		String cmd = null;
		String readycmd = null;
		String readyvar = null;
		int next;

		// Argument 1 can be a command name or -java
		// if there are 3 or more arguments

		cmd = objv[1].toString();

		if (cmd.equals("-java")) {
			// -java usage requires either a -readycmd
			// or a -readyvar argument pair since
			// the user code needs some way to know
			// when the source has been compiled
			// and loaded into the JVM.

			if (objv.length != 5) {
				throw new TclNumArgsException(interp, 1, objv,
						"?cmd? ?options?");
			}

			// javaInfo should be a list of length 2, with the
			// following contents: {JCLASSNAME JSRCCODE}

			TclObject obj = objv[2];
			if (TclList.getLength(interp, obj) != 2) {
				throw new TclException(interp,
						"-java JINFO argument must be a 2 element list of {JCLASSNAME JSRCCODE}");
			}
			javaInfo = objv[2].toString();
			cmd = null;
			next = 3;
		} else {
			next = 2;
		}

		if (next == objv.length) {
			// TJC::command cmd
		} else {
			// There should be 2 more arguments, either
			// -readycmd cmd or -readyvar var.

			if ((next + 2) != objv.length) {
				throw new TclNumArgsException(interp, 1, objv,
						"?cmd? ?options?");
			}

			String option = objv[next].toString();
			String value = objv[next + 1].toString();

			if (option.equals("-readycmd")) {
				readycmd = value;
			} else if (option.equals("-readyvar")) {
				readyvar = value;
			} else {
				throw new TclNumArgsException(interp, 1, objv,
						"?cmd? ?options?");
			}
		}

		// Double check settings

		if (javaInfo != null && cmd != null) {
			throw new TclRuntimeError("can set both cmd and -java");
		}
		if (readycmd != null && readyvar != null) {
			throw new TclRuntimeError("can set both -readyvar and -readycmd");
		}

		// If compiling a Java class, init helper method that
		// will be invoked when the Java method is compiled.

		if (javaInfo != null) {
			JavaCompile(interp, javaInfo, readycmd, readyvar);
		} else {
			TclCompile(interp, cmd, readycmd, readyvar);
		}

		return;
	}

	// Process Java source code, compile it to byte code and
	// then load the byte code into the interp.

	static void JavaCompile(Interp interp, String javaInfo, String readyCmd,
			String readyVar) throws TclException {
		// Make sure compiler thread is running
		TJCThread.startThread();

		TJCCompileJavaCmd cjobj = new TJCCompileJavaCmd(interp, javaInfo,
				readyCmd, readyVar);

		// The fully qualified Java source file name must be passed
		// to the compiler layer. For example, a class named Test11
		// in the package foo.bar must be compiled with the source
		// file name "foo/bar/Test11.java". The user needs to pass
		// the class name in the JINFO argument, so construct the
		// Java file name from the Java class name.

		String javaFileName = cjobj.getJavaFileName();
		String javaSrc = cjobj.getJavaSource();

		if (cjobj.debug) {
			System.out.println("Sending Java file name: " + javaFileName);
			System.out.println("Sending javaSrc:\n" + javaSrc);
		}

		TJCThread.compileJavaSource(javaFileName, javaSrc, cjobj);
	}

	// Compile Tcl proc currently defined in the interpreter.

	static void TclCompile(Interp interp, String cmd, String readyCmd,
			String readyVar) throws TclException {
		int i, len;

		String fullyQualifiedCmd;

		// Make sure compiler thread is running
		TJCThread.startThread();

		// Lookup Procedure in the interp.

		Procedure proc = Procedure.findProc(interp, cmd);
		if (proc == null) {
			throw new TclException(interp, "\"" + cmd + "\" isn't a procedure");
		}

		// Generate non-unique Java class name based on the
		// Tcl proc name. The proc includes any namespace
		// qualifiers.

		StringBuffer pname = new StringBuffer(64);
		StringBuffer cname = new StringBuffer(64);

		if (cmd.startsWith("::")) {
			// already fully qualified
			pname.append(cmd);
		} else {
			// make it fully qualified
			String nsName = proc.wcmd.ns.fullName;

			if (nsName.equals("::")) {
				pname.append(nsName);
			} else {
				pname.append(nsName);
				pname.append("::");
			}
			pname.append(NamespaceCmd.tail(cmd));
		}

		if (false) {
			// Debug
			interp.eval("namespace current", 0);
			String cns = interp.getResult().toString();
			System.out.println("current namespace is \"" + cns + "\"");
			System.out.println("looked up proc \"" + cmd + "\"");
			System.out.println("found proc in namespace \""
					+ proc.wcmd.ns.fullName + "\"");
			System.out.println("fully qualified name is \"" + pname.toString());
		}

		// Generate Java class name for Tcl proc

		fullyQualifiedCmd = pname.toString();

		String upper = fullyQualifiedCmd;
		boolean cap = true;
		len = upper.length();
		for (i = 0; i < len; i++) {
			char c = upper.charAt(i);
			if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
					|| (c >= '0' && c <= '9')) {
				if (cap) {
					c = Character.toUpperCase(c);
					cap = false;
				}
				cname.append(c);
			} else {
				cap = true;
			}
		}
		cname.append("Cmd");
		String javaClassName = cname.toString();

		// Determine if a Tcl proc with this name
		// was already compiled and loaded into
		// the current ClassLoader. In this case,
		// we need to make the Java class name
		// unique so that the ClassLoader does not
		// fail to load the class once it has
		// been compiled.

		if (isClassDefined(interp, javaClassName)) {
			String prefix = cname.toString();
			String suffix = null;

			for (i = 2; i < Integer.MAX_VALUE; i++) {
				suffix = String.valueOf(i);

				if (!isClassDefined(interp, prefix + suffix)) {
					break;
				}
			}
			if (i == Integer.MAX_VALUE) {
				// This should never happen
				throw new TclRuntimeError("suffix integer overflow");
			}

			cname.append(suffix);
			javaClassName = cname.toString();
		}

		// Generate proc declaration

		TclObject procList = TclList.newInstance();
		TclList.append(interp, procList, TclString.newInstance("proc"));
		TclList.append(interp, procList, TclString.newInstance(cmd));

		// Build up args list from the Procedure object

		TclObject args = TclList.newInstance();
		len = proc.argList.length;
		for (i = 0; i < len; i++) {
			TclObject name = proc.argList[i][0];
			TclObject defval = proc.argList[i][1];

			if (defval == null) {
				TclList.append(interp, args, TclString.newInstance(name
						.toString()));
			} else {
				TclObject defpair = TclList.newInstance();
				TclList.append(interp, defpair, TclString.newInstance(name
						.toString()));
				TclList.append(interp, defpair, TclString.newInstance(defval
						.toString()));
				TclList.append(interp, args, defpair);
			}
		}
		if (proc.isVarArgs) {
			TclList.append(interp, args, TclString.newInstance("args"));
		}
		TclList.append(interp, procList, TclString.newInstance(args));

		// Get proc body and append it to the proc decl

		TclList.append(interp, procList, TclString.newInstance(proc.body
				.toString()));

		TJCCompileTclCmd ctobj = new TJCCompileTclCmd(interp,
				fullyQualifiedCmd, readyCmd, readyVar);

		if (ctobj.debug) {
			System.out.println("Sending proc decl\n" + procList.toString());
		}

		TJCThread.compileTclSource(javaClassName, procList.toString(), ctobj);
	}

	// Return true if a Java class with the given name has
	// already been defined in the ClassLoader for the
	// given interp.

	static boolean isClassDefined(Interp interp, String javaClassName) {
		final boolean debug = false;

		TclClassLoader tclClassLoader = (TclClassLoader) interp
				.getClassLoader();
		Class alreadyLoaded = null;

		try {
			if (debug) {
				System.out.println("checking for duplicate class name : "
						+ javaClassName);
			}

			alreadyLoaded = tclClassLoader.loadClass(javaClassName);
		} catch (ClassNotFoundException e) {
			// No-op
		} catch (PackageNameException e) {
			// Should not be possible to catch a PackageNameException
			// since the Java class is not in the tcl.* or java.* packages.

			throw new TclRuntimeError("unexpected PackageNameException :"
					+ e.getMessage());
		}

		boolean isDefined = (alreadyLoaded != null);

		if (debug) {
			System.out.println("class isDefined is " + isDefined);
		}

		return isDefined;
	}

} // end class TJCCompileCmd

// Helper class to load compiled class into the
// current thread and register a compiled command
// implementation in the current interp. An
// instance of this object is created for each
// command that is compiled.

class TJCCompileTclCmd extends TclEvent implements TJCThread.CompiledClassReady {
	final boolean debug = false;

	final Interp interp;
	final String cmd;
	final String readyCmd;
	final String readyVar;

	boolean status;
	String errorMsg = "";
	String className = null; // Java class name for this command
	byte[] classBytes = null; // Java class bytes for this command

	TJCCompileTclCmd(Interp interp, String cmd, String readyCmd, String readyVar) {
		this.interp = interp;
		this.cmd = cmd;
		this.readyCmd = readyCmd;
		this.readyVar = readyVar;
	}

	// Invoked by TJCThread when a compile job is finished.
	// This implementation will queue an event in the original
	// thread that will replace the Tcl command with a
	// compiled implementation of the command.

	public void compiled(final String geninfo, // Name of Tcl command that was
			// compiled.
			final String jfilename, // File name for Java source,
			// like "Test.java".
			final String jsrcode, // Java source that was compiled.
			final ArrayList cnames, // List of compiled class names.
			final ArrayList cdata, // List of compiled class data as byte[].
			final int status, final String msg) {
		// Add an event to the thread safe Tcl event queue that
		// will replace the existing command with a compiled one.

		if (debug) {
			System.out
					.println("TJCCompileTclCmd CompiledClassReady.compiled()");
			System.out.println("geninfo was " + geninfo);
			System.out.println("jfilename was " + jfilename);
			System.out.println("jsrcode was " + jsrcode);
			System.out.println("cnames was " + cnames);
			if (cdata == null) {
				System.out.println("cdata was null");
			} else {
				System.out.println("cdata length was " + cdata.size());
			}
		}

		// Check compile status:

		if (status == TJCThread.STATUS_OK) {
			this.status = true;

			if (debug) {
				System.out.println("Status was OK");
			}
		} else {
			this.status = false;
			this.errorMsg = msg;

			if (debug) {
				System.out.println("Status was not OK");
				System.out.println("errorMsg was \"" + this.errorMsg + "\"");
			}
		}

		// Tcl command should compile into one class file.

		if (cdata == null) {
			// Error should be indicated in errorMsg
		} else if (cdata.size() == 1) {
			className = (String) cnames.get(0); // Name of class, could be null
			// if not known
			classBytes = (byte[]) cdata.get(0);
		} else {
			this.status = false;
			this.errorMsg = "unexpected number of class files " + cdata.size();

			if (debug) {
				System.out.println("Status was not OK");
				System.out.println("errorMsg was \"" + this.errorMsg + "\"");
			}
		}

		interp.getNotifier().queueEvent(this, TCL.QUEUE_TAIL);

		// Don't wait for the event to be processed in the
		// original thread. Just continue to process the
		// next event in TJCThread.
		// event.sync();
	}

	// Invoked by the original thread (not by TJCThread)
	// when the original thread enters the event loop.
	// This method will replace the Tcl proc with the
	// compiled implementation of the proc.

	public int processEvent(int flags) {
		if (debug) {
			System.out.println("TJCCompileTclCmd.processEvent()");
		}

		// If the compile failed, report that now
		if (status == false) {
			readyReport();
			return 1;
		}

		// Otherwise, load the class file via the TclClassLoader
		// and replace the Tcl proc with the compiled command.

		TclClassLoader tclClassLoader = (TclClassLoader) interp
				.getClassLoader();
		// Flush class loader cache in case it holds the last ref to
		// a previously loaded version of this same class (Tcl command).
		if (className != null) {
			tclClassLoader.removeCache(className);
		}
		Class class_obj = tclClassLoader.defineClass(className, classBytes);
		if (class_obj == null) {
			// Class could not be loaded, status is fail
			status = false;
			errorMsg = "class not loaded by TclClassLoader";
			readyReport();
			return 1;
		}

		Object o = null;
		String instErr = null;
		if (className == null) {
			className = class_obj.getName();
		}
		try {
			o = class_obj.newInstance();
		} catch (InstantiationException ie) {
			instErr = "instance of class " + className
					+ " could not be created";
		} catch (IllegalAccessException iae) {
			instErr = "instance of class " + className
					+ " could not be created";
		}
		if (!(o instanceof TJC.CompiledCommand)) {
			instErr = "instance of class " + className
					+ " must extend TJC.CompiledCommand";
		}
		if (instErr != null) {
			status = false;
			errorMsg = instErr;
			readyReport();
			return 1;
		}
		TJC.CompiledCommand cmdObj = (TJC.CompiledCommand) o;
		try {
			if (debug) {
				System.out.println("now to create command \"" + cmd + "\"");
			}
			TJC.createCommand(interp, cmd, cmdObj);
		} catch (TclException te) {
			status = false;
			errorMsg = te.getMessage();
			readyReport();
			return 1;
		}

		readyReport(); // Report success
		return 1;
	}

	// This method is invoked to indicate that a command has been
	// compiled and installer, or that it failed. This method
	// will set a -readyvar or invoke a -readycmd callback
	// if one was indicated via the TJC::compile command.

	void readyReport() {
		try {

			if (readyVar != null) {
				// Set readyVar to: {STATUS CMDNAME MSG}
				//
				// STATUS: OK or FAIL
				// CMDNAME: Name of Tcl command
				// MSG: text of error message if (STATUS == FAIL)

				TclObject tlist = TclList.newInstance();

				// STATUS:
				if (status) {
					TclList.append(interp, tlist, TclString.newInstance("OK"));
				} else {
					TclList
							.append(interp, tlist, TclString
									.newInstance("FAIL"));
				}

				// CMDNAME:
				TclList.append(interp, tlist, TclString.newInstance(cmd));

				// MSG:
				TclList.append(interp, tlist, TclString.newInstance(errorMsg));

				if (debug) {
					System.out.println("now to set readyVar: " + readyVar + " "
							+ tlist);
				}

				interp.setVar(readyVar, null, tlist, TCL.GLOBAL_ONLY);
			} else if (readyCmd != null) {
				// Invoke readyCmd with the following arguments:
				// readyCmd STATUS CMDNAME MSG
				//
				// STATUS: OK or FAIL
				// CMDNAME: Name of Tcl command
				// MSG: text of error message if (STATUS == FAIL)

				TclObject tlist = TclList.newInstance();

				// readyCmd
				TclList.append(interp, tlist, TclString.newInstance(readyCmd));

				// STATUS:
				if (status) {
					TclList.append(interp, tlist, TclString.newInstance("OK"));
				} else {
					TclList
							.append(interp, tlist, TclString
									.newInstance("FAIL"));
				}

				// CMDNAME:
				TclList.append(interp, tlist, TclString.newInstance(cmd));

				// MSG:
				TclList.append(interp, tlist, TclString.newInstance(errorMsg));

				if (debug) {
					System.out.println("now to eval readyCmd: " + tlist);
				}

				interp.eval(tlist, TCL.EVAL_GLOBAL);
			}

		} catch (TclException te) {
			// TclException should not be thrown above
			te.printStackTrace(System.err);
		}
	}
}

// Helper class to load compiled class into the current
// thread.

class TJCCompileJavaCmd extends TclEvent implements
		TJCThread.CompiledClassReady {
	final boolean debug = false;

	final Interp interp;
	final TclObject javaInfo;
	final String readyCmd;
	final String readyVar;

	boolean status;
	String errorMsg = "";

	ArrayList cnames; // Array of class names
	ArrayList cdata; // Array of class bytes

	TJCCompileJavaCmd(Interp interp, String javaInfo, String readyCmd,
			String readyVar) {
		this.interp = interp;
		// Allocate new TclObject so we don't need to worry about
		// releasing it.
		this.javaInfo = TclString.newInstance(javaInfo);
		this.readyCmd = readyCmd;
		this.readyVar = readyVar;
	}

	// Invoked by TJCThread when a compile job is finished.
	// This implementation will queue an event in the original
	// thread that will define the Java class.

	public void compiled(final String geninfo, // Unused
			final String jfilename, // File name for Java source,
			// like "Test.java".
			final String jsrcode, // Java source that was compiled.
			final ArrayList cnames, // List of compiled class names.
			final ArrayList cdata, // List of compiled class data as byte[].
			final int status, final String msg) {
		// Add an event to the thread safe Tcl event queue that
		// will define the Java class.

		if (debug) {
			System.out
					.println("TJCCompileJavaCmd CompiledClassReady.compiled()");
			System.out.println("geninfo was " + geninfo);
			System.out.println("jfilename was " + jfilename);
			System.out.println("jsrcode was " + jsrcode);
			System.out.println("cnames was " + cnames);
			if (cdata == null) {
				System.out.println("cdata was null");
			} else {
				System.out.println("cdata length was " + cdata.size());
			}
		}

		// Check compile status:

		if (status == TJCThread.STATUS_OK) {
			this.status = true;

			if (debug) {
				System.out.println("Status was OK");
			}
		} else {
			this.status = false;
			this.errorMsg = msg;

			if (debug) {
				System.out.println("Status was not OK");
				System.out.println("errorMsg was \"" + this.errorMsg + "\"");
			}
		}

		// A Java class declaration can compile into multiple classes.

		if (cdata == null) {
			// Error should be indicated in errorMsg
		} else {
			this.cnames = cnames;
			this.cdata = cdata;
		}

		interp.getNotifier().queueEvent(this, TCL.QUEUE_TAIL);

		// Don't wait for the event to be processed in the
		// original thread. Just continue to process the
		// next event in TJCThread.
		// event.sync();
	}

	// Invoked by the original thread (not by TJCThread)
	// when the original thread enters the event loop.
	// This method will define the Java class in
	// the interpreter.

	public int processEvent(int flags) {
		Class cl;

		if (debug) {
			System.out.println("TJCCompileJavaCmd.processEvent()");
		}

		// If the compile failed, report that now
		if (status == false) {
			readyReport();
			return 1;
		}

		// Otherwise, load the classes via the TclClassLoader.

		TclClassLoader tclClassLoader = (TclClassLoader) interp
				.getClassLoader();

		// Class names may or may not be known, if class name is the empty
		// string then query the class name from the class object after
		// the class is defined.

		ArrayList resolved_cnames = new ArrayList();

		for (int i = 0; i < cdata.size(); i++) {
			String cname = null;
			if (cnames != null) {
				cname = (String) cnames.get(i);
				if (cname.length() == 0) {
					cname = null;
				}
			}
			byte[] classBytes = (byte[]) cdata.get(i);
			cl = tclClassLoader.defineClass(cname, classBytes);
			if (cl == null) {
				// Class could not be loaded, status is fail
				status = false;
				if (cname != null) {
					errorMsg = "class \"" + cname
							+ "\" not loaded by TclClassLoader";
				} else {
					errorMsg = "class not loaded by TclClassLoader";
				}
				readyReport();
				return 1;
			}
			if (cname == null) {
				cname = cl.getName();
			}
			resolved_cnames.add(cname);
		}
		cnames = resolved_cnames;

		readyReport(); // Report success
		return 1;
	}

	// This method is invoked to indicate that a Java class
	// was compiled and loaded, or that it failed. This method
	// will set a -readyvar or invoke a -readycmd callback
	// if one was indicated via the TJC::compile command.

	void readyReport() {
		try {

			if (readyVar != null) {
				// Set readyVar to: {STATUS CLASSNAMES MSG}
				//
				// STATUS: OK or FAIL
				// CLASSNAMES: List of fully qualified Java class names
				// MSG: text of error message if (STATUS == FAIL)

				TclObject tlist = TclList.newInstance();

				// STATUS:
				if (status) {
					TclList.append(interp, tlist, TclString.newInstance("OK"));
				} else {
					TclList
							.append(interp, tlist, TclString
									.newInstance("FAIL"));
				}

				// CLASSNAMES:
				TclObject cnames_list = TclList.newInstance();
				if (cnames != null) {
					for (int i = 0; i < cnames.size(); i++) {
						String cname = (String) cnames.get(i);
						TclList.append(interp, cnames_list, TclString
								.newInstance(cname));
					}
				}
				TclList.append(interp, tlist, cnames_list);

				// MSG:
				TclList.append(interp, tlist, TclString.newInstance(errorMsg));

				if (debug) {
					System.out.println("now to set readyVar: " + readyVar + " "
							+ tlist);
				}

				interp.setVar(readyVar, null, tlist, TCL.GLOBAL_ONLY);
			} else if (readyCmd != null) {
				// Invoke readyCmd with the following arguments:
				// readyCmd STATUS CLASSNAMES MSG
				//
				// STATUS: OK or FAIL
				// CLASSNAMES: List of fully qualified Java class names
				// MSG: text of error message if (STATUS == FAIL)

				TclObject tlist = TclList.newInstance();

				// readyCmd
				TclList.append(interp, tlist, TclString.newInstance(readyCmd));

				// STATUS:
				if (status) {
					TclList.append(interp, tlist, TclString.newInstance("OK"));
				} else {
					TclList
							.append(interp, tlist, TclString
									.newInstance("FAIL"));
				}

				// CLASSNAMES:
				TclObject cnames_list = TclList.newInstance();
				if (cnames != null) {
					for (int i = 0; i < cnames.size(); i++) {
						String cname = (String) cnames.get(i);
						TclList.append(interp, cnames_list, TclString
								.newInstance(cname));
					}
				}
				TclList.append(interp, tlist, cnames_list);

				// MSG:
				TclList.append(interp, tlist, TclString.newInstance(errorMsg));

				if (debug) {
					System.out.println("now to eval readyCmd: " + tlist);
				}

				interp.eval(tlist, TCL.EVAL_GLOBAL);
			}

		} catch (TclException te) {
			// TclException should not be thrown above
			te.printStackTrace(System.err);
		}
	}

	// Get the fully qualified Java file name for the given
	// javaInfo pair.

	String getJavaFileName() throws TclException {
		TclObject obj = TclList.index(interp, javaInfo, 0);
		String clName = obj.toString();

		// Generate file name from the class name.
		// "Foo" -> "Foo.java"
		// "one.two.Three" -> "one/two/Three.java"

		StringBuffer nbuff = new StringBuffer(64);
		if (clName.indexOf('.') == -1) {
			// Default package
			nbuff.append(clName);
		} else {
			// Replace each instance of '.' with '/'
			nbuff.append(clName.replace('.', '/'));
		}
		nbuff.append(".java");
		return nbuff.toString();
	}

	// Get the Java source code portion of the javaInfo pair.

	String getJavaSource() throws TclException {
		TclObject src = TclList.index(interp, javaInfo, 1);
		return src.toString();
	}

}
