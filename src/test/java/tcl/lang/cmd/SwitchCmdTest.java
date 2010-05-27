package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class SwitchCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList expectedFailureList = new LinkedList(Arrays.asList( new String[] {
			// differences in regexp expression error messages
			// FIXME - change the error text in the SwitchCmd (regexp) to match 8.4
			"switch-5.1"
		}));
		
		String resName = "/tcl/lang/cmd/switch.test";
		tclTestResource(resName, expectedFailureList);
	}
}
