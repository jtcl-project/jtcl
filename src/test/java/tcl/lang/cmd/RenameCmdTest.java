package tcl.lang.cmd;

import tcl.lang.TclCmdTest;

public class RenameCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		// FIXME: rename.test altered to run test "rename-6.1 {old code invalidated (epoch incremented) when cmd with compile proc is renamed } {"
		// a difference between C/Tcl on-the-fly bytecode compiler and JTcl's pure interpreter??
		// original test looks was this (from rename-5.1 to end of file)  
	
//		test rename-6.1 {old code invalidated (epoch incremented) when cmd with compile proc is renamed } {
//		    proc x {} {
//		        set a 123
//		        set b [incr a]
//		    }
//		    x
//		    rename incr incr.old
//		    proc incr {} {puts "new incr called!"}
//		    catch {x} msg
//		    set msg
//		} {wrong # args: should be "incr"}
//
//		if {[info commands incr.old] != {}} {
//		    catch {rename incr {}}
//		    catch {rename incr.old incr}
//		}
//		::tcltest::cleanupTests
//		return
	
		String resName = "/tcl/lang/cmd/rename.test";
		tclTestResource(resName);
	}
}
