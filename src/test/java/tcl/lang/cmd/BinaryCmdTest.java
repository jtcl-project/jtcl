package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class BinaryCmdTest extends TclCmdTest {

    @Test
    public void binaryCommand() throws Exception {
        String resName = "/tcl/lang/cmd/binary.test";
        tclTestResource(resName);
    }

}
