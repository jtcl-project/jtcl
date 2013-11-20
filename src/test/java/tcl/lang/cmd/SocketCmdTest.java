package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

import java.util.Collections;

public class SocketCmdTest extends TclCmdTest {

    @Test
    public void socketCommand() throws Exception {
        String resName = "/tcl/lang/cmd/socket.test";
        /* need TCLTEST_NAMEOFEXECUTABLE in order to properly initialize stdio testConstraint */
        tclTestResource(TCLTEST_NAMEOFEXECUTABLE, resName, Collections.<String>emptyList());
    }

}
