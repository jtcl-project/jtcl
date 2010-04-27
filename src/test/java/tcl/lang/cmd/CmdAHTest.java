package tcl.lang.cmd;

import java.util.Arrays;

import tcl.lang.TclCmdTest;

public class CmdAHTest  extends TclCmdTest {
	public void testCmd() throws Exception {
		String[] expectedFailures = {"cmdAH-20.3", "cmdAH-20.4"};
		
		String resName = "/tcl/lang/cmd/cmdAH.test";
		tclTestResource(resName, Arrays.asList(expectedFailures));
	}
}
