package tcl.lang.cmd;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class MiscCmdTest extends TclCmdTest {
	public void testBasic() throws Exception {
		LinkedList expectedFailureList = new LinkedList(Arrays.asList( new String[] {
				// FIXME: error message differ
				"basic-12.1", 
				// FIXME: exec problems??
				"basic-46.2", "basic-46.3", "basic-46.3", "basic-46.4", "basic-46.5"
			}));
		
		String resName = "/tcl/lang/cmd/basic.test";
		tclTestResource(resName, expectedFailureList);
	}
	
	public void testIo() throws Exception {
		String resName = "/tcl/lang/cmd/io.test";
		tclTestResource(resName);
	}
	
	public void testUtil() throws Exception {
		String resName = "/tcl/lang/cmd/util.test";
		tclTestResource(resName);
	}
	
	public void testVar() throws Exception {
		String resName = "/tcl/lang/cmd/var.test";
		tclTestResource(resName);
	}
}