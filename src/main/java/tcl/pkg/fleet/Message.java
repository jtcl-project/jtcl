package tcl.pkg.fleet;

import tcl.lang.*;

class Message extends TclEvent implements
        FleetMember.MessageResult {

    final boolean debug = false;
    final TclObject messageList;
    final Interp interp;
    final String readyCmd;
    final String readyVar;
    boolean status;
    private TclObject result;
    String errorMsg = "";
    FleetCmd fleet=null;
    String memberName="";
    int count=0;

    Message(Interp interp, TclObject messageList,String readyCmd,
            String readyVar) {
        this.interp = interp;
        this.messageList = messageList;
        this.readyCmd = readyCmd;
        this.readyVar = readyVar;
    }

    // Invoked by FleetMember when a compile job is finished.
    // This implementation will queue an event in the original
    // thread that will define the Java class.
    public void completed(final int status, final FleetCmd fleet, final FleetMember member, final TclObject result) {
        // Add an event to the thread safe Tcl event queue that
        // will define the Java class.
        this.fleet = fleet;
        this.memberName = member.getName();
        this.count = member.messageCount();
        if (debug) {
            System.out.println("TJCCompileJavaCmd CompiledClassReady.compiled()");
            System.out.println("geninfo was " + result.toString());
        }

        if (status == FleetMember.STATUS_OK) {
            this.status = true;
            this.result = result;
            if (debug) {
                System.out.println("Status was OK");
            }
        } else {
            this.status = false;
            this.result = result;

            if (debug) {
                System.out.println("Status was not OK");
                System.out.println("errorMsg was \"" + this.errorMsg + "\"");
            }
        }
        interp.getNotifier().queueEvent(this, TCL.QUEUE_TAIL);

        // Don't wait for the event to be processed in the
        // original thread. Just continue to process the
        // next event in FleetMember.
        // event.sync();
    }

    // Invoked by the original thread (not by FleetMember)
    // when the original thread enters the event loop.
    // This method will define the Java class in
    // the interpreter.
    public int processEvent(int flags) {
        if (debug) {
            System.out.println("process completion");
        }
        readyReport(); // Report success
        return 1;
    }

    // This method is invoked to indicate that a Java class
    // was completed and loaded, or that it failed. This method
    // will set a -readyvar or invoke a -readycmd callback
    // if one was indicated via the TJC::compile command.
    void readyReport() {
        if (debug) {
            System.out.println("report");
        }
        try {

            if (readyVar != null) {
                // Set readyVar to: {STATUS CLASSNAMES MSG}
                //
                // STATUS: OK or FAIL
                // CLASSNAMES: List of fully qualified Java class names
                // MSG: text of error message if (STATUS == FAIL)

                TclObject tDict = TclDict.newInstance();
                if (status) {
                    TclDict.put(interp, tDict, TclString.newInstance("status"),TclString.newInstance("OK"));
                } else {
                    TclDict.put(interp, tDict, TclString.newInstance("status"),TclString.newInstance("FAIL"));                    
                }

                TclDict.put(interp, tDict, TclString.newInstance("fleet"),TclString.newInstance(fleet.fleetName));
                TclDict.put(interp, tDict, TclString.newInstance("member"),TclString.newInstance(memberName));
                TclDict.put(interp, tDict, TclString.newInstance("value"),result);
                TclDict.put(interp, tDict, TclString.newInstance("count"),TclInteger.newInstance(count));

                // MSG:

                if (debug) {
                    System.out.println("now to set readyVar: " + readyVar + " "
                            + tDict);
                }

                interp.setVar(readyVar, null, tDict, TCL.GLOBAL_ONLY);
            } else if (readyCmd != null) {
                TclObject tlist = TclList.newInstance();
                TclList.append(interp, tlist, TclString.newInstance(readyCmd));
                TclObject tDict = TclDict.newInstance();
                if (status) {
                    TclDict.put(interp, tDict, TclString.newInstance("status"),TclString.newInstance("OK"));
                } else {
                    TclDict.put(interp, tDict, TclString.newInstance("status"),TclString.newInstance("FAIL"));                    
                }

                TclDict.put(interp, tDict, TclString.newInstance("fleet"),TclString.newInstance(fleet.fleetName));
                TclDict.put(interp, tDict, TclString.newInstance("member"),TclString.newInstance(memberName));
                TclDict.put(interp, tDict, TclString.newInstance("value"),result);
                TclDict.put(interp, tDict, TclString.newInstance("count"),TclInteger.newInstance(count));
                TclList.append(interp,tlist,tDict);
                // MSG:
                if (debug) {
                    System.out.println("now to eval readyCmd: " + tlist+" interp " + interp);
                }
                interp.eval(tlist, TCL.EVAL_GLOBAL);
                //result.release();
            }

        } catch (TclException te) {
            // TclException should not be thrown above
            te.printStackTrace(System.err);
        }
    }
}
