package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class InfoCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList<String> expectedFailureList = new LinkedList<String>(Arrays.asList( new String[] {
				// fails because it counts the number of compiled, not interpreted, commands in C Tcl; JTCL doesn't compile
	            "info-3.1",
	            // returns the correct values, but non in the same order as C Tcl
	            "info-15.4" 
	        }));
		String resName = "/tcl/lang/cmd/info.test";
		tclTestResource(resName, expectedFailureList);
	}
}
