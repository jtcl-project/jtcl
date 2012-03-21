/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tcl.pkg.fleet;

import java.util.ArrayList;
import tcl.lang.*;

/**
 *
 * @author brucejohnson
 */
public class ArgOptions {

    private final Interp interp;
    private final TclObject[] argv;
    private final int start;
    private final boolean used[];

    public ArgOptions(Interp interp, TclObject[] argv, int start) {
        this.interp = interp;
        this.argv = argv;
        this.start = start;
        used = new boolean[argv.length];
    }

    public String get(String optName, String defValue) throws TclException {
        for (int i = start; ((i < argv.length) && (i < used.length)); i++) {
            if (used[i]) {
                continue;
            }

            String arg = argv[i].toString();

            if (optName.startsWith(arg)) {
                used[i] = true;
                i++;

                if ((i >= argv.length) || (i >= used.length)) {
                    throw new TclException(interp,
                            "No value for argument \"" + optName + "\"");
                }

                used[i] = true;
                defValue = argv[i].toString();

                break;
            }
        }

        return defValue;
    }

    public double get(String optName, double defValue) throws TclException {
        for (int i = start; ((i < argv.length) && (i < used.length)); i++) {
            if (used[i]) {
                continue;
            }

            String arg = argv[i].toString();

            if (optName.startsWith(arg)) {
                used[i] = true;
                i++;

                if ((i >= argv.length) || (i >= used.length)) {
                    throw new TclException(interp,
                            "No value for argument \"" + optName + "\"");
                }

                used[i] = true;
                defValue = TclDouble.get(interp, argv[i]);

                break;
            }
        }

        return defValue;
    }

    public int get(String optName, int defValue) throws TclException {
        for (int i = start; ((i < argv.length) && (i < used.length)); i++) {
            if (used[i]) {
                continue;
            }

            String arg = argv[i].toString();

            if (optName.startsWith(arg)) {
                used[i] = true;
                i++;

                if ((i >= argv.length) || (i >= used.length)) {
                    throw new TclException(interp,
                            "No value for argument \"" + optName + "\"");
                }

                used[i] = true;
                defValue = TclInteger.get(interp, argv[i]);

                break;
            }
        }

        return defValue;
    }

    public boolean get(String optName) throws TclException {
        boolean defValue = false;

        for (int i = start; ((i < argv.length) && (i < used.length)); i++) {
            if (used[i]) {
                continue;
            }

            String arg = argv[i].toString();

            if (optName.startsWith(arg)) {
                used[i] = true;

                return true;
            }
        }

        return defValue;
    }

    public boolean getOptDoubleList(String optName, ArrayList<Double> defValue)
            throws TclException {
        for (int i = start; ((i < argv.length) && (i < used.length)); i++) {
            if (used[i]) {
                continue;
            }

            String arg = argv[i].toString();

            if (optName.startsWith(arg)) {
                used[i] = true;
                i++;

                if ((i >= argv.length) || (i >= used.length)) {
                    throw new TclException(interp,
                            "No value for argument \"" + optName + "\"");
                }

                used[i] = true;
                TclObject[] regions = TclList.getElements(interp, argv[i]);
                for (TclObject regionPt : regions) {
                    defValue.add(TclDouble.get(interp, regionPt));
                }
                break;
            }
        }
        return defValue.size() > 0;
    }
    public double[] get(String optName,double[] defValue)
            throws TclException {
        for (int i = start; ((i < argv.length) && (i < used.length)); i++) {
            if (used[i]) {
                continue;
            }

            String arg = argv[i].toString();
            
            if (optName.startsWith(arg)) {
                used[i] = true;
                i++;

                if ((i >= argv.length) || (i >= used.length)) {
                    throw new TclException(interp,
                            "No value for argument \"" + optName + "\"");
                }

                used[i] = true;
                
                TclObject[] regions = TclList.getElements(interp, argv[i]);
                defValue = new double[regions.length];
                int j=0;
                for (TclObject regionPt : regions) {
                    defValue[j++] = TclDouble.get(interp, regionPt);
                }
                break;
            }
        }
        return defValue;
    }
}