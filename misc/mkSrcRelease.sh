#!/bin/bash

echo "deprecated - used 'release_build.sh' instead"
echo "^C to abort (or return to continue)"
read junk

# creates jtcl src zip file

if [ $# -ne 1 ] ; then
    echo usage: `basename $0` version
    exit
fi

version=$1

if [ ! -f  ../target/jtcl-$version.jar ] ; then
    echo "missing ../target/jtcl-$version.jar"
    exit
fi

dir=/tmp/jtcl-$$.d
mkdir -p $dir/jtcl-$version

cp -r ../* $dir/jtcl-$version/
cp ../.??* $dir/jtcl-$version/
cd $dir
rm -rf jtcl-$version/.hg
rm -rf jtcl-$version/.hgignore
rm -rf jtcl-$version/target
rm -rf jtcl-$version/bldTJC
rm -rf jtcl-$version/tjc*jar
zip -r ../jtcl-${version}-src.zip *
cd ..
ls -alt `pwd`/jtcl-${version}-src.zip

rm -rf $dir
