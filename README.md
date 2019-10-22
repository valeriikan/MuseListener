# MuseListener

Сlient application for retrieving raw data from Muse headband. This is a simplified connection-only version, for the advanced headband use please refer to the [original repository](https://github.com/garily/PEAC).


## How to use
1. Pair Muse to your smartphone manually via native bluetooth interface
2. Make sure that Muse is in searching mode (LED should blink). If it is not, break the connection between devices (Muse turn-on/turn-off is enough)
3. Open the application
4. Press "Scan" and wait until Muse will appear on the spinner list
5. Press "Connect"
6. Once device is connected, you can [maintain](https://github.com/valeriikan/MuseListener/blob/master/app/src/main/java/fi/oulu/muselistener/MainActivity.java#L264) the incoming data
<br>

- [x] Tested and works with OnePlus 6T (API 28), Samsung Galaxy A5 (API 24), Motorola MotoG3 (API 23)
- [ ] Device scanning does not work on LGE Nexus 5 (API 23)
