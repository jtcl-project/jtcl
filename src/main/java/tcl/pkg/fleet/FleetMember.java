
/* Heavily modified from TJCThread code  by bjohnson */

//
// TJC Runtime Compiler main thread. This thread is accessed
// by any active interp that wishes to compile a Tcl method
// into Java class files. Generating Java code and then
// compiling it to Tcl code can take some time, so it is
// done in a separate thread and loaded into the requesting
// Interp when ready.
package tcl.pkg.fleet;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import tcl.lang.*;

public class FleetMember implements Runnable {

    public static final int STATUS_OK = 0;
    public static final int STATUS_ERROR = 1;
    private static boolean debug = false;
    private boolean terminated = false;
    // The FleetMember instance, it is possible that
    // the thread could be terminated and then
    // started again which would replace this instance.
    private Thread thread = null;
    // Thread safe queue of events to process.
    private BlockingQueue queue = new LinkedBlockingQueue();
    // Jacl interp used to process events. The
    // interp is created in the other thread.
    // it should never be accessed from the
    // caller thread.
    private Interp interp = null;
    private final String name;

    public static interface MessageResult {

        public void completed(final int status, final String memberName, final TclObject result);
    }

    private static class ExecEvent {

        final Message callback;

        ExecEvent(final Message callback) {
            this.callback = callback;
        }
    }

    FleetMember(final String name) {
        this.name = name;
        thread = new Thread(this);
        thread.setDaemon(true);

        thread.setName(name+" service");

        if (debug) {
            System.out.println("thread create");
        }
        thread.start();


    }

    // Invoked to send a message that will be evaluated as a command
    public synchronized void execCommand(Message callback) {
        if (debug) {
            System.out.println("execCommand ");
        }

        ExecEvent event = new ExecEvent(callback);
        queue.add(event);

        synchronized (this) {
            if (debug) {
                System.out.println("notify in execCommand");
            }
            notify(); // wake up other thread
        }
    }
    public int forget() {
        int size = queue.size();
        queue.clear();
        return size;
    }
    public int messageCount() {
        int size = queue.size();
        return size;
    }
    public void run() {
        if (debug) {
            System.out.println("thread start");
        }

        // Loop forever waiting for next request to be
        // added to the queue.
        try {
            while (true) {
                ExecEvent event = (ExecEvent) queue.take();
                if (event.callback == null) {
                    break;
                }
                processEvent(event);
            }
        } catch (InterruptedException ieE) {
            ieE.printStackTrace();

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

    // Invoked once for each event ExecEvent processed in the
    // TJC compile thread.
    private void processEvent(ExecEvent event) {

        if (debug) {
            System.out.println("PROCESS QUEUE EVENT: " + event);
        }

        try {
            // Init interp if needed
            if (interp == null) {
                if (debug) {
                    System.out.println("Interp() and init");
                }
                interp = new Interp();
            }
            evalScript(event.callback);

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

            if (debug) {
                System.out.println(msg.toString());
            }

            // Invoke callback to report error

            event.callback.completed(1,name, TclString.newInstance(msg.toString()));
        } finally {
            try {
                TclObject[] cmdArgs = TclList.getElements(interp, event.callback.messageList);
                for (TclObject cmdArg:cmdArgs) {
                    cmdArg.release();
                }
        //        event.callback.messageList.release();
            } catch (TclException tclE) {
                
            }

        }
    }

    private void evalScript(Message callback) throws TclException {
        if (debug) {
            System.out.println("evalCmd ");
        }

        interp.eval(callback.messageList, TCL.EVAL_GLOBAL);
        // fixme need to worry about refcount etc of sending result as TclObject
        
        TclObject result = interp.getResult();
        result.preserve();
        interp.resetResult();
        callback.completed(0,name, result);
    }

    // Invoked when FleetMember is garbage collected.
    protected void finalize() throws Throwable {
        if (debug) {
            System.out.println("TclThread finalized");
        }

        super.finalize();
    }
}
