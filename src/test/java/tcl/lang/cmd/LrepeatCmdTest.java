package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class LrepeatCmdTest extends TclCmdTest {

    @Test
	public void lrepeatCommand() throws Exception {
		String resName = "/tcl/lang/cmd/lrepeat.test";
		tclTestResource(resName);
	}

}