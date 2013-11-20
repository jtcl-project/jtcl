package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class LlengthCmdTest extends TclCmdTest {

    @Test
    public void llengthCommand() throws Exception {
        String resName = "/tcl/lang/cmd/llength.test";
        tclTestResource(resName);
    }

}
