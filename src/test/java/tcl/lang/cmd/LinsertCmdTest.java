package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class LinsertCmdTest extends TclCmdTest {

    @Test
	public void linsertCommand() throws Exception {
		String resName = "/tcl/lang/cmd/linsert.test";
		tclTestResource(resName);
	}

}
