#!/bin/bash

echo "deprecated - used 'release_build.sh' instead"
echo "^C to abort (or return to continue)"
read junk

# creates jtcl binary distribution after building with "ant compile; ant package"

version=$1

if [ ! -f  ../jtcl-$version.jar ] ; then
    echo "missing ../jtcl-$version.jar"
    exit
fi

dir=/tmp/jtcl-$$.d
mkdir -p $dir/jtcl-$version

for f in ../src/main/scripts/* ; do
    f2=`basename $f`
    sed -e "s/\${project.version}/$version/" <$f >$dir/jtcl-$version/$f2
    chmod +x $dir/jtcl-$version/$f2
done
cp ../src/main/readmes/*          $dir/jtcl-$version
cp ../src/main/licenses/*         $dir/jtcl-$version
cp ../jtcl-$version.jar           $dir/jtcl-$version

cd $dir
zip -r jtcl-${version}-bin.zip jtcl-$version/*
mv jtcl-${version}-bin.zip /tmp
cd
rm -rf $dir
ls -alt /tmp/jtcl-${version}-bin.zip
