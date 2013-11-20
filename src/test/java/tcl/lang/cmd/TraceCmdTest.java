package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class TraceCmdTest extends TclCmdTest {

    @Test
	public void traceCommand() throws Exception {
		String resName = "/tcl/lang/cmd/trace.test";
		tclTestResource(resName);
	}

}
