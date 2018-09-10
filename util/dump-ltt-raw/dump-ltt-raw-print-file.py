#!/usr/bin/python3
import sys
import numpy as np
from matplotlib import pyplot as plt
import matplotlib
import math
import itertools

# Dump the contents of a LTT lasercutter raw print file resulting from "Print to File" with the windows print driver (or "Export file" with the visicut driver)
# USAGE: see end of this file
# LICENSE: You may use this under either the the MIT License (see LICENSE file) or the GNU Lesser Public License (Version 3 or later, see ../../COPYING.LESSER). 
# SPDX-License-Identifier: MIT OR LGPL-3.0+

def vecAbs(vec):
    return math.sqrt(np.dot(vec, vec))

class EngraveLine(object):
    def __init__(self, pos, rightToLeft, data, pitch, bitsPerPixel=1):
        self.pos = pos;
        self.rightToLeft = rightToLeft;
        self.data = data;
        #self.data=[0, 0, 0, 0, 0]
        self.bitsPerPixel = bitsPerPixel;
        self.pitch = pitch;

    def plot(self):
        data = self.data
        def iterateBits(byte):
            for i in [7, 6, 5, 4, 3, 2, 1, 0]:
                yield bool(byte & (1<<i))
        if self.bitsPerPixel == 1:
            data = itertools.chain.from_iterable([iterateBits(byte) for byte in data])
            data=list(data)
            # print("".join(["X" if x else "_" for x in data]))
        data = np.array(data)/(2**(self.bitsPerPixel-1))
        data = data.reshape((data.size, 1))
        if self.rightToLeft:
            data = np.flip(data, 0)
            self.pos = self.pos - np.array([self.pitch*data.size, 0]);
        # we interpret the pixels as "rectangle from (x,y) to (x+pitch, y+pitch)", which is slightly incorect but easier for debugging
        extent=(self.pos[0], self.pos[0]+(data.size)*self.pitch, self.pos[1], self.pos[1] + self.pitch)
        plt.imshow(X=data.T, cmap=('Reds' if self.rightToLeft else 'Blues'), norm=matplotlib.colors.Normalize(vmin=0, vmax=1), extent=extent);
        plt.autoscale(True)

def dump(filename, graph=False, showEngrave=False):
    f=open(filename,  'rb')
    s=f.read();
    pos=0
    def read(numChars=1):
        nonlocal pos
        y=s[pos:pos+numChars];
        pos += numChars;
        return y
    def rewind(numChars):
        nonlocal pos
        pos -= numChars
    def hasData():
        return pos < len(s)
    def readU8():
        return ord(read(1))

    def readUBytes(numBytes):
        value = 0;
        for i in range(numBytes):
            value = value * 256 + readU8();
        return value
    def readU16():
        return readUBytes(2);
    def readU32():
        return readUBytes(4);
    def readSigned32():
        value = readU32();
        if value >= (0x80000000 + 0xFFFFFFFF)/2:
            value = value - 2**32
        elif value > 0x80000000:
            print("something is strange with the number!!! Bug? Value = 0x80000000 + {}".format(value - 0x80000000))
        return value

    def readPrint(numChars=1):
        y=read(numChars)
        print(" hex: " + " ".join(["{:02X}".format(i) for i in y]))
        return y
    def readPrintXY():
        readPrint(8)
        rewind(8)
        x = readSigned32();
        y = readSigned32();
        print("= decoded xy: {} {}".format(x,y))
        return np.array([x,y]);

    dxBefore = np.zeros(2);
    dxUnityBefore = np.zeros(2);
    joinActive = False;
    v = 0;
    vEnd = 0;
    vEndBefore = 1e-99;
    aBefore = np.zeros(2)
    positions=[]
    speeds=[]
    engraveLines=[]
    pitch = float("NaN")
    assert read(3)==b'LTT'
    while hasData():
        start = readU8();
        cmd = readU8()
        if start == 0x50:
            cmd = 0x5000 + cmd;
        elif start == 0x5A:
            cmd = 0x5A00 + cmd;
        elif start!=0x1b:
            print(s)
            raise Exception("did not get known start of command, but {:02X}".format(start))

        commands={0x30: "Engrave line L2R", 0x31: "Engrave line R2L", 0x41: "Air Assist", 0x42: "end of file", 0x4A: "power", 0x4D: "job mode", 0x4E: "color", 0x50: "PPI", 0x53: "speed", 0x76:"version", 0x5041: "Pos. absolute", 0x5044:"Laser on", 0x5046: "End join", 0x504A: "Start join", 0x5045: "End speed", 0x5052: "Pos. relative", 0x5055: "Laser off", 0x5A41: "Focus?"}
        print("\n■ Command {:02X} ".format(cmd) + commands.get(cmd, ""))

        # two-byte commands:

        # FIXED-LENGTH COMMANDS:
        if cmd in [0x56, 0x5044, 0x5046, 0x504A, 0x5053, 0x5055]:
            # no data
            if cmd == 0x504A:
                joinActive=True;
            if cmd == 0x5046:
                joinActive=False
        elif cmd in [0x41, 0x43, 0x44, 0x4D, 0x4E, 0x4F, 0x61]:
            # 1 byte data
            value = readU8()
            print(value)
            if cmd == 0x44: # engrave DPI
                pitch = value
        elif cmd in [0x4A, 0x50, 0x51, 0x52]:
            # 2 byte data
            readPrint(2)
        elif cmd == 0x53:
            v=readU16()*0.1
            print("speed {}".format(v))
        elif cmd in [0x5045]:
            print("analyzing previous segment (very rough approximations!):")
            # TODO: these approximations are not very good because:
            # 1. vEnd is the maximum speed along the whole line segment, i.e. max(v, vBefore)
            # 2. the lasercutter has its own acceleration limit, so jumping from v=0 to v=100 does not mean that the acceleration is infinite.
            # dt, v, a, j as "backward differential"
            dv = vEndBefore*dxUnityBefore - vEnd*dxUnity
            dt = vecAbs(dx) / ((vEndBefore + vEnd)/2) + 1e-99
            a = dv/dt
            j = (a-aBefore)/dt
            print("dx {} v {} a {} jerk {} dt {}".format(dx, vEnd*dxUnity, a, j, dt))
            print("new segment:")
            # 2 byte unsigned, 1/10 percent
            # "end speed"
            vEndBefore = vEnd
            vEnd = readU16()*0.1
            print("{:.1f} %".format(vEnd))
        elif cmd == 0x42:
            read(2)
            print("end of file.")
            print("checksum")
            readPrint(2)
            print("length")
            readPrint(4)
            print("------------------")
        elif cmd == 0x76:
            # 3 byte data
            readPrint(3)
        elif cmd == 0x59:
            print(readU32())
        elif cmd == 0x5A41:
            print(readSigned32())
        elif cmd == 0x45:
            # 7 byte data
            readPrint(7)
        elif cmd in [0x5041, 0x5052]:
            # 8 byte data = X,y
            p = readPrintXY()
            if cmd == 0x5052 and joinActive:
                # TODO: currently, only jointCurve segments are saved for plotting, because the other velocities would be shown incorrectly.
                positions += [p]
                speeds += [vEnd if joinActive else v]
                assert speeds[-1]>0
                dxBefore = dx
                dx = p
                dxAbs = vecAbs(dx);
                dxUnity = dx/dxAbs
                aBefore = a
                dxBefore = dx
                dxUnityBefore = dxUnity;
            else:
                # abs. position command - reset everything
                a = np.zeros(2)
                dxUnity = np.zeros(2)
                dxUnityBefore = np.zeros(2)
                dx = np.zeros(2)
                dv = np.zeros(2)
                aBefore = np.zeros(2)
                vEnd = 0;
                dvBefore = 0;
                dt = 1e-42;

        elif cmd == 0x6E:
            # 8 byte data
            readPrint(8)
        elif cmd == 0x54:
            readPrint(16)
        elif cmd == 0x6C:
            readPrint(32)
        # DYNAMIC LENGTH COMMANDS:
        elif cmd == 0x46:
            # 1 byte length, $length byte data
            length=readU8()
            print(length)
            print(readPrint(length))
        elif cmd in [0x30, 0x31]:
            length=readU32() - 8;
            coord = readPrintXY();
            print("{} bytes of compressed data".format(length))
            def decodePackbytes(data, magic=0xC0):
                # decode compressed data, which is compressed similar to the TIFF Packbytes encoding:
                # either a raw byte < 0xC0 -> [byte]*1
                # or: [(runlength + 0xC0) byte]  -> [byte]*runlength
                decodedData = []
                runlength = 1;
                nextByteMustBeData = False
                for byte in data:
                    if byte >= magic and not nextByteMustBeData:
                        runlength = byte - magic
                        assert runlength > 0
                        nextByteMustBeData = True
                    else:
                        decodedData += [byte] * runlength
                        runlength = 1
                        nextByteMustBeData = False
                return decodedData
            assert decodePackbytes([0xC2, 0xFF, 0x04, 0xC1, 0xC1]) == ([0xFF, 0xFF, 0x04, 0xC1])
            rawData = read(length)
            #print(rawData)
            data = decodePackbytes(rawData)
            #print(data)
            print("{} bytes of decompressed data, average value {}".format(len(data), sum(data)/len(data)))
            engraveLines += [EngraveLine(pos=coord, rightToLeft=(cmd == 0x31), data=data, pitch=pitch)]
        else:
            raise Exception("unknown current command  {:02X}".format(cmd))
    print("done")
    if showEngrave:
        print("plotting engrave lines: ")
        for i in range(len(engraveLines)):
            el = engraveLines[i]
            el.plot()
            sys.stdout.write("\r" + str(100* i/len(engraveLines)) + "%                         ")
            sys.stdout.flush()
        print("")
        if len(engraveLines) == 0:
            plt.annotate("nothing engraved", [0.5,0.5])
        plt.xlabel("X in device pixels")
        plt.ylabel("Y in device pixels")
        plt.title("Engraving (blue: left->right, red: right->left)")
        print("close plot window to continue")
        plt.show()
    if not graph:
        return
    positions = np.array(positions)
    speeds = np.array(speeds)
    lens = np.sqrt(np.sum(positions**2,1))
    dxUnity = positions/np.vstack((lens, lens)).transpose()
    speedx = speeds*dxUnity[:, 0]
    speedy = speeds*dxUnity[:, 1]
    # the max. speed is the euclidean norm.
    t = np.cumsum(lens/speeds)
    t = np.cumsum(lens/speeds)
    plt.plot(t,speedx, 'r.-')
    plt.plot(t,speedy, 'b.-')
    plt.plot(t,  speeds,  'g.-')
    plt.xlabel("time (approximate) in [px/%], not in seconds!")
    plt.ylabel("speed")
    plt.title("speeds for 'joint curve' over approximate time")
    plt.legend(["x", "y", "v_end"])
    print("close plot window to continue")
    plt.show()
    plt.plot(np.cumsum(lens),speedx, 'r.-', label='x')
    plt.plot(np.cumsum(lens),speedy, 'b.-', label='y')
    plt.plot(np.cumsum(lens),  speeds,  'g.-', label='vEnd')
    #hideLegend=False
    #for jerk in np.linspace(.1,1.1,5):
        #plt.plot(np.cumsum(lens), jerk*.25*(np.cumsum(lens))**(2./3), 'm-', label=('_nolegend_' if hideLegend else 'constant jerk'))
        #hideLegend=True
    #hideLegend=False
    #for accel in np.linspace(.1,1,5):
        #plt.plot(np.cumsum(lens), accel*np.sqrt(2*1000*(33./2101.)**2*np.cumsum(lens)), 'c-', label=('_nolegend_' if hideLegend else 'constant accel.'))
        #hideLegend=True
    plt.title("speeds for 'joint curve' over length")
    plt.xlabel("total length")
    plt.ylabel("speed")
    plt.legend()
    plt.ylim([-100, 100])
    plt.show()

    ## a = dv/dt = dv/ds * ds/dt = dv/ds * v
    #a_approx = np.hstack((np.diff(speedx),0))/lens * speedx
    ## j = da/dt = da/ds * ds/dt = da/ds * v
    #j_approx = np.hstack((np.diff(a_approx),0))/lens * speedx
    #plt.plot(np.cumsum(lens), a_approx,  'g.-', label="approx local accel")
    #plt.plot(np.cumsum(lens), j_approx,  'm-', label="approx local jerk")
    #plt.xlabel("total length")
    #plt.legend()
    #plt.show()

    positionsAbsolute = np.cumsum(positions,0)
    plt.plot(positionsAbsolute[:, 0],  positionsAbsolute[:, 1],  'b.-')
    plt.axis("equal")
    plt.title("Curve for 'joint curve', neglecting absolute moves")
    print("close plot window to exit")
    plt.show()

if len(sys.argv) > 1:
    filename = sys.argv[1]
    dump(filename, graph="--graph" in sys.argv, showEngrave="--engrave" in sys.argv)
else:
    print("usage: dump-ltt-raw-print-file.py <printfile> [--graph] [--engrave]")
    sys.exit(1)
