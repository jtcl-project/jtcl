package tcl.pkg.fleet;

import tcl.lang.Extension;
import tcl.lang.Interp;

/*
 * This class implements a simple Tcl extension package "Fleet".
 */
/**
 * 
 * @author brucejohnson
 */
public class FleetExt extends Extension {
    /*
     * Create all the commands in the Simple package.
     */

    /**
     * 
     * @param interp
     */
    public void init(Interp interp) {
        Extension.loadOnDemand(interp, "fleet", "tcl.pkg.fleet.FleetCmd");
    }
}
