package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class CaseCmdTest extends TclCmdTest {

    @Test
	public void caseCommand() throws Exception {
		String resName = "/tcl/lang/cmd/case.test";
		tclTestResource(resName);
	}

}
