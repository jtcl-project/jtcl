package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class PwdCmdTest extends TclCmdTest {

    @Test
    public void pwdCommand() throws Exception {
        String resName = "/tcl/lang/cmd/pwd.test";
        tclTestResource(resName);
    }

}
