#!/usr/bin/python3
"""
Dump the contents of an Epilog lasercutter raw print file resulting from "Print to File" with the Epilog windows print driver (or "Export file" with the visicut driver)
"""
# USAGE: see end of this file
# LICENSE: You may use this under either the the MIT License (see ../dump-ltt-raw/LICENSE file) or the GNU Lesser Public License (Version 3 or later, see ../../COPYING.LESSER). 
# SPDX-License-Identifier: MIT OR LGPL-3.0+

import sys
import os

"""
format raw HPGL string.
Currently, this is only very rough and doesn't always exactly match the command boundaries.
"""
def dump(filename):
    f=open(filename,  'rb')
    output=''
    for cmd in f.read().split(b'\x1b'):
        # new line for each HPGL special command. These start with ESC 0x1B.
        output += '\\x1b'
        contents = str(cmd)[2:-1]
        # newline and indentation for each normal HPGL command. These end with ;
        if ";" in contents:
            contents = contents.replace(";", ";\r\n\t")
        output += contents
        output += '\r\n';
    return output


if len(sys.argv) > 1:
    filename = sys.argv[1]
    if filename == "--all":
        for f in os.listdir("."):
            if os.path.isfile(f) and f.endswith(".prn"):
                dump_file = open(f + ".dump",  'w')
                dump_file.write(dump(f))
                dump_file.close()
    else:
        print(dump(filename))
else:
    print("usage: dump-epilog-raw-print-file.py <printfile>")
    sys.exit(1)

