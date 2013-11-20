package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

import java.util.Collections;

public class LsearchCmdTest extends TclCmdTest {

    @Test
    public void lsearchCommand() throws Exception {
        String resName = "/tcl/lang/cmd/lsearch.test";
        tclTestResource(resName);
    }

}
