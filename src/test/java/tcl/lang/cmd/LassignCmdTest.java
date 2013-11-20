package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class LassignCmdTest extends TclCmdTest {

    @Test
	public void lassignCommand() throws Exception {
		String resName = "/tcl/lang/cmd/lassign.test";
		tclTestResource(resName);
	}

}