# paraffin - build a JTcl application jar file

package require ziplib

set appDir tcl/app

proc cmdLine {} {
    set mkIndex 0
    if {[lindex $::argv 0] eq "-i"} {
        set ::argv [lrange $::argv 1 end]
        set mkIndex 1
    }
    if {[llength $::argv] < 3 || [llength $::argv] > 4} {
	puts "usage: [file tail $::argv0]  \[ -i \] app-name source-directory start-file \[ jarlib-directory \] \]"
	exit
    }
    set libdir ""
    foreach {app srcdir start libdir} $::argv {break}
    if {[file exists $app.jar]} {
        puts "file \"$app.jar\" already exists."
        exit
    }
    if {! [file isdirectory $srcdir]} {
	puts "invalid srcdir \"$srcdir\""
	exit
    }
    if {! [file isfile  [file join $srcdir $start]]} {
	puts "invalid start-file \"$start\""
	exit
    }
    if {[string length $libdir] && ! [file isdirectory $libdir]} {
	puts "jarlib-directory \"$libdir\" does not exists"
	exit
    }
    if {$mkIndex} {
        auto_mkindex $srcdir
    }
    mkJar $app $srcdir $start $libdir
}


proc mkJar {app srcdir start libdir} {
    set appjar [ziplib::openOutputZip $app.jar]
    set jtclJar [ziplib::getClassLocation [java::getinterp]]
    if {! [file isfile $jtclJar]} {
        puts "can't file jtcl.jar"
        exit
    }
    ziplib::mkZipDir $appjar META-INF
    ziplib::mkZipFile $appjar META-INF/MANIFEST.MF
    set mf [list \
        "Manifest-Version: 1.0\n" \
        "Main-Class: tcl.lang.AppShell\n" \
        "JTcl-Main: $::appDir/$start\n" \
    ]
    foreach line $mf {
        set s [java::new String $line]
        $appjar write [$s getBytes] 0 [$s length]
    }
    if {[string length $libdir]} {
        set jars [concat $jtclJar [glob [file join $libdir *]]]
    } else {
        set jars $jtclJar 
    }
    foreach jar $jars {
        if {! [file isfile $jar]} {
            continue
        }
        set jarin [ziplib::openInputZip $jar]
        ziplib::copyZip $jarin $appjar [list META-INF/ META-INF/MANIFEST.MF]
        $jarin close
    }
    ziplib::zipFromDir $appjar $srcdir $::appDir
    $appjar close
}

cmdLine
puts "created \"[lindex $argv 0].jar\""
