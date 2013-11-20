package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class FormatCmdTest extends TclCmdTest {

    @Test
	public void formatCommand() throws Exception {
		String resName = "/tcl/lang/cmd/format.test";
		tclTestResource(resName);
	}

}
