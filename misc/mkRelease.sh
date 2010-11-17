#!/bin/bash

version=2.0.0-a1

dir=/tmp/jtcl-$$.d
mkdir -p $dir/jtcl-$version

cp ../README             $dir/jtcl-$version
cp ../bin/*              $dir/jtcl-$version
cp ../target/jtcl-*.jar  $dir/jtcl-$version
cp ../licenses/*         $dir/jtcl-$version

cd $dir
zip -r jtcl-${version}.zip jtcl-$version/*
mv jtcl-${version}.zip /tmp
cd
rm -rf $dir
