package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class CmdAHTest extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList expectedFailureList = new LinkedList(Arrays.asList(new String[] { 
			"cmdAH-20.1","cmdAH-20.2", "cmdAH-20.3", "cmdAH-20.4", "cmdAH-23.1", "cmdAH-23.2", "cmdAH-23.6", 
			"cmdAH-24.3", "cmdAH-26.1", "cmdAH-28.3", "cmdAH-29.4.1"
		}));

		String resName = "/tcl/lang/cmd/cmdAH.test";
		tclTestResource(resName, expectedFailureList);
	}
}
