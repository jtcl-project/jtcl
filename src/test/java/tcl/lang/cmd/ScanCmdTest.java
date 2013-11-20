package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class ScanCmdTest extends TclCmdTest {

    @Test
    public void ScanCommand() throws Exception {
        String resName = "/tcl/lang/cmd/scan.test";
        tclTestResource(resName);
    }

}
