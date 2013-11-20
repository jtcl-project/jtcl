package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class TimerCmdTest extends TclCmdTest {

    @Test
	public void timerCommand() throws Exception {
		String resName = "/tcl/lang/cmd/timer.test";
		tclTestResource(resName);
	}

}