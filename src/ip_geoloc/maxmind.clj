(ns ip-geoloc.maxmind
  (:require [clojure.java.io :as io]
            [safely.core :refer [safely sleep]])
  (:require [pandect.algo.md5 :as hash])
  (:require [clj-http.client :as http])
  (:import com.maxmind.geoip2.DatabaseReader$Builder
           com.maxmind.geoip2.exception.AddressNotFoundException
           com.maxmind.geoip2.model.CityResponse
           [com.maxmind.geoip2.record City Continent
            Country Location Postal RepresentedCountry Subdivision Traits]))

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
    "get only coordinates info.")

  (database-location [this]
    "Returns the path of the current database"))


(defmacro if-ip-exists [& body]
  `(try
     ~@body
     (catch AddressNotFoundException x#
       nil)))


(deftype MaxMind2 [db-path db]

  GeoIpProvider

  (init [this]
    (let [db-path (if (string? db-path) db-path (.getAbsolutePath db-path))
          db (if (.endsWith db-path ".gz")
               (java.util.zip.GZIPInputStream.
                (io/input-stream db-path))
               (io/input-stream db-path))]
      (MaxMind2. db-path (.build (DatabaseReader$Builder. db)))))

  (close [this]
    (when db
      (.close db))
    nil)

  (full-geo-lookup [this ip]
    (if-ip-exists (->clojure (.city db (java.net.InetAddress/getByName ip)))))

  (geo-lookup [this ip]
    (when-let [data (if-ip-exists (.city db (java.net.InetAddress/getByName ip)))]
      (let [continent (->clojure (.getContinent data))
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
         :longitude (:longitude location)})))

  (coordinates [this ip]
    (if-ip-exists
     (->clojure
      (.getLocation (.city db (java.net.InetAddress/getByName ip))))))

  (database-location [this]
    db-path))


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


(defn check-db [md5 afile]
  (when afile
    (= md5
       (safely
        (hash/md5-file afile)
        :on-error
        :default nil))))


(defn fetch-db-md5 [url]
  (:body (http/get url {:as :text})))


(defn update-db [{:keys [database-url database-md5-url database-folder]}]
  (let [dbgz (io/file database-folder "GeoLite2-City.mmdb.gz")
        db   (io/file database-folder
                      (str "GeoLite2-City.mmdb." (System/currentTimeMillis)))]
    (download-db database-url dbgz)
    (gunzip-file dbgz db)
    (if (check-db (fetch-db-md5 database-md5-url) db)
      (do
        (let [newdb (io/file (str (.getAbsolutePath db) ".ok"))]
          (.renameTo db newdb)
          newdb))
      (safely (.delete db) :on-error :ignore true))))


(defn find-last-available-db [{:keys [database-folder]}]
  (->> (io/file database-folder)
       (.list)
       (filter #(re-matches #"GeoLite2-City\.mmdb\.\d+\.ok" %))
       (sort)
       (last)))


(defn update-db-if-needed [current-db-file
                           {:keys [database-url database-md5-url
                                   database-folder] :as cfg}]
  (when-not (check-db (fetch-db-md5 database-md5-url) current-db-file)
    (update-db cfg)))


(comment
  {:database-file nil
   :database-folder "/tmp/maxmind"
   :auto-update true
   :auto-update-check-time (* 3 60 60 1000) ;; every 3 hours
   :provider (atom nil)
   :update-thread (atom nil)})


(defn update-db! [{:keys [provider] :as config}]
  (safely
   (let [old-provider    @provider
         current-db-file (when old-provider (database-location old-provider))
         newdb (update-db-if-needed current-db-file config)]
     (when newdb
       (println "A new ip-geoloc db has been found:" newdb)
       (let [new-provider (init (MaxMind2. newdb nil))]
         (if (compare-and-set! provider old-provider new-provider)
           (do
             (when old-provider (close old-provider))
             (println "new db successfully installed."))
           (do
             (when new-provider (close new-provider))
             (println "WARN the new db couldn't be loaded."))))))

   :on-error
   :ignore true))


(defn start-update-db-background-thread!
  [{:keys [auto-update-check-time] :as config}]
  (let [thread
        (Thread.
         (fn []
           (loop []
             (println "background thread to update ip-geoloc db started!")

             (update-db! config)

             (sleep auto-update-check-time :+/- 0.20)

             ;; if the thread is interrupted then exit
             (when-not (.isInterrupted (Thread/currentThread))
               (recur))

             (println "background thread to update ip-geoloc db stopped!")))
         "ip-geoloc update thread")]
    (.start thread)
    (fn [] (.interrupt thread))))

(comment

  (def lastdb
    (update-db {:database-url *database-url*
                :database-md5-url *database-md5-url*
                :database-folder  "/tmp/dir2"}))

  (update-db-if-needed lastdb
                       {:database-url *database-url*
                        :database-md5-url *database-md5-url*
                        :database-folder  "/tmp/dir2"})


  (def cfg {:database-file nil
            :database-folder "/tmp/maxmind"
            :auto-update true
            :auto-update-check-time (* 3 1000) ;; every 3 hours
            :provider (atom nil)
            :update-thread (atom nil)
            :database-url *database-url*
            :database-md5-url *database-md5-url*
            })


  (update-db! cfg)

  (database-location @(:provider cfg))

  (def t (start-update-db-background-thread! cfg))

  (t)

  (update-db-if-needed nil
                       cfg)


  (def p (MaxMind2. "/tmp/maxmind/GeoLite2-City.mmdb.1442851935955.ok" nil))
  (def p (init p))
  (close p)
  (database-location nil)
  )
