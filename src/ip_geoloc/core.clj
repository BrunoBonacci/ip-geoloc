(ns ip-geoloc.core
  (:require [ip-geoloc.maxmind :as mmind])
  (:import [ip_geoloc.maxmind MaxMind2]))


(defn init-provider [type db-file]
  (case type
    :max-mind2 (mmind/init (MaxMind2. db-file nil))))


(defn full-geo-lookup [provider ip]
  (mmind/full-geo-lookup provider ip))


(defn coordinates [provider ip]
  (mmind/coordinates provider ip))


(defn geo-lookup [provider ip]
  (mmind/geo-lookup provider ip))



(comment

  (def ip1 "220.181.108.182")
  (def ip2 "104.131.115.133")
  (def ip3 "23.232.137.112")

  (def file "/tmp/GeoLite2-City.mmdb.gz")

  (def prvd (init-provider :max-mind2 file))

  (geo-lookup prvd ip1)
  (geo-lookup prvd ip2)
  (geo-lookup prvd ip3)
  )
