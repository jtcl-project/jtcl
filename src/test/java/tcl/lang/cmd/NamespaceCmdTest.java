package tcl.lang.cmd;

import java.util.Collections;

import tcl.lang.TclCmdTest;

public class NamespaceCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		// FIXME: namespace-9.9 causes and endless loop!!
		fail("namespace-9.9 causes and endless loop!");
		
		String resName = "/tcl/lang/cmd/namespace.test";
		tclTestResource("tcltest::configure -verbose {start pass body error skip}", resName, Collections.EMPTY_LIST);
	}
}
