package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class SourceCmdTest extends TclCmdTest {

    @Test
    public void sourceCommand() throws Exception {
        String resName = "/tcl/lang/cmd/source.test";
        tclTestResource(resName);
    }

}
