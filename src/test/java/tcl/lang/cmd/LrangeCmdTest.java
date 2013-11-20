package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class LrangeCmdTest extends TclCmdTest {

    @Test
	public void lrangeCommand() throws Exception {
		String resName = "/tcl/lang/cmd/lrange.test";
		tclTestResource(resName);
	}

}
