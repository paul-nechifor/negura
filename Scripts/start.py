#!/usr/bin/env python2

from subprocess import Popen as run
import time, os

def register6():
    params = ['gnome-terminal', '--geometry', '70x25', '--command']
    os.system("rm -fr /home/p/util; mkdir /home/p/util")
    time.sleep(0.5)
    for i in xrange(6):
        out = open("out.sh", "w")
        out.write("""#!/bin/bash
java -Xms2M -Xmx10M -cp ../NeguraClient/dist/Negura-*Linux64.jar negura.client.Main autoreg %d 127.0.0.1 5000
sleep 9h
""" % i)
        out.close()
        os.system("chmod +x out.sh")
        run(params + ["./out.sh"])
        time.sleep(1)
    time.sleep(1)
    os.system("rm -fr out.sh")

if __name__ == "__main__":
    register6()
