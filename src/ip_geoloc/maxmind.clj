(ns ip-geoloc.maxmind
  (:require [clojure.java.io :as io])
  (:require [pandect.algo.md5 :as hash])
  (:require [clj-http.client :as http])
  (:import  [com.maxmind.geoip2 DatabaseReader DatabaseReader$Builder]
            [com.maxmind.geoip2.model CityResponse]
            [com.maxmind.geoip2.record City Country Subdivision
             Postal Location Continent RepresentedCountry Traits]))





(def ^:dynamic *database-url* "http://geolite.maxmind.com/download/geoip/database/GeoLite2-City.mmdb.gz")
(def ^:dynamic *database-md5-url* "http://geolite.maxmind.com/download/geoip/database/GeoLite2-City.md5")


(defprotocol ToClojure
  (->clojure [data] "convert a given object into a Clojure data structure"))


(extend-protocol ToClojure

  CityResponse
  (->clojure [data]
    {:continent (->clojure (.getContinent data))
     :country (->clojure (.getCountry data))
     :registered-country (->clojure (.getRegisteredCountry data))
     :represented-country (->clojure (.getRepresentedCountry data))
     :subdivistions (->clojure (.getSubdivisions data))
     :most-specific-subdivision (->clojure (.getMostSpecificSubdivision data))
     :least-specific-subdivision (->clojure (.getLeastSpecificSubdivision data))
     :city (->clojure (.getCity data))
     :postal (->clojure (.getPostal data))
     :location (->clojure (.getLocation data))
     :traits (->clojure (.getTraits data))})

  Continent
  (->clojure [data]
    {:code  (.getCode data)})

  Country
  (->clojure [data]
    {:name  (.getName data)
     :id    (.getGeoNameId data)
     :isoCode (.getIsoCode data)
     :confidence (.getConfidence data)
     :names (.getNames data)})

  RepresentedCountry
  (->clojure [data]
    {:name  (.getName data)
     :id    (.getGeoNameId data)
     :isoCode (.getIsoCode data)
     :confidence (.getConfidence data)
     :names (.getNames data)
     :type (.getType data)})

  Subdivision
  (->clojure [data]
    {:name  (.getName data)
     :id    (.getGeoNameId data)
     :isoCode (.getIsoCode data)
     :confidence (.getConfidence data)
     :names (.getNames data)})

  City
  (->clojure [data]
    {:name  (.getName data)
     :id    (.getGeoNameId data)
     :names (.getNames data)})

  Postal
  (->clojure [data]
    {:code (.getCode data)
     :confidence (.getConfidence data)})

  Location
  (->clojure [data]
    {:accuracy-radius (.getAccuracyRadius data)
     :average-income  (.getAverageIncome data)
     :latitude (.getLatitude data)
     :longitude (.getLongitude data)
     :metro-code (.getMetroCode data)
     :population-density (.getPopulationDensity data)
     :timezone (.getTimeZone data)
     })


  Traits
  (->clojure [data]
    {:autonomous-system-number (.getAutonomousSystemNumber data)
     :autonomousSystemOrganization (.getAutonomousSystemOrganization data)
     :domain (.getDomain data)
     :ip-address (.getIpAddress data)
     :isp (.getIsp data)
     :organization (.getOrganization data)
     :user-type (.getUserType data)
     :anonymous-proxy? (.isAnonymousProxy data)
     :satellite-provider? (.isSatelliteProvider data)})

  java.util.ArrayList
  (->clojure [data]
    (apply vector
           (doall (map ->clojure data))))

)


(defprotocol GeoIpProvider
  (init [this]
    "Initialise the provider")

  (close [this]
    "Close the provider")

  (full-geo-lookup [this ip]
    "Get a full geo IP lookup")

  (geo-lookup [this ip]
    "get a concise geo IP lookup")

  (coordinates [this ip]
    "get only coordinates info."))


(deftype MaxMind2 [db-path db]

  GeoIpProvider

  (init [this]
    (let [db (if (.endsWith db-path ".gz")
               (java.util.zip.GZIPInputStream.
                (io/input-stream db-path))
               (io/input-stream db-path))]
      (MaxMind2. db-path (.build (DatabaseReader$Builder. db)))))

  (close [this]
    (.close db)
    nil)

  (full-geo-lookup [this ip]
    (->clojure (.city db (java.net.InetAddress/getByName ip))))

  (geo-lookup [this ip]
    (let [data (.city db (java.net.InetAddress/getByName ip))
          continent (->clojure (.getContinent data))
          country   (->clojure (.getCountry data))
          subdivs   (apply vector (map :name (->clojure (.getSubdivisions data))))
          city      (->clojure (.getCity data))
          postal    (->clojure (.getPostal data))
          location  (->clojure (.getLocation data))]
      {:continent (:code continent)
       :countryIsoCode (:isoCode country)
       :country (:name country)
       :subdivistions subdivs
       :city (:name city)
       :postCode (:code postal)
       :latitude (:latitude location)
       :longitude (:longitude location)}))

  (coordinates [this ip]
    (->clojure (.getLocation (.city db (java.net.InetAddress/getByName ip))))))


(defn- gunzip-file [in out]
  (with-open [input (java.util.zip.GZIPInputStream.
                     (io/input-stream  (io/file in)))
              output (io/output-stream (io/file out))]
    (io/copy input output)))


(defn- ensure-dirs [path]
  (.mkdirs (.getParentFile (io/file path))))


(defn download-db [from to]
  (ensure-dirs to)
  (io/copy
   (:body (http/get from {:as :stream}))
   (io/file to)))


(defn check-db [md5 file]
  (= md5 (hash/md5-file file)))


(defn fetch-db-md5 [url]
  (:body (http/get url {:as :text})))


(defn update-db [{:keys [database-url database-md5-url database-folder]}]
  (let [dbgz (io/file database-folder "GeoLite2-City.mmdb.gz")
        db   (io/file database-folder "GeoLite2-City.mmdb")]
    (download-db database-url dbgz)
    (gunzip-file dbgz db)
    (check-db (fetch-db-md5 database-md5-url) db)))

(comment

  (download-db *database-url* "/tmp/GeoLite2-City.mmdb.gz")
  (gunzip-file "/tmp/GeoLite2-City.mmdb.gz" "/tmp/GeoLite2-City.mmdb")

  (check-db
   (fetch-db-md5 *database-md5-url*)
   "/tmp/GeoLite2-City.mmdb")

  (update-db {:database-url *database-url*
              :database-md5-url *database-md5-url*
              :database-folder  "/tmp/dir2"})
  )
