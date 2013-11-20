package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class HistoryCmdTest extends TclCmdTest {

    @Test
    public void historyCommand() throws Exception {
        String resName = "/tcl/lang/cmd/history.test";
        tclTestResource(resName);
    }

}
