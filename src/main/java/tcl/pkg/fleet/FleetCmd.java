package tcl.pkg.fleet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import tcl.lang.*;

public class FleetCmd implements Command {

    static private long fleetCount = 0;
    private long memberCount = 0;
    Namespace ns;
    String fleetName;
    final static private HashMap<String,FleetMember> fleetMembers = new HashMap<String,FleetMember>();
    private enum SubCmds {

        create() {

            void eval(final Interp interp, final TclObject argv[], final FleetCmd mCmd) throws TclException {
                ArgOptions argOptions = new ArgOptions(interp, argv, 2);
                String name = argOptions.get("-name", "fleet" + fleetCount);
                fleetCount++;
                FleetCmd fleetCmd = new FleetCmd();
                interp.createCommand(name, fleetCmd);
                fleetCmd.fleetName = name;
                interp.setResult(name);
                Namespace ns = Namespace.createNamespace(interp, "::fleet::"+name, null);
                fleetCmd.ns = ns;
            }
        },
        member() {
            void eval(final Interp interp, final TclObject argv[], final FleetCmd mCmd) throws TclException {
                ArgOptions argOptions = new ArgOptions(interp, argv, 2);
                String name = argOptions.get("-name", "member" + mCmd.memberCount);
                mCmd.memberCount++;
                FleetMember fleetMember = new FleetMember(mCmd,name);
                fleetMembers.put(name,fleetMember);
                interp.setResult(name);
            }
        },
        tell() {

            void eval(final Interp interp, final TclObject argv[], final FleetCmd mCmd) throws TclException {
                ArgOptions argOptions = new ArgOptions(interp, argv, 4);
                if (argv.length < 4) {
                    throw new TclNumArgsException(interp, 2, argv, "memberName msg ?-reply reply? ?-var varName?");
                }
                String replyCmd = argOptions.get("-reply", (String) null);
                String doneVar = argOptions.get("-var", (String) null);
                String memberName = argv[2].toString();

                TclObject[] cmdArgs = TclList.getElements(interp, argv[3]);
                // fixme preserve or duplicate, what about release
                if (memberName.equals("*")) {
                    for (FleetMember member: mCmd.fleetMembers.values()) {
                         TclObject messageList = TclList.newInstance();
                         messageList.preserve();
                         for (TclObject cmdArg:cmdArgs) {
                             cmdArg.preserve();
                             TclList.append(interp, messageList, cmdArg);
                         }
                         sendCommand(interp, member, messageList, replyCmd, doneVar);
                     }
                } else {
                    TclObject messageList = TclList.newInstance();
                    messageList.preserve();
                    for (TclObject cmdArg:cmdArgs) {
                        cmdArg.preserve();
                        TclList.append(interp, messageList, cmdArg);
                    }
                    FleetMember member = mCmd.fleetMembers.get(memberName);
                    if (member == null) {
                        throw new TclException(interp,"Can't find member \"" + memberName + "\" in fleet \"" + mCmd.fleetName+"\"");
                    }
                    sendCommand(interp, member, messageList, replyCmd, doneVar);
                }
            }
        },
        forget() {
            void eval(final Interp interp, final TclObject argv[], final FleetCmd mCmd) throws TclException {
                if (argv.length != 3) {
                    throw new TclNumArgsException(interp, 2, argv, "memberName");
                }
                String memberName = argv[2].toString();
                FleetMember member = mCmd.fleetMembers.get(memberName);
                if (member == null) {
                    throw new TclException(interp,"Can't find member \"" + memberName + "\" in fleet \"" + mCmd.fleetName+"\"");
                } else {
                    int size = member.forget();
                    interp.setResult(size);
                }
            }
        },
        count() {
            void eval(final Interp interp, final TclObject argv[], final FleetCmd mCmd) throws TclException {
                if (argv.length != 4) {
                    throw new TclNumArgsException(interp, 2, argv, "-messages memberName");
                }
                ArgOptions argOptions = new ArgOptions(interp, argv, 2);
                String memberName = argOptions.get("-messages", "");
                if (!memberName.equals("")) {
                    FleetMember member = mCmd.fleetMembers.get(memberName);
                    if (member == null) {
                        throw new TclException(interp,"Can't find member \"" + memberName + "\" in fleet \"" + mCmd.fleetName+"\"");
                    } else {
                        int size = member.messageCount();
                        interp.setResult(size);
                    }
                }
            }
        },
        stats() {
            void eval(final Interp interp, final TclObject argv[], final FleetCmd mCmd) throws TclException {
                if (argv.length != 4) {
                    throw new TclNumArgsException(interp, 2, argv, "-member memberName");
                }
                ArgOptions argOptions = new ArgOptions(interp, argv, 2);
                String memberName = argOptions.get("-member", "");
                if (!memberName.equals("")) {
                    FleetMember member = mCmd.fleetMembers.get(memberName);
                    if (member == null) {
                        throw new TclException(interp,"Can't find member \"" + memberName + "\" in fleet \"" + mCmd.fleetName+"\"");
                    } else {
                        TclObject tDict = TclDict.newInstance();
                        double processingTime = member.getProcessingTime();
                        double waitingTime = member.getWaitingTime();
                        TclDict.put(interp, tDict, TclString.newInstance("processing"),TclDouble.newInstance(processingTime));
                        TclDict.put(interp, tDict, TclString.newInstance("waiting"),TclDouble.newInstance(waitingTime));
                        interp.setResult(tDict);
                    }
                }
            }
        },
        destroy() {

            void eval(final Interp interp, final TclObject argv[], final FleetCmd mCmd) throws TclException {
                if ((argv.length != 2) && (argv.length != 3)) {
                    throw new TclNumArgsException(interp, 2, argv, "?memberName?");
                }
                if (argv.length == 3) {
                    String memberName = argv[2].toString();
                    FleetMember member = mCmd.fleetMembers.get(memberName);
                    if (member == null) {
                        throw new TclException(interp,"Can't find member \"" + memberName + "\" in fleet \"" + mCmd.fleetName+"\"");
                    } else {
                        member.execCommand(null);
                        mCmd.fleetMembers.remove(memberName);
                    }
                } else {
                    Namespace.deleteNamespace(mCmd.ns);
                    int result = interp.deleteCommand(mCmd.fleetName);
                    interp.setResult(result);
                }
            }
        };

        abstract void eval(final Interp interp, final TclObject argv[], final FleetCmd mCmd) throws TclException;
    }
    private static HashMap<String, SubCmds> aliasMap = new HashMap<String, SubCmds>();

    SubCmds getSubCmd(final Interp interp, final String name) throws TclException {
        SubCmds subCmd;
        try {
            subCmd = SubCmds.valueOf(name);
        } catch (IllegalArgumentException iAE) {
            subCmd = aliasMap.get(name);
            if (subCmd == null) {
                int count = 0;
                ArrayList<String> sValues = new ArrayList<String>();
                for (SubCmds testCmd : SubCmds.values()) {
                    if (testCmd.name().startsWith(name)) {
                        sValues.add(testCmd.name());
                        subCmd = testCmd;
                        count++;
                    }
                }
                if (count == 1) {
                    aliasMap.put(name, subCmd);
                } else {
                    if (count == 0) {
                        sValues.clear();
                        for (SubCmds value : SubCmds.values()) {
                            sValues.add(value.name());
                        }
                    }
                    Collections.sort(sValues);
                    StringBuilder valueString = new StringBuilder();
                    int i = 0;
                    for (String value : sValues) {
                        if (i != sValues.size() - 1) {
                            valueString.append(value);
                            valueString.append(", ");
                        } else {
                            valueString.append("or ");
                            valueString.append(value);
                        }
                        i++;
                    }
                    if (count == 0) {
                        throw new TclException(interp, "bad option \"" + name + "\": must be " + valueString);
                    } else {
                        throw new TclException(interp, "ambiguous option \"" + name + "\": must be " + valueString);
                    }
                }
            }
        }

        return subCmd;

    }

    /**
     *
     * @param interp
     * @param argv
     * @throws TclException
     */
    public void cmdProc(Interp interp, TclObject[] argv) throws TclException {
        if (argv.length < 2) {
            throw new TclNumArgsException(interp, 1, argv, "opt");
        }
        try {
            SubCmds subCmd = getSubCmd(interp, argv[1].toString());
            subCmd.eval(interp, argv, this);
        } catch (TclException tclE) {
            throw tclE;
        } catch (Exception e) {
            e.printStackTrace();
            throw new TclException(interp, argv[0] + " " + argv[1] + " " + e.getMessage());
        }
    }

    static void sendCommand(final Interp interp, final FleetMember member, final TclObject messageList, final String readyCmd,
            final String readyVar) throws TclException {
        Message cjobj = new Message(interp, messageList,readyCmd, readyVar);
        member.execCommand(cjobj);
    }
}

