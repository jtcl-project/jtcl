package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class IoCmdTest extends TclCmdTest {

    @Test
	public void ioCommand() throws Exception {
		String resName = "/tcl/lang/cmd/ioCmd.test";
		tclTestResource(resName);
	}

}