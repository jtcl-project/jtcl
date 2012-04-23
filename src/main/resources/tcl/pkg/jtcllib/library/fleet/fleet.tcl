package provide fleet 0.1
package require java
java::load tcl.pkg.fleet.FleetExt

namespace eval ::fleet {}

proc ::fleet::initResults {reply} {
   set status [dict get $reply status]
   set value [dict get $reply value]
   puts "$status $value"

}

proc ::fleet::processReply {reply} {
   set status [dict get $reply status]
   set value [dict get $reply value]
   if {$status eq "FAIL"} {
        puts $value
   } else {
       set fleet [dict get $reply fleet]
       upvar #0 ::fleet::${fleet}::pars pars
       set memberName [dict get $reply member]
       set value [dict get $reply value]
       $pars(calcProc) $value
       incr pars(nResults) 
       if {$pars(nResults) == $pars(nMessages)} {
            $pars(doneProc)
       } else {
           set count [dict get $reply count]
           if {$count < $pars(lowWater)} {
               set newCount [expr {$pars(highWater)-$count}]
               sendMessagesToMember $fleet $memberName $newCount
           }
       }
   }
}
proc ::fleet::sendMessages {fleet} {
    upvar #0 ::fleet::${fleet}::pars pars
    after cancel "sendMessages $fleet"
    for {set i 0} {$i < $pars(nMembers)} {incr i} {
        set member $pars(members,$i)
        set count [$pars(fleet) count -messages $member]
        set newCount [expr {$pars(highWater)-$count}]
        if {$count < $pars(lowWater)} {
            sendMessagesToMember $fleet $member $newCount
        }
    }
    after 200 [info level 0]
}

proc ::fleet::sendMessagesToMember {fleet member newCount} {
    upvar #0 ::fleet::${fleet}::pars pars
    for {set j 0} {$j < $newCount} {incr j} {
        if {$pars(messageNum) >= $pars(nMessages)} {
             return 1
        }
        $pars(messageProc) $pars(fleet) $member $pars(messageNum)
        incr pars(messageNum)
    }
}

proc ::fleet::initFleet {nMembers {script {}} } {
   set fleet [fleet create]
   upvar #0 ::fleet::${fleet}::pars pars
   array set pars {
       nMembers 2
       lowWater 50
       highWater 100
       nMessages 10000
       updateAt 500
       nResults 0
       messageNum 0
       messageProc messageProc
       calcProc calcProc
       doneProc reportProc
   }
   set pars(nMembers) $nMembers
   set pars(fleet) $fleet

   for {set i 0} {$i < $pars(nMembers)} {incr i} {
       set pars(members,$i) [$pars(fleet) member]
       if {$script ne {}} {
           $fleet tell $pars(members,$i) $script -reply ::fleet::initResults
       }
   }
   return $fleet
}

proc ::fleet::jproc {fleet args} {
    eval ::hyde::jproc $args
    set procName [lindex $args 1]
    set procArgs [lindex $args 2]
    set procArgs2 [list]
    foreach "type arg" $procArgs {
        lappend procArgs2 $arg
    }
    set procArgs3 ""
    foreach "type arg" $procArgs {
        append procArgs3 "\$$arg "
    }
    set bytes $::hyde::cacheCode(hyde/${procName}Cmd)
    $fleet tell * "java::defineclass $bytes"
    set jproc {
        proc $procName \{$procArgs2\} {
            return [java::call hyde.${procName}Cmd $procName $procArgs3]
        }
    }
    set jproc [subst -nocommand $jproc]
    puts $jproc
    $fleet tell * $jproc
}

proc ::fleet::configure {fleet args} {
   upvar #0 ::fleet::${fleet}::pars pars
   foreach "name value" $args {
       set name [string trimleft $name "-"]
       set pars($name) $value
   } 
}
