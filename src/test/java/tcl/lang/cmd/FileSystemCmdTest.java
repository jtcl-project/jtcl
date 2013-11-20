package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

import java.util.Collections;

public class FileSystemCmdTest extends TclCmdTest {

    @Test
    public void fileSystemCommand() throws Exception {
        String resName = "/tcl/lang/cmd/fileSystem.test";
        tclTestResource(resName);
    }

}