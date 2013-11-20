package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class SplitCmdTest extends TclCmdTest {

    @Test
    public void splitCommand() throws Exception {
        String resName = "/tcl/lang/cmd/split.test";
        tclTestResource(resName);
    }

}
