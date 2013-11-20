package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class CmdMZTest extends TclCmdTest {

    @Test
    public void cmdMZCommand() throws Exception {
        String resName = "/tcl/lang/cmd/cmdMZ.test";
        tclTestResource(resName);
    }

}
