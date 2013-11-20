package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class JoinCmdTest extends TclCmdTest {

    @Test
	public void joinCommand() throws Exception {
		String resName = "/tcl/lang/cmd/join.test";
		tclTestResource(resName);
	}

}
