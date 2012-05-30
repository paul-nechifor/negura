#!/usr/bin/env python2

from subprocess import Popen as run
import time, os

def register(n):
    params = ['gnome-terminal', '--geometry', '70x25', '--command']
    os.system("rm -fr /home/p/util; mkdir /home/p/util")
    time.sleep(0.5)
    for i in xrange(n):
        print "Starting", i
        out = open("out.sh", "w")
#java -Xms4M -Xmx20M -cp ../NeguraClient/dist/Negura-*Linux64.jar negura.client.Main autoreg %d 127.0.0.1 5000
        out.write("""#!/bin/bash
java -cp ../NeguraClient/dist/Negura-*Linux64.jar negura.client.Main autoreg %d 127.0.0.1 5000
sleep 9h
""" % i)
        out.close()
        os.system("chmod +x out.sh")
        run(params + ["./out.sh"])
        time.sleep(1)
    time.sleep(1)
    os.system("rm -fr out.sh")

if __name__ == "__main__":
    register(6)
