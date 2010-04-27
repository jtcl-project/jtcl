package tcl.pkg.java;

import java.lang.reflect.Method;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;

/**
 * This command is the only means to safely call a blocking Java method like a
 * modal AWT dialog box. If you used java::call directly the entire application
 * would hang because the AWT blocks the calling thread in an AWT modal dialos
 * show() method. You would use this command in place of the java::call method
 * or a java instance method. It will invoke a static or instance Java method
 * using another thread so that the calling thread will not block. This command
 * is unsupported and undocumented so you should only need to use it if you run
 * into this problem.
 */

public class UnsupportedJDetachCallCmd implements Command, Runnable {
	private boolean inuse = false;

	private Thread t = null;
	private boolean started = false; // true if "detached" thread is running

	// These are the Java objects that we pass over to the other
	// thread so that it can invoke the method.

	private Object[] call_args;
	private Method call_method;
	private Object call_javaObj;

	public void cmdProc(Interp interp, TclObject[] argv) throws TclException {

		if (argv.length < 3) {
			throw new TclNumArgsException(interp, 1, argv,
					"classorObj signature ?arg arg ...?");
		}

		int startIdx = 3;
		int count = argv.length - startIdx;

		if (inuse) {
			throw new TclException(interp,
					"java_detachcall invoked when Thread was already in use");
		}

		// get java objects needed in the call

		TclObject reflectObj = argv[1];
		TclObject signature = argv[2];

		// System.out.println("reflectObj is \"" + reflectObj + "\"");
		// System.out.println("signature is \"" + signature + "\"");

		Class javaCl;

		try {
			// try to get the class by name
			call_javaObj = null;
			javaCl = JavaInvoke.getClassByName(interp, reflectObj.toString());
		} catch (TclException e) {
			// if that did not work it must be a reflect object

			try {
				call_javaObj = ReflectObject.get(interp, reflectObj);
				javaCl = ReflectObject.getClass(interp, reflectObj);
			} catch (TclException e2) {
				throw new TclException(interp,
						"unknown java class or object \"" + reflectObj + "\"");
			}
		}

		FuncSig sig;

		// Check for a static method signature first, then try and instance sig.
		try {
			sig = FuncSig.get(interp, javaCl, signature, argv, startIdx, count,
					true);
		} catch (TclException e) {
			sig = FuncSig.get(interp, javaCl, signature, argv, startIdx, count,
					false);
		}

		call_method = (Method) sig.func;

		// System.out.println("call_javaObj is \"" + call_javaObj + "\"");
		// System.out.println("javaCl is \"" + javaCl + "\"");
		// System.out.println("call_method is \"" + call_method + "\"");

		Class[] paramTypes = call_method.getParameterTypes();

		if (count != paramTypes.length) {
			throw new TclException(interp, "wrong # args for calling "
					+ "method \"" + signature + "\"");
		}

		if (count == 0) {
			call_args = new Object[0];
		} else {
			call_args = new Object[count];
			for (int i = 0; i < count; i++) {
				call_args[i] = JavaInvoke.convertTclObject(interp,
						paramTypes[i], argv[i + startIdx]);
			}
		}

		// create the thread if it was not done already

		if (t == null) {
			t = new Thread(this);
			t.setDaemon(true);
			t.setName("DetachCall service");
			t.start();

			// System.out.println("java_detachcall: created thread");
			// System.out.flush();

			// wait until the other thread has been started and is waiting
			// for an event to process from this thread

			while (!started) {
				Thread.yield();
				Thread.yield();
			}

			Thread.yield();
			Thread.yield();
			Thread.yield();

			// System.out.println("java_detachcall: thread has been started");
			// System.out.flush();

		}

		// signal the other thread

		// System.out.println("java_detachcall: thread has been signaled");
		// System.out.flush();

		synchronized (this) {
			notify(); // tell other thread it should process the call
		}

		// return from this method invocation with no result
		interp.resetResult();

		// System.out.println("java_detachcall: returning");
		// System.out.flush();

	}

	public void run() {
		started = true; // set once to indicate that this thread is ready

		// System.out.println("detached_thread: started up");
		// System.out.flush();

		// enter service loop waiting for blocking Java calls

		// System.out.println("detached_thread: entering service loop");
		// System.out.flush();

		while (true) {
			inuse = false;

			try {
				synchronized (this) {
					wait(); // wait for next service request
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			// System.out.println("detached_thread: servicing event");
			// System.out.flush();

			inuse = true;

			// service the event by invoking the Java method

			try {
				call_method.invoke(call_javaObj, call_args);
			} catch (Exception e) {
				e.printStackTrace();
			}

			// do nothing with the result of the method invocation
		}

	}

}
