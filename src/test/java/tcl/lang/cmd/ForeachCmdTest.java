package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class ForeachCmdTest extends TclCmdTest {

    @Test
    public void foreachCommand() throws Exception {
        String resName = "/tcl/lang/cmd/foreach.test";
        tclTestResource(resName);
    }

}
