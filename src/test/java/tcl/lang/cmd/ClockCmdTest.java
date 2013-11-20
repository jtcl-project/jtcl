package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class ClockCmdTest extends TclCmdTest {

    @Test
	public void clockCommand() throws Exception {
		String resName = "/tcl/lang/cmd/clock.test";
		tclTestResource(resName);
	}

}
