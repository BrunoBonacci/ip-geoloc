(ns ip-geoloc.core
  (:require [com.stuartsierra.component :refer [start stop] :as component]
            [ip-geoloc.maxmind :as mmind])
  (:import [ip_geoloc.maxmind MaxMind2]))


(def ^:dynamic *provider* nil)


(defrecord Provider [database-file
                     database-folder
                     auto-update
                     auto-update-check-time]
  component/Lifecycle

  (start [{:keys [provider] :as config}]
    (if (some-> provider deref)
      config
      (map->Provider (mmind/start-maxmind config))))

  (stop [{:keys [provider] :as config}]
    (if (some-> provider deref)
      (map->Provider (mmind/stop-maxmind config))
      config)))


(defn create-provider [config]
  (map->Provider config))


(defn init-provider! [config]
  (alter-var-root #'*provider* (constantly (create-provider config))))


(defn start-provider! []
  (alter-var-root #'*provider*
                  (constantly (start *provider*))))


(defn stop-provider! []
  (alter-var-root #'*provider*
                  (constantly (stop *provider*))))

(defn full-geo-lookup
  ([ip]
   (full-geo-lookup *provider* ip))
  ([{:keys [provider]} ip]
   (mmind/full-geo-lookup @provider ip)))


(defn coordinates
  ([ip]
   (coordinates *provider* ip))
  ([{:keys [provider]} ip]
   (mmind/coordinates @provider ip)))


(defn geo-lookup
  ([ip]
   (geo-lookup *provider* ip))
  ([{:keys [provider]} ip]
   (mmind/geo-lookup @provider ip)))


(comment

  (def ip1 "220.181.108.182")
  (def ip2 "104.131.115.133")
  (def ip3 "23.232.137.112")

  (def prvd (create-provider {}))

  (def prvd (start prvd))

  (geo-lookup prvd ip1)
  (geo-lookup prvd ip2)
  (geo-lookup prvd ip3)

  (def prvd (stop prvd) )

  (mmind/stop-maxmind prvd)

  )
