#!/bin/bash

version=2.0.0-a1

dir=/tmp/jtcl-$$.d
mkdir $dir

cp ../README             $dir
cp ../bin/jtcl*          $dir
cp ../target/jtcl-*.jar  $dir
cp ../licenses/*         $dir

cd $dir
zip ../jtcl-${version}.zip *
cd
rm -rf $dir
