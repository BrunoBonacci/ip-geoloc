(ns ip-geoloc.core
  (:require [com.stuartsierra.component :refer [start stop] :as component]
            [ip-geoloc.maxmind :as mmind])
  (:import [ip_geoloc.maxmind MaxMind2]))


(def ^:dynamic *provider* nil)


(defrecord Provider [db-file]
  component/Lifecycle

  (start [{:keys [provider] :as this}]
    (if provider
      this
      (assoc this :provider (mmind/init (MaxMind2. db-file nil?)))))

  (stop [{:keys [provider] :as this}]
    (if provider
      (do
        (mmind/close provider)
        (dissoc this :provider))
      this)))


(defn full-geo-lookup [{:keys [provider]} ip]
  (mmind/full-geo-lookup provider ip))


(defn coordinates [{:keys [provider]} ip]
  (mmind/coordinates provider ip))


(defn geo-lookup [{:keys [provider]} ip]
  (mmind/geo-lookup provider ip))


(defn create-provider [config]
  (map->Provider config))


(defn init-provider! [config]
  (alter-var-root #'*provider* (constantly (create-provider config))))


(defn start-provider! [config]
  (alter-var-root #'*provider*
                  (constantly (start *provider*))))


(defn stop-provider! [config]
  (alter-var-root #'*provider*
                  (constantly (stop *provider*))))



(comment

  (def ip1 "220.181.108.182")
  (def ip2 "104.131.115.133")
  (def ip3 "23.232.137.112")

  (def prvd (create-provider {:db-file "/tmp/dir2/GeoLite2-City.mmdb"}))

  (def prvd (start prvd))

  (geo-lookup prvd ip1)
  (geo-lookup prvd ip2)
  (geo-lookup prvd ip3)

  (def prvd (stop prvd))

  )
