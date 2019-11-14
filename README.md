# LibLaserCut #
This is a library intended to provide suppport
for Lasercutters on any platform.

Currently it supports most Epilog Lasers,
the current LAOS board. (www.laoslaser.org),
the SmoothieBoard (www.smoothieware.org),
K40, generic GRBL based boards,
and some untested work-in-progress drivers like the Roland iModela and the Lasersaur.

## Tested/Supported devices ##

Manufacturer     | Model                   |  Interface       |  Driver               | Notes
------------------------|-------------------------|--------------------|-----------------------|----------
Epilog                  | Zing                       | Ethernet       | EplilogZing        |
Epilog                  | Helix                      | Ethernet       | EpilogHelix        |
Smoothieware   | Smoothieboard   | Ethernet       | SmoothieBoard |

**If you run VisiCut successfully and your device is not yet listed, please edit this file and provide a pull request, so this stays up to date **



It was created for VisiCut (http://visicut.org)
but you are invited to use it for your own programs.

If your Lasercutter is not supported, please contribute by implementing
your driver as a subclass of the LaserCutter.java class.
