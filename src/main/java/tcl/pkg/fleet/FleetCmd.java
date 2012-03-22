package tcl.pkg.fleet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import tcl.lang.*;

public class FleetCmd implements Command {

    static private long fleetCount = 0;
    private long memberCount = 0;
    FleetMember fleetMember = null;
    String fleetName;
    final static private HashMap<String,FleetMember> fleetMembers = new HashMap<String,FleetMember>();
    private enum SubCmds {

        CREATE() {

            void eval(final Interp interp, final TclObject argv[], final FleetCmd mCmd) throws TclException {
                ArgOptions argOptions = new ArgOptions(interp, argv, 2);
                String name = argOptions.get("-name", "fleet" + fleetCount);
                fleetCount++;
                FleetCmd fleetCmd = new FleetCmd();
                interp.createCommand(name, fleetCmd);
                fleetCmd.fleetName = name;
                interp.setResult(name);
            }
        },
        MEMBER() {
            void eval(final Interp interp, final TclObject argv[], final FleetCmd mCmd) throws TclException {
                ArgOptions argOptions = new ArgOptions(interp, argv, 2);
                String name = argOptions.get("-name", "member" + mCmd.memberCount);
                mCmd.memberCount++;
                FleetMember fleetMember = new FleetMember(name);
                fleetMembers.put(name,fleetMember);
                interp.setResult(name);
            }
        },
        TELL() {

            void eval(final Interp interp, final TclObject argv[], final FleetCmd mCmd) throws TclException {
                ArgOptions argOptions = new ArgOptions(interp, argv, 4);
                if (argv.length < 4) {
                    throw new TclNumArgsException(interp, 2, argv, "memberName msg ?-reply reply? ?-var varName?");
                }
                String replyCmd = argOptions.get("-reply", (String) null);
                String doneVar = argOptions.get("-var", (String) null);
                String memberName = argv[2].toString();

                TclObject[] cmdArgs = TclList.getElements(interp, argv[3]);
                TclObject messageList = TclList.newInstance();
                // fixme preserve or duplicate, what about release
                for (TclObject cmdArg:cmdArgs) {
                    cmdArg.preserve();
                    TclList.append(interp, messageList, cmdArg);
                }
                messageList.preserve();
                FleetMember member = mCmd.fleetMembers.get(memberName);
                if (member == null) {
                    throw new TclException(interp,"Can't find member \"" + memberName + "\" in fleet \"" + mCmd.fleetName+"\"");
                }
                sendCommand(interp, member, messageList, replyCmd, doneVar);
            }
        },
        FORGET() {
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
        COUNT() {
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
        DESTROY() {

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
                    int result = interp.deleteCommand(mCmd.fleetName);
                    interp.setResult(result);
                }
            }
        };

        abstract void eval(final Interp interp, final TclObject argv[], final FleetCmd mCmd) throws TclException;
    }
    private static HashMap<String, SubCmds> aliasMap = new HashMap<String, SubCmds>();

    SubCmds getSubCmd(final Interp interp, final String name) throws TclException {
        String uName = name.toUpperCase();
        SubCmds subCmd;
        try {
            subCmd = SubCmds.valueOf(uName);
        } catch (IllegalArgumentException iAE) {
            subCmd = aliasMap.get(name);
            if (subCmd == null) {
                int count = 0;
                ArrayList<String> sValues = new ArrayList<String>();
                for (SubCmds testCmd : SubCmds.values()) {
                    if (testCmd.name().startsWith(uName)) {
                        sValues.add(testCmd.name().toLowerCase());
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
                            sValues.add(value.name().toLowerCase());
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

