# ip-geoloc

A Clojure library for IP geo-location.

It wraps the MaxMind GeoLite2 and provide support for different levels of resolution.

The MaxMind GeoLite2 database is available here: http://dev.maxmind.com/geoip/geoip2/geolite2/
and it is distributed under [Creative Commons Attribution-ShareAlike 3.0 Unported License](http://creativecommons.org/licenses/by-sa/3.0/)

To meet the attribution license requirements please include this in your product/project.

> **This product includes GeoLite2 data created by MaxMind, available from [http://www.maxmind.com](http://www.maxmind.com)**.

It supports the following feature:

  * IP geo-location with city level details
  * IPv4 and IPv6 support
  * 3 levels of output details: just lat/lng coordinates, concise, full.
  * Simplified database update
  * Fully automated database update
  * caching of common IPs

## Usage

FIXME

## License

Copyright © 2015 Bruno Bonacci

Distributed under the Apache License v 2.0 (http://www.apache.org/licenses/LICENSE-2.0)
