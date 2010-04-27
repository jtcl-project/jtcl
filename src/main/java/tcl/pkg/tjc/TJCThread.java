/*
 * Copyright (c) 2006 Mo DeJong
 *
 * See the file "license.amd" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: TJCThread.java,v 1.3 2006/08/04 23:11:14 mdejong Exp $
 *
 */

// TJC Runtime Compiler main thread. This thread is accessed
// by any active interp that wishes to compile a Tcl method
// into Java class files. Generating Java code and then
// compiling it to Tcl code can take some time, so it is
// done in a separate thread and loaded into the requesting
// Interp when ready.

package tcl.pkg.tjc;

import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Vector;

import tcl.lang.Interp;
import tcl.lang.TCL;
import tcl.lang.TclException;
import tcl.lang.TclList;
import tcl.lang.TclObject;
import tcl.lang.TclString;
import tcl.pkg.java.ReflectObject;

public class TJCThread implements Runnable {
	public static final int STATUS_OK = 0;
	public static final int STATUS_ERROR = 1;

	private static boolean debug = false;

	private static String debugSetup = "";

	private static String driver = null;

	// The TJCThread instance, it is possible that
	// the thread could be terminated and then
	// started again which would replace this instance.

	private static TJCThread tinstance = null;
	private static Thread tmain = null;

	// Set to true when the tmain thread should
	// terminate itself.

	private boolean terminate_request = false;
	private boolean terminated = false;

	// Set to true when the thread is ready to process
	// events. This may not be set until the thread has started
	// up and possibly processed any events that were initially
	// added to the queue.

	private boolean ready = false;

	// Thread safe queue of events to process.

	private static Vector queue = new Vector();

	// Jacl interp used to process events. The
	// interp is created in the other thread.
	// it should never be accessed from the
	// caller thread.

	private Interp interp = null;

	// Event record. This is a buffer of line
	// oriented data that indicates what events
	// are being processed and in what order.

	private static StringBuffer eventLog = null;

	// Invoke the compiled() method when the
	// results of a compileJavaSource() or
	// compileTclSource() invocation are ready.

	public static interface CompiledClassReady {
		public void compiled(final String geninfo, // Name that identifies the
				// source
				// for generated Java code. This
				// "" when compiling a Java file.
				final String jfilename, // File name for Java source,
				// like "Test.java".
				final String jsrcode, // Java source that was compiled.
				final ArrayList cnames, // List of compiled class names.
				final ArrayList cdata, // List of compiled class data as byte[].
				final int status, final String msg);
	}

	// Invoked to indicate that a given Java
	// source file should be compiled.

	public static synchronized void compileJavaSource(String filename,
			String source, CompiledClassReady callback) {
		if (callback == null) {
			throw new NullPointerException("callback");
		}

		if (eventLog != null) {
			eventLog.append("compileJavaSource " + filename + "\n");
		}
		if (debug) {
			System.out.println("compileJavaSource " + filename);
		}

		// Test 1, check that the callback works without invoking
		// the notify to process in the other thread.

		if (filename.equals("__FakeTest1.java")) {
			if (eventLog != null) {
				eventLog.append("Fake Java Test 1 processed\n");
			}
			if (debug) {
				System.out.println("Fake Java Test 1 processed");
			}

			ArrayList class_names = new ArrayList();
			class_names.add("__FakeTest1");

			byte[] bytes = { (byte) 'F', (byte) 'A', (byte) 'K', (byte) 'E' };

			ArrayList class_data = new ArrayList();
			class_data.add(bytes);

			callback.compiled("", filename, source, class_names, class_data, 0,
					"");
			return;
		}

		// Add event to thread safe queue

		Vector event = new Vector();
		event.addElement("JAVA");
		event.addElement(filename);
		event.addElement(source);
		event.addElement(callback);

		queue.addElement(event);

		synchronized (tinstance) {
			if (eventLog != null) {
				eventLog.append("notify in compileJavaSource\n");
			}
			if (debug) {
				System.out.println("notify in compileJavaSource");
			}

			tinstance.notify(); // wake up other thread
		}
	}

	// Invoked to indicate that a given Tcl procedure
	// should be compiled into a class file.

	public static synchronized void compileTclSource(String filename,
			String proc_source, CompiledClassReady callback) {
		if (eventLog != null) {
			eventLog.append("compileTclSource " + filename + "\n");
		}
		if (debug) {
			System.out.println("compileTclSource " + filename);
			System.out.println("Tcl proc is:\n" + proc_source);
		}

		// Add event to thread safe queue

		Vector event = new Vector();
		event.addElement("TCL");
		event.addElement(filename);
		event.addElement(proc_source);
		event.addElement(callback);

		queue.addElement(event);

		synchronized (tinstance) {
			if (eventLog != null) {
				eventLog.append("notify in compileTclSource\n");
			}
			if (debug) {
				System.out.println("notify in compileTclSource");
			}

			tinstance.notify(); // wake up other thread
		}
	}

	// Invoked when the user wants the main thread to die.

	public static synchronized void terminateThread() {
		if (eventLog != null) {
			eventLog.append("terminateThread\n");
		}
		if (debug) {
			System.out.println("terminateThread");

			if (queue.size() != 0) {
				System.out.println("terminateThread with unprocessed events");
			} else {
				System.out
						.println("terminateThread with no unprocessed events");
			}
		}

		if (tinstance.terminated) {
			if (eventLog != null) {
				eventLog.append("thread already terminated\n");
			}
			if (debug) {
				System.out.println("thread already terminated");
			}

			return;
		}

		tinstance.terminate_request = true;

		synchronized (tinstance) {
			if (eventLog != null) {
				eventLog.append("notify in terminateThread\n");
			}
			if (debug) {
				System.out.println("notify in terminateThread");
			}
			tinstance.notify(); // wake up other thread
		}
	}

	// Invoked to indicate how debug options for
	// this module should be setup. This method
	// must be invoked before compilation begins
	// since there options are processed when the
	// tmain thread starts.

	public static synchronized void debugSetup(String dbgstr) {
		debugSetup = dbgstr;

		eventLog = null;

		// Search for known debug tokens

		StringTokenizer st = new StringTokenizer(debugSetup);
		while (st.hasMoreTokens()) {
			String token = (String) st.nextToken();
			if (token.equals("-debug")) {
				debug = true;
			} else if (token.equals("-event")) {
				eventLog = new StringBuffer(128);
			} else if (token.equals("-pizza")) {
				driver = "pizza";
			} else if (token.equals("-janino")) {
				driver = "janino";
			}
		}
	}

	// Invoked by test code to see if the thread
	// started up and is ready to process events.
	// Events can be queued up before the thread
	// is ready to start processing them.

	public static synchronized boolean isThreadReady() {
		return tinstance.ready;
	}

	// Invoked to query the event log, if no event log
	// was enabled then this method will return null.

	public static synchronized String getEventLog() {
		if (eventLog == null) {
			return null;
		}
		return eventLog.toString();
	}

	// Invoked to explicitly start up the main
	// thread if it is not already running.
	// Test code might want to do this without
	// having a specific file to compile.

	public static synchronized void startThread() {
		if (tinstance != null && !tinstance.terminate_request) {
			// Thread already started, it is currently running,
			// and a terminate request is not currently pending.

			if (debug) {
				System.out.println("thread currently running");
			}

			return;
		}

		if (debug) {
			System.out.println("creating new Thread()");
		}

		tinstance = new TJCThread();
		tmain = new Thread(tinstance);
		tmain.setDaemon(true);

		// Drop Priority of compile thread below that
		// of the calling thread.

		int priority = tmain.getPriority();
		if (priority > Thread.MIN_PRIORITY) {
			priority--;
			tmain.setPriority(priority);
		}

		tmain.setName("TJCThread service");

		if (eventLog != null) {
			eventLog.append("thread create\n");
		}
		if (debug) {
			System.out.println("thread create");
		}
		tmain.start();

		// Don't wait around for the other thread to
		// be initialized and start running.
	}

	public void run() {
		if (eventLog != null) {
			eventLog.append("thread start\n");
		}
		if (debug) {
			System.out.println("thread start");
		}

		// Loop forever waiting for next request to be
		// added to the queue.

		while (true) {
			if (terminate_request) {
				break;
			}

			// Process all events in the queue, one at
			// a time starting with the first one. Be
			// sure to do this in a thread safe way
			// in case another thread is adding elements
			// to the queue.

			while (true) {
				Vector event;
				synchronized (queue) {
					if (queue.size() == 0) {
						break;
					}
					event = (Vector) queue.remove(0);
					if (debug) {
						System.out.println("removed event, there are "
								+ queue.size() + " events left");
					}
				}
				// Process the event after removing it
				// from the queue and releasing the
				// monitor.
				processEvent(event);

				// Bail out if thread should die
				if (terminate_request) {
					break;
				}
			}

			// Bail out if thread should die
			if (terminate_request) {
				break;
			}

			try {
				synchronized (this) {
					if (eventLog != null) {
						eventLog.append("thread wait\n");
					}
					if (debug) {
						System.out.println("thread wait");
					}

					ready = true;
					this.wait(); // wait for next service request
					ready = false;
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (terminate_request) {
				break;
			}

			if (eventLog != null) {
				eventLog.append("thread wakeup\n");
			}
			if (debug) {
				System.out.println("thread wakeup");
			}
		}

		if (terminate_request) {
			if (eventLog != null) {
				eventLog.append("thread terminate request\n");
			}
			if (debug) {
				System.out.println("thread terminate request");
			}
		}

		// When execution reaches the end of run() the
		// thread will terminate itself.

		if (eventLog != null) {
			eventLog.append("thread terminated\n");
		}
		if (debug) {
			System.out.println("thread terminated");
		}
		terminated = true;

		// Dispose of Jacl interp before leaving this
		// thread. It is critical that the Jacl interp
		// be disposed of in the same thread it was
		// created in.

		if (interp != null) {
			if (debug) {
				System.out.println("Invoking interp.dispose()");
			}

			interp.dispose();
			interp = null;
		}
	}

	// Invoked once for each event Vector processed in the
	// TJC compile thread.

	private void processEvent(Vector event) {
		int len = event.size();

		if (eventLog != null) {
			eventLog.append("process event\n");
		}
		if (debug) {
			System.out.println("PROCESS QUEUE EVENT: " + event);
		}

		if (len != 4) {
			throw new RuntimeException("unexpected number of event args: "
					+ len);
		}

		String type = (String) event.elementAt(0);
		String filename = (String) event.elementAt(1);
		String source = (String) event.elementAt(2);
		CompiledClassReady callback = (CompiledClassReady) event.elementAt(3);

		// Test 2, check that the callback works from the
		// other threads.

		if (filename.equals("__FakeTest2.java")) {
			if (eventLog != null) {
				eventLog.append("Fake Java Test 2 processed\n");
			}
			if (debug) {
				System.out.println("Fake Java Test 2 processed");
			}

			ArrayList class_names = new ArrayList();
			class_names.add("__FakeTest2");

			byte[] bytes = { (byte) 'F', (byte) 'A', (byte) 'K', (byte) 'E' };

			ArrayList class_data = new ArrayList();
			class_data.add(bytes);

			callback.compiled("", filename, source, class_names, class_data, 0,
					"");
			return;
		}

		try {
			// Init interp if needed
			if (interp == null) {
				if (eventLog != null) {
					eventLog.append("Interp() and init\n");
				}
				if (debug) {
					System.out.println("Interp() and init");
				}
				interp = new Interp();
				if (driver != null) {
					interp.setVar("JAVA_DRIVER", null, driver, 0);
				}
				interp
						.eval("source resource:/tcl/pkg/tjc/library/tjcthread.tcl");
			}

			if (type.equals("JAVA")) {
				processJavaSource(filename, source, callback);
			} else if (type.equals("TCL")) {
				processTclSource(filename, source, callback);
			} else {
				throw new TclException(interp, "unknown type " + type);
			}
		} catch (TclException te) {
			StringBuffer msg = new StringBuffer(128);
			msg.append("TclException: ");

			TclObject ei;
			try {
				ei = interp.getVar("errorInfo", null, TCL.GLOBAL_ONLY);
				msg.append(ei.toString());
			} catch (TclException e) {
				msg.append(te.getMessage());
			}

			if (eventLog != null) {
				eventLog.append(msg.toString());
				eventLog.append('\n');
			}
			if (debug) {
				System.out.println(msg.toString());
			}

			// Invoke callback to report error

			callback.compiled("", filename, source, null, null, 1, msg
					.toString());
		}
	}

	// Compile a Java source file into bytecode and invoke the callback.

	private void processJavaSource(String filename, String source,
			CompiledClassReady callback) throws TclException {
		if (eventLog != null) {
			eventLog.append("process java source: " + filename + "\n");
		}
		if (debug) {
			System.out.println("processJavaSource " + filename);
		}

		TclObject cmd_obj = TclString.newInstance("processJavaSource");
		TclObject filename_obj = TclString.newInstance(filename);
		TclObject source_obj = TclString.newInstance(source);

		TclObject list = TclList.newInstance();
		TclList.append(interp, list, cmd_obj);
		TclList.append(interp, list, filename_obj);
		TclList.append(interp, list, source_obj);

		interp.eval(list, TCL.EVAL_GLOBAL);

		// Invoke processJavaSource, this method should set the interp
		// result to a flat list of class names and reflected byte[]
		// objects.

		TclObject result = interp.getResult();
		if (debug) {
			System.out.println("processJavaSource interp result was: "
					+ result.toString());
		}

		ArrayList class_names = new ArrayList();
		ArrayList class_data = new ArrayList();

		final int len = TclList.getLength(interp, result);
		for (int i = 0; i < len; i += 2) {
			TclObject name = TclList.index(interp, result, i);
			TclObject reflectObj = TclList.index(interp, result, i + 1);

			class_names.add((String) name.toString());

			Object obj = ReflectObject.get(interp, reflectObj);
			if (!(obj instanceof byte[])) {
				throw new TclException(interp, "obj \"" + obj
						+ "\" is not a byte[] instance");
			}

			class_data.add((byte[]) obj);
		}
		interp.resetResult();

		callback.compiled("", filename, source, class_names, class_data, 0, "");
	}

	// Compile a Tcl source file into bytecode and invoke the callback.

	private void processTclSource(String filename, String source,
			CompiledClassReady callback) throws TclException {
		TclObject cmd_obj, filename_obj, source_obj, list;

		if (eventLog != null) {
			eventLog.append("process tcl source: " + filename + "\n");
		}
		if (debug) {
			System.out.println("processTclSource " + filename);
		}

		// Invoke the processTclSource Tcl command to generate Java
		// code from the Tcl proc declaration.

		cmd_obj = TclString.newInstance("processTclSource");
		filename_obj = TclString.newInstance(filename);
		source_obj = TclString.newInstance(source);

		list = TclList.newInstance();
		TclList.append(interp, list, cmd_obj);
		TclList.append(interp, list, filename_obj);
		TclList.append(interp, list, source_obj);

		interp.eval(list, TCL.EVAL_GLOBAL);

		// The result object from processTclSource is a list
		// containing the generated proc name and the generated
		// Java source code. Pass the Java source code to
		// the processJavaSource Tcl command.

		TclObject processTclSourceResult = interp.getResult();
		String proc_name = TclList.index(interp, processTclSourceResult, 0)
				.toString();
		String java_source = TclList.index(interp, processTclSourceResult, 1)
				.toString();

		if (debug) {
			System.out.println("processTclSource interp result was:\n"
					+ java_source);
		}

		cmd_obj = TclString.newInstance("processJavaSource");
		filename_obj = TclString.newInstance(filename);
		source_obj = TclString.newInstance(java_source);

		list = TclList.newInstance();
		TclList.append(interp, list, cmd_obj);
		TclList.append(interp, list, filename_obj);
		TclList.append(interp, list, source_obj);

		interp.eval(list, TCL.EVAL_GLOBAL);

		// The processJavaSource command will set the interp
		// result to a flat list of class names and reflected
		// byte[] objects.

		TclObject result = interp.getResult();
		if (debug) {
			System.out.println("processTclSource interp result was: "
					+ result.toString());
		}

		ArrayList class_names = new ArrayList();
		ArrayList class_data = new ArrayList();

		final int len = TclList.getLength(interp, result);
		for (int i = 0; i < len; i += 2) {
			TclObject name = TclList.index(interp, result, i);
			TclObject reflectObj = TclList.index(interp, result, i + 1);

			class_names.add((String) name.toString());

			Object obj = ReflectObject.get(interp, reflectObj);
			if (!(obj instanceof byte[])) {
				throw new TclException(interp, "obj \"" + obj
						+ "\" is not a byte[] instance");
			}

			class_data.add((byte[]) obj);
		}
		interp.resetResult();

		callback.compiled(proc_name, filename, java_source, class_names,
				class_data, 0, "");
	}

	// Invoked when TJCThread is garbage collected.

	protected void finalize() throws Throwable {
		if (debug) {
			System.out.println("TJCThread finalized");
		}

		super.finalize();
	}

}
