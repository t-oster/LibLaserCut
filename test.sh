#!/bin/bash
# Performs some sanity and code cleanness tests. Intended to be run before every commit

echo "Checking for copryight header"
if ! [ -f copyrightheader ]
then
	echo "File 'copyrightheader' is missing"
	exit 1
fi

# filter out the lines that are not relevant for the license
#  - author lines: that contain year, author name(s) and at least one email adress enclosed in <>
#  - blank lines: just " *" or similar
IGNORE_AUTHOR_LINE_REGEXP='^([ \*]*| \* Copyright \([cC]\) 20[0-9]{2}.*<.*@.*>.*)$'

HEADERSIZE=$(cat copyrightheader | egrep -v "$IGNORE_AUTHOR_LINE_REGEXP" | wc -l)
ERRORS=0
for f in $(find src -name '*.java')
do
	if ! diff <(cat $f | egrep -v "$IGNORE_AUTHOR_LINE_REGEXP" | head -n $HEADERSIZE) <(cat copyrightheader | egrep -v "$IGNORE_AUTHOR_LINE_REGEXP")
	then
		echo "Copyright header mismatch on $f"
		ERRORS=1
	fi
done
exit $ERRORS
