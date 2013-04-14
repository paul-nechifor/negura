#!/usr/bin/env python2

import colorsys

nColors = 12

for i in xrange(nColors):
    r, g, b = colorsys.hsv_to_rgb(i * (1.0 / nColors), 1, 0.8)
    print r"\definecolor{c%d}{rgb}{%.3f,%.3f,%.3f}" % (i+1, r, g, b)

