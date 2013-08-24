all:
	cd NeguraCommon; ant
	cd NeguraClient; ant
	cd NeguraServer; ant

clean:
	cd NeguraCommon; ant clean
	cd NeguraClient; ant clean
	cd NeguraServer; ant clean
