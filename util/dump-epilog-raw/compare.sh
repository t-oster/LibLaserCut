#!/bin/bash
# compare two raw epilog print files
# usage: compare.sh fileA.prn fileB.prn

# LICENSE: You may use this under either the the MIT License (see LICENSE file) or the GNU Lesser Public License (Version 3 or later, see ../../COPYING.LESSER).
# SPDX-License-Identifier: MIT OR LGPL-3.0+

set -e
dumptool="$(dirname "$0")/dump-epilog-raw-print-file.py"
diff <($dumptool "$1") <($dumptool "$2")
