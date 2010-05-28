package tcl.lang.cmd;

import tcl.lang.TclCmdTest;

public class RegexCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		String resName = "/tcl/lang/cmd/regexp.test";
		tclTestResource(resName);
	}
	
	public void testRegexComp() throws Exception {
		String resName = "/tcl/lang/cmd/regexpComp.test";
		tclTestResource(resName);
	}
}
