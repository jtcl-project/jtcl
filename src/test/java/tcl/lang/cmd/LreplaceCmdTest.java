package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class LreplaceCmdTest extends TclCmdTest {

    @Test
	public void lreplaceCommand() throws Exception {
		String resName = "/tcl/lang/cmd/lreplace.test";
		tclTestResource(resName);
	}

}
