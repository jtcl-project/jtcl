package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class InterpCmdTest extends TclCmdTest {
	LinkedList<String> expectedFailureList = new LinkedList<String>(Arrays.asList( new String[] {
			// different list from [interp hidden] because the 'load' command is not implemented in JTCL
			"interp-21.5",
			"interp-21.8",
			// recursion limit is actually inherited by the slave interpreter as expected, but the nested proc
			// call count is one fewer in JTCL than in TCL.  My best guess is that this is due to TCL compiling
			// some of the contents of the recursive proc in these tests, so that there's no eval internal
			// to the proc (DJB)
			"interp-29.4.1",
			"interp-29.4.2"
		}));
	public void testCmd() throws Exception {
		String resName = "/tcl/lang/cmd/interp.test";
		tclTestResource(resName, expectedFailureList);
	}
}
