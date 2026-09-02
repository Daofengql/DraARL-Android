# Third-Party Notices

## Amateur Radio CQ and ITU zone boundaries

The compressed `cqzones` and `ituzones` resources under `app/src/main/res/raw/`
are derived from [HB9HIL/hamradio-zones-geojson](https://github.com/HB9HIL/hamradio-zones-geojson).
The static `cq_zone_reference.png` and `itu_zone_reference.png` images are
generated from the same boundary data. They are application reference graphics,
not official CQ Magazine or ITU maps.

They are distributed under the MIT License:

```text
Copyright (c) 2024 HB9HIL

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Near-real-time ionospheric propagation maps

The propagation conditions screen loads the current MUF(3000 km) and foF2 maps
from [prop.kc2g.com](https://prop.kc2g.com/). The maps are generated about every
15 minutes from ionosonde observations distributed through
[GIRO](https://giro.uml.edu/) and [INGV](https://ingv.it/). The service is made
possible by funding from the [World Wide Radio Operators Foundation](https://wwrof.org/).

The map service source code is available at
[arodland/prop](https://github.com/arodland/prop/). DraARL loads the current map
images from the service and does not redistribute them in the application
package. Data age, availability, and coverage depend on the upstream service
and contributing ionosonde operators.
