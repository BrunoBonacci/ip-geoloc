(ns ip-geoloc.maxmind
  (:require [clojure.java.io :as io]
            [safely.core :refer [safely sleep]]
            [clojure.tools.logging :as log])
  (:require [pandect.algo.md5 :as hash])
  (:require [clj-http.client :as http])
  (:import com.maxmind.geoip2.DatabaseReader$Builder
           com.maxmind.geoip2.exception.AddressNotFoundException
           com.maxmind.geoip2.model.CityResponse
           [com.maxmind.geoip2.record City Continent
            Country Location Postal RepresentedCountry Subdivision Traits]))



(def ^:const DEFAULTS
  {;; location of the "GeoLite2-City.mmdb" database
   ;; if not specified the system will download the latest
   :database-file nil

   ;; when `:database-file` is not specified ip-geoloc
   ;; will attempt to download the latest database from the MaxMind website
   ;; This is the folder which must be writable where to store the
   ;; downloaded database.
   :database-folder "/tmp/maxmind"

   ;; whether or not the ip-geoloc must start a background thread
   ;; which attempt to check the presence of a new database
   ;; and download it
   :auto-update true

   ;; If `:auto-update` is true then how often the thread
   ;; must check for availability of a new database.
   ;; The time is expressed in milliseconds and it is
   ;; randomised.
   :auto-update-check-time (* 3 60 60 1000) ;; 3 hours

   ;; The MaxMind url for the database
   :database-url "http://geolite.maxmind.com/download/geoip/database/GeoLite2-City.mmdb.gz"

   ;; The url of the MD5 signature to verify the database integrity
   :database-md5-url "http://geolite.maxmind.com/download/geoip/database/GeoLite2-City.md5"})



(defprotocol ToClojure
  (->clojure [data] "convert a given object into a Clojure data structure"))



(extend-protocol ToClojure

  CityResponse
  (->clojure [data]
    {:continent (->clojure (.getContinent data))
     :country (->clojure (.getCountry data))
     :registered-country (->clojure (.getRegisteredCountry data))
     :represented-country (->clojure (.getRepresentedCountry data))
     :subdivisions (->clojure (.getSubdivisions data))
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
         :subdivisions subdivs
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
  (safely
   (io/copy
    (:body (http/get from {:as :stream}))
    (io/file to))
   :on-error
   :max-retry 5
   :retry-delay [:random-exp-backoff :base 200 :+/- 0.5]))



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
          (safely (.delete dbgz) :on-error :default nil)
          newdb))
      (safely (.delete db) :on-error :default nil))))



(defn find-last-available-db [{:keys [database-folder]}]
  (some->> (io/file database-folder)
           (.list)
           (filter #(re-matches #"GeoLite2-City\.mmdb\.\d+\.ok" %))
           (sort)
           (last)
           (io/file database-folder)))



(defn update-db-if-needed
  [current-db-file
   {:keys [database-url database-md5-url
           database-folder] :as cfg}]
  (when-not (check-db (fetch-db-md5 database-md5-url) current-db-file)
    (update-db cfg)))



(defn update-db! [{:keys [provider] :as config}]
  (safely
   (let [old-provider    @provider
         current-db-file (when old-provider (database-location old-provider))
         newdb (update-db-if-needed current-db-file config)]
     (when newdb
       (log/info "A new ip-geoloc MaxMind db has been found:" newdb)
       (let [new-provider (init (MaxMind2. newdb nil))]
         (if (compare-and-set! provider old-provider new-provider)
           (do
             (when old-provider
               (close old-provider)
               (safely
                (.delete (io/file current-db-file))
                :on-error :default nil))
             (log/info "New MaxMind db successfully installed."))
           (do
             (when new-provider (close new-provider))
             (safely
              (.delete (io/file newdb))
              :on-error :default nil)
             (log/warn "The new MaxMind db couldn't be loaded."))))))

   :on-error
   :default nil))



(defn start-update-db-background-thread!
  [{:keys [auto-update-check-time update-thread] :as config}]
  (let [stopped (atom false)
        thread
        (Thread.
         (fn []
           (log/info "Background thread to update ip-geoloc MaxMind db started!")
           (loop []

             (update-db! config)

             (sleep auto-update-check-time :+/- 0.20)

             ;; if the thread is interrupted then exit
             (when-not @stopped
               (recur))))
         "ip-geoloc update thread")]
    (.start thread)
    ;; return a function without params which
    ;; when executed stop the thread
    (let [t (fn []
              (swap! stopped (constantly true))
              (.interrupt thread)
              (log/info "stopping auto-update thread.")
              (swap! update-thread (constantly nil)))]
      (swap! update-thread (constantly t)))))



(defn- normalize-config [config]
  (as-> (merge DEFAULTS
               {:provider (atom nil)
                :update-thread (atom nil)}
               (into {} (filter second config))) $
    (if (:database-file $) (assoc $ :auto-update false) $)))



(defn start-maxmind [config]
  (let [{:keys [database-file database-folder
                auto-update provider
                update-thread] :as cfg} (normalize-config config)]
    (cond
      ;; if already started do nothing
      @provider cfg
      ;; if a specific file has been chosen
      ;; use that one with no update
      database-file
      (swap! provider (constantly (init (MaxMind2. database-file nil))))
      ;; if a folder it is used then
      ;; then we look for the last db available
      database-folder
      ;; checking if a db is already present
      (let [lastdb (find-last-available-db cfg)]
        (if lastdb
          (swap! provider (constantly (init (MaxMind2. lastdb nil))))
          ;; if not present download one
          (swap! provider (constantly (init (MaxMind2. (update-db cfg) nil)))))
        ;; if auto-update is enabled then start background thread
        (when auto-update
          (start-update-db-background-thread! cfg))))
    cfg))



(defn stop-maxmind [{:keys [provider update-thread] :as cfg}]
  ;; stopping update thread
  (when-let [ut (some-> update-thread deref)]
    (ut))
  ;; stopping db
  (some-> provider deref close)
  ;; resetting reference
  (swap! provider (constantly nil))
  ;; updated state
  cfg
  )
