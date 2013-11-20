package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class SwitchCmdTest extends TclCmdTest {

    @Test
    public void switchCommand() throws Exception {
        String resName = "/tcl/lang/cmd/switch.test";
        tclTestResource(resName);
    }

}
