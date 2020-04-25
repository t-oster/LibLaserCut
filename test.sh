#!/usr/bin/env bash
set -e

# Performs some sanity and code cleanness tests. Intended to be run before every commit

echo "Checking for copryight header"
if ! [ -f copyrightheader ]
then
	echo "File 'copyrightheader' is missing"
	exit 1
fi

# filter out the lines that are not relevant for the license
#  - author lines: that contain year, author name(s) and at least one email adress enclosed in <> or ()
#  - blank lines: just " *" or similar
IGNORE_AUTHOR_LINE_REGEXP='^(  Copyright \([cC]\) 20[0-9]{2}.*[<\(].*@.*[>\)].*)$'

HEADERSIZE=$(grep -E -v "$IGNORE_AUTHOR_LINE_REGEXP" copyrightheader | wc -l)
ERRORS=0
for f in $(find src -name '*.java')
do
	if ! diff <(grep -E -v "$IGNORE_AUTHOR_LINE_REGEXP" "$f" | head -n "$HEADERSIZE") <(grep -E -v "$IGNORE_AUTHOR_LINE_REGEXP" copyrightheader)
	then
		echo "Copyright header mismatch on $f"
		ERRORS=1
	fi
done
exit $ERRORS
