package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class SetCmdTest extends TclCmdTest {

    @Test
    public void setCommand() throws Exception {
        String resName = "/tcl/lang/cmd/set.test";
        tclTestResource(resName);
    }

}
