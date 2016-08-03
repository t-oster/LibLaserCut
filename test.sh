#!/bin/bash
# Performs some sanity and code cleanness tests. Intended to be run before every commit

echo "Checking for copryight header"
if ! [ -f copyrightheader ]
then
	echo "File 'copyrightheader' is missing"
	exit 1
fi
HEADERSIZE=$(wc -l < copyrightheader)
# filter out the line that contains year, author name(s) and at least one email adress enclosed in <>
IGNORE_AUTHOR_LINE_REGEXP='^ \* Copyright \(C\) 20[0-9]{2}.*<.*@.*>.*$'
ERRORS=0
for f in $(find src -name '*.java')
do
	if ! diff <(cat $f | head -n $HEADERSIZE | egrep -v "$IGNORE_AUTHOR_LINE_REGEXP") <(cat copyrightheader | egrep -v "$IGNORE_AUTHOR_LINE_REGEXP")
	then
		echo "Copyright header mismatch on $f"
		ERRORS=1
	fi
done
exit $ERRORS
