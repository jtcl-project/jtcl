package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class LsetCmdTest extends TclCmdTest {

    @Test
	public void lsetCommand() throws Exception {
		String resName = "/tcl/lang/cmd/lset.test";
		tclTestResource(resName);
	}

    @Test
	public void lsetCompCommand() throws Exception {
		String resName = "/tcl/lang/cmd/lsetComp.test";
		tclTestResource(resName);
	}

}
