package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class LreverseCmdTest extends TclCmdTest {

    @Test
	public void lreverseCommand() throws Exception {
		String resName = "/tcl/lang/cmd/lreverse.test";
		tclTestResource(resName);
	}

}