package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class LindexCmdTest extends TclCmdTest {

    @Test
	public void lindexCommand() throws Exception {
		String resName = "/tcl/lang/cmd/linsert.test";
		tclTestResource(resName);
	}

}
