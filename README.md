# ip-geoloc

**WORK IN PROGRESS**

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

Add the dependency in your `project.clj`

    [com.brunobonacci/ip-geoloc "0.1.0-SNAPSHOT"]

then require the namespace:

```Clojure
(require '[ip-geoloc.core :refer :all])
```

You can download a *MaxMind GeoLite2 City* database from this page:
http://dev.maxmind.com/geoip/geoip2/geolite2/ and put it in a folder
of choice. As you'll see this can be done automatically, the library
takes care of downloading the latest copy of the database for you, but
if you don't want the library download the DB automatically (maybe
because the production environment doesn't have outbound internet
connection) you can specify a local `db-file`.


```Clojure
;; create a provider which point to the database you downloaded
(def provider (create-provider {:db-file "/tmp/GeoLite2-City.mmdb"}))

;; start the provider and connect to the database
(def provider (start provider))

;; now you can start looking up for IPs in three formats

;; The concise format
(geo-lookup provider "8.8.8.8")
;;=> {:continent "NA",
;;    :countryIsoCode "US",
;;    :country "United States",
;;    :subdivistions ["California"],
;;    :city "Mountain View",
;;    :postCode "94040",
;;    :latitude 37.386,
;;    :longitude -122.0838}


;; the coordinates only
(coordinates provider "8.8.8.8")
;;=> {:accuracy-radius nil,
;;    :average-income nil,
;;    :latitude 37.386,
;;    :longitude -122.0838,
;;    :metro-code 807,
;;    :population-density nil,
;;    :timezone "America/Los_Angeles"}


;; the full format
(full-geo-lookup provider "8.8.8.8")
;;=> {:continent {:code "NA"},
;;    :most-specific-subdivision
;;    {:name "California",
;;     :id 5332921,
;;     :isoCode "CA",
;;     :confidence nil,
;;     :names
;;     {"de" "Kalifornien", "ru" "Калифорния", "pt-BR" "Califórnia", "ja" "カリフォルニア州", "en" "California", "fr" "Californie", "zh-CN" "加利福尼亚州", "es" "California"}},
;;    :city
;;    {:name "Mountain View",
;;     :id 5375480,
;;     :names
;;     {"de" "Mountain View", "ru" "Маунтин-Вью", "ja" "マウンテンビュー", "en" "Mountain View", "fr" "Mountain View", "zh-CN" "芒廷维尤"}},
;;    :subdivistions
;;    [{:name "California",
;;      :id 5332921,
;;      :isoCode "CA",
;;      :confidence nil,
;;      :names
;;      {"de" "Kalifornien", "ru" "Калифорния", "pt-BR" "Califórnia", "ja" "カリフォルニア州", "en" "California", "fr" "Californie", "zh-CN" "加利福尼亚州", "es" "California"}}],
;;    :registered-country
;;    {:name "United States",
;;     :id 6252001,
;;     :isoCode "US",
;;     :confidence nil,
;;     :names
;;     {"de" "USA", "ru" "Сша", "pt-BR" "Estados Unidos", "ja" "アメリカ合衆国", "en" "United States", "fr" "États-Unis", "zh-CN" "美国", "es" "Estados Unidos"}},
;;    :least-specific-subdivision
;;    {:name "California",
;;     :id 5332921,
;;     :isoCode "CA",
;;     :confidence nil,
;;     :names
;;     {"de" "Kalifornien", "ru" "Калифорния", "pt-BR" "Califórnia", "ja" "カリフォルニア州", "en" "California", "fr" "Californie", "zh-CN" "加利福尼亚州", "es" "California"}},
;;    :represented-country
;;    {:name nil, :id nil, :isoCode nil, :confidence nil, :names {}, :type nil},
;;    :location
;;    {:accuracy-radius nil,
;;     :average-income nil,
;;     :latitude 37.386,
;;     :longitude -122.0838,
;;     :metro-code 807,
;;     :population-density nil,
;;     :timezone "America/Los_Angeles"},
;;    :country
;;    {:name "United States",
;;     :id 6252001,
;;     :isoCode "US",
;;     :confidence nil,
;;     :names
;;     {"de" "USA", "ru" "Сша", "pt-BR" "Estados Unidos", "ja" "アメリカ合衆国", "en" "United States", "fr" "États-Unis", "zh-CN" "美国", "es" "Estados Unidos"}},
;;    :postal {:code "94040", :confidence nil},
;;    :traits
;;    {:user-type nil,
;;     :organization nil,
;;     :isp nil,
;;     :satellite-provider? false,
;;     :anonymous-proxy? false,
;;     :autonomousSystemOrganization nil,
;;     :autonomous-system-number nil,
;;     :domain nil,
;;     :ip-address "8.8.8.8"}}


;; when you are done you can stop it
(stop provider)

```

You can also use the convenience functions which use the global `*provider*`.

```Clojure
;; initialize the provider
(init-provider! {:db-file "/tmp/GeoLite2-City.mmdb"})

;; start it
(start-provider!)

;; now you can resolve the IP in the same way
;; without having to pass the provider
(geo-lookup "8.8.8.8")
;;=> {:continent "NA",
;;    :countryIsoCode "US",
;;    :country "United States",
;;    :subdivistions ["California"],
;;    :city "Mountain View",
;;    :postCode "94040",
;;    :latitude 37.386,
;;    :longitude -122.0838}


;; when you are done you can stop it
(stop-provider!)

```

## License

Copyright © 2015 Bruno Bonacci

Distributed under the Apache License v 2.0 (http://www.apache.org/licenses/LICENSE-2.0)
