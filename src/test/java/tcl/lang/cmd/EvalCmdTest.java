package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class EvalCmdTest extends TclCmdTest {

    @Test
    public void evalCommand() throws Exception {
        String resName = "/tcl/lang/cmd/eval.test";
        tclTestResource(resName);
    }

}
