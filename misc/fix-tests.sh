#!/bin/bash

# change test files in bulk 
# jtcl doesn't have a 'compiling' mode, to change occurances in error messages 
# from 'while compiling' to  'while executing'

cd ../src/test/resources/tcl/lang/cmd
for testfile in *.test ; do
    sed -e 's/while compiling/while executing/' <$testfile >$testfile.tmp
    rm $testfile
    mv $testfile.tmp $testfile
done
